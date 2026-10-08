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


//S→C：停止当前 BossShow 演出
public class BossShowStopPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowStopPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_stop_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowStopPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowStopPacket.encode(msg, buf), BossShowStopPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowStopPacket> type() {
        return TYPE;
    }


    private final ResourceLocation cutsceneId;
    private final boolean skipped;

    public BossShowStopPacket(ResourceLocation cutsceneId, boolean skipped) {
        this.cutsceneId = cutsceneId;
        this.skipped = skipped;
    }

    public static void encode(BossShowStopPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.cutsceneId);
        buf.writeBoolean(msg.skipped);
    }

    public static BossShowStopPacket decode(FriendlyByteBuf buf) {
        return new BossShowStopPacket(buf.readResourceLocation(), buf.readBoolean());
    }

    public static void handle(BossShowStopPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.onStop(msg); });
    }

    public ResourceLocation cutsceneId() { return cutsceneId; }
    public boolean skipped() { return skipped; }

    private static final class ClientHandlerRef {
        static void onStop(BossShowStopPacket msg) {
            BossShowClientState.onServerStop(msg);
        }
    }
}
