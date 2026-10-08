package net.eca.network;

import net.eca.client.ClientEntityUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class EntityContainerCheckRequestPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityContainerCheckRequestPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "entity_container_check_request_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityContainerCheckRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityContainerCheckRequestPacket.encode(msg, buf), EntityContainerCheckRequestPacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityContainerCheckRequestPacket> type() {
        return TYPE;
    }


    private final UUID requestId;
    private final UUID entityUuid;

    public EntityContainerCheckRequestPacket(UUID requestId, UUID entityUuid) {
        this.requestId = requestId;
        this.entityUuid = entityUuid;
    }

    public static void encode(EntityContainerCheckRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.requestId);
        buf.writeUUID(msg.entityUuid);
    }

    public static EntityContainerCheckRequestPacket decode(FriendlyByteBuf buf) {
        return new EntityContainerCheckRequestPacket(buf.readUUID(), buf.readUUID());
    }

    public static void handle(EntityContainerCheckRequestPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.apply(msg); });
    }

    // 客户端引用隔离在独立内部类中，实际逻辑委托给 @OnlyIn(Dist.CLIENT) 的 ClientEntityUtil
    private static final class ClientHandlerRef {
        static void apply(EntityContainerCheckRequestPacket msg) {
            ClientEntityUtil.handleContainerCheckRequest(msg.requestId, msg.entityUuid);
        }
    }
}
