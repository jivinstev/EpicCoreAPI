package net.eca.network;

import net.eca.util.EntityUtil.ServerTeleportConnectionBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public record EntityTeleportAckPacket(int teleportId, double x, double y, double z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityTeleportAckPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "entity_teleport_ack_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityTeleportAckPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityTeleportAckPacket.encode(msg, buf), EntityTeleportAckPacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityTeleportAckPacket> type() {
        return TYPE;
    }


    public static void encode(EntityTeleportAckPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.teleportId);
        buffer.writeDouble(message.x);
        buffer.writeDouble(message.y);
        buffer.writeDouble(message.z);
    }

    public static EntityTeleportAckPacket decode(FriendlyByteBuf buffer) {
        return new EntityTeleportAckPacket(
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
        );
    }

    public static void handle(EntityTeleportAckPacket message, IPayloadContext context) {
        ServerPlayer sender = ((ServerPlayer) context.player());
        if (sender != null && sender.connection instanceof ServerTeleportConnectionBridge bridge) {
            bridge.eca$completeTeleport(message.teleportId, message.x, message.y, message.z);
        }
    }
}
