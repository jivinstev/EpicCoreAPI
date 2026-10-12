package net.eca.network;

import net.eca.client.ClientEntityUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record EntityTeleportSyncPacket(int entityId, double x, double y, double z,
                                       float yRot, float xRot, boolean onGround, int teleportId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityTeleportSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "entity_teleport_sync_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityTeleportSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityTeleportSyncPacket.encode(msg, buf), EntityTeleportSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityTeleportSyncPacket> type() {
        return TYPE;
    }


    public static void encode(EntityTeleportSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId);
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
        buffer.writeFloat(message.yRot);
        buffer.writeFloat(message.xRot);
        buffer.writeBoolean(message.onGround);
        buffer.writeVarInt(message.teleportId + 1);
    }

    public static EntityTeleportSyncPacket decode(FriendlyByteBuf buffer) {
        return new EntityTeleportSyncPacket(
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readVarInt() - 1
        );
    }

    public static void handle(EntityTeleportSyncPacket message, IPayloadContext context) {
        context.enqueueWork(() -> { if (FMLEnvironment.getDist() == Dist.CLIENT) ClientHandler.apply(message); });
    }

    private static final class ClientHandler {
        private static void apply(EntityTeleportSyncPacket message) {
            ClientEntityUtil.syncTeleportFromServer(
                    message.entityId,
                    message.x,
                    message.y,
                    message.z,
                    message.yRot,
                    message.xRot,
                    message.onGround,
                    message.teleportId
            );
        }
    }
}
