package net.eca.network;

import net.eca.util.bossshow.BossShowPlaybackTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


//C→S：玩家按 ESC 请求跳过当前演出
public class BossShowSkipPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowSkipPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_skip_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowSkipPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowSkipPacket.encode(msg, buf), BossShowSkipPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowSkipPacket> type() {
        return TYPE;
    }


    public BossShowSkipPacket() {}

    public static void encode(BossShowSkipPacket msg, FriendlyByteBuf buf) {}

    public static BossShowSkipPacket decode(FriendlyByteBuf buf) {
        return new BossShowSkipPacket();
    }

    public static void handle(BossShowSkipPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player != null) {
                BossShowPlaybackTracker.onClientSkip(player);
            }
        });
    }
}
