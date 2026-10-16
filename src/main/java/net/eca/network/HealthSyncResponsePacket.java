package net.eca.network;

import net.eca.util.health.report.HealthReportManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client observations affect diagnostics only, never server health or mutation success. */
public record HealthSyncResponsePacket(UUID request, UUID entityUuid, boolean verified, float actual) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HealthSyncResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "health_sync_response_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HealthSyncResponsePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> HealthSyncResponsePacket.encode(msg, buf), HealthSyncResponsePacket::decode);

    @Override
    public CustomPacketPayload.Type<HealthSyncResponsePacket> type() {
        return TYPE;
    }

    public static void encode(HealthSyncResponsePacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.request());
        buffer.writeUUID(message.entityUuid());
        buffer.writeBoolean(message.verified());
        buffer.writeFloat(message.actual());
    }

    public static HealthSyncResponsePacket decode(FriendlyByteBuf buffer) {
        return new HealthSyncResponsePacket(buffer.readUUID(), buffer.readUUID(), buffer.readBoolean(), buffer.readFloat());
    }

    public static void handle(HealthSyncResponsePacket message, IPayloadContext context) {
        ServerPlayer sender = ((ServerPlayer) context.player());
        context.enqueueWork(() -> {
            if (sender != null) HealthReportManager.completeClientSync(
                    message.request(), message.entityUuid(), sender, message.verified(), message.actual());
        });
    }
}
