package net.eca.network;

import net.eca.blender.client.animation.BlenderAnimationClientState;
import net.eca.blender.animation.BlenderPlaybackState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record BlenderAnimationSyncPacket(UUID entityId, long revision, BlenderPlaybackState state) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BlenderAnimationSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "blender_animation_sync_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlenderAnimationSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BlenderAnimationSyncPacket.encode(msg, buf), BlenderAnimationSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<BlenderAnimationSyncPacket> type() {
        return TYPE;
    }

    public static void encode(BlenderAnimationSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.entityId);
        buffer.writeLong(message.revision);
        buffer.writeBoolean(message.state != null);
        if (message.state == null) {
            return;
        }
        buffer.writeUtf(message.state.animation(), 256);
        buffer.writeLong(message.state.referenceGameTime());
        buffer.writeFloat(message.state.elapsedSeconds());
        buffer.writeFloat(message.state.speed());
        buffer.writeBoolean(message.state.loop());
        buffer.writeBoolean(message.state.paused());
    }

    public static BlenderAnimationSyncPacket decode(FriendlyByteBuf buffer) {
        UUID entityId = buffer.readUUID();
        long revision = buffer.readLong();
        if (!buffer.readBoolean()) {
            return new BlenderAnimationSyncPacket(entityId, revision, null);
        }
        String animation = buffer.readUtf(256);
        long referenceGameTime = buffer.readLong();
        float elapsedSeconds = buffer.readFloat();
        float speed = buffer.readFloat();
        boolean loop = buffer.readBoolean();
        boolean paused = buffer.readBoolean();
        BlenderPlaybackState state = new BlenderPlaybackState(animation, revision, referenceGameTime,
            elapsedSeconds, speed, loop, paused);
        return new BlenderAnimationSyncPacket(entityId, revision, state);
    }

    public static void handle(BlenderAnimationSyncPacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandler.apply(message); });
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientHandler {
        private static void apply(BlenderAnimationSyncPacket message) {
            BlenderAnimationClientState.apply(message.entityId, message.revision, message.state);
        }
    }
}
