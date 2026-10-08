package net.eca.network;

import net.eca.client.HealthClientSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/*
 * 服务端改血成功后 → 追踪客户端对本地实体重跑 ECA 改血。
 * 自定义存储型实体客户端也有独立一份存储，服务端改动不会自动同步；
 * 客户端重跑同一条逆向链打穿本地存储，使其血条/显示随之刷新。
 */
public final class SetHealthClientSyncPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetHealthClientSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "set_health_client_sync_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetHealthClientSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> SetHealthClientSyncPacket.encode(msg, buf), SetHealthClientSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<SetHealthClientSyncPacket> type() {
        return TYPE;
    }


    private final int entityId;
    private final float health;
    private final UUID entityUuid;
    private final UUID request;

    public SetHealthClientSyncPacket(int entityId, UUID entityUuid, UUID request, float health) {
        this.entityId = entityId;
        this.entityUuid = entityUuid;
        this.request = request;
        this.health = health;
    }

    public static void encode(SetHealthClientSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeFloat(msg.health);
        buf.writeUUID(msg.entityUuid);
        buf.writeUUID(msg.request);
    }

    public static SetHealthClientSyncPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        float health = buf.readFloat();
        return new SetHealthClientSyncPacket(id, buf.readUUID(), buf.readUUID(), health);
    }

    public static void handle(SetHealthClientSyncPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.apply(msg); });
    }

    // 公共包处理器不直接加载客户端队列。
    private static final class ClientHandlerRef {
        static void apply(SetHealthClientSyncPacket msg) {
            HealthClientSync.enqueue(msg.entityId, msg.entityUuid, msg.request, msg.health);
        }
    }
}
