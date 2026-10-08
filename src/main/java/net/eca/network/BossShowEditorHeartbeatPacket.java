package net.eca.network;

import net.eca.util.bossshow.BossShowEditorSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public final class BossShowEditorHeartbeatPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowEditorHeartbeatPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_editor_heartbeat_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowEditorHeartbeatPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowEditorHeartbeatPacket.encode(msg, buf), BossShowEditorHeartbeatPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowEditorHeartbeatPacket> type() {
        return TYPE;
    }


    public static void encode(BossShowEditorHeartbeatPacket message, FriendlyByteBuf buffer) {}

    public static BossShowEditorHeartbeatPacket decode(FriendlyByteBuf buffer) {
        return new BossShowEditorHeartbeatPacket();
    }

    public static void handle(BossShowEditorHeartbeatPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) context.player());
            if (player != null) {
                BossShowEditorSessionManager.heartbeat(player);
            }
        });
    }
}
