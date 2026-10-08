package net.eca.network;

import net.eca.util.bossshow.BossShowClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;


//S→C：更新当前 BossShow 演出的字幕文本
public class BossShowSubtitlePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowSubtitlePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_subtitle_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowSubtitlePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowSubtitlePacket.encode(msg, buf), BossShowSubtitlePacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowSubtitlePacket> type() {
        return TYPE;
    }


    private final String text;

    public BossShowSubtitlePacket(String text) {
        this.text = text != null ? text : "";
    }

    public static void encode(BossShowSubtitlePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.text, 512);
    }

    public static BossShowSubtitlePacket decode(FriendlyByteBuf buf) {
        return new BossShowSubtitlePacket(buf.readUtf(512));
    }

    public static void handle(BossShowSubtitlePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.onSubtitle(msg); });
    }

    public String text() { return text; }

    private static final class ClientHandlerRef {
        static void onSubtitle(BossShowSubtitlePacket msg) {
            BossShowClientState.onSubtitle(msg);
        }
    }
}
