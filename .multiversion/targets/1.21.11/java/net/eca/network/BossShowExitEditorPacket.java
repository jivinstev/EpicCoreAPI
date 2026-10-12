package net.eca.network;

import net.eca.command.BossShowCommand;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


//C→S：客户端关闭编辑器，请求服务端还原 gamemode 并清理标记
public class BossShowExitEditorPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowExitEditorPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "boss_show_exit_editor_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowExitEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowExitEditorPacket.encode(msg, buf), BossShowExitEditorPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowExitEditorPacket> type() {
        return TYPE;
    }


    public BossShowExitEditorPacket() {}

    public static void encode(BossShowExitEditorPacket msg, FriendlyByteBuf buf) {}

    public static BossShowExitEditorPacket decode(FriendlyByteBuf buf) {
        return new BossShowExitEditorPacket();
    }

    public static void handle(BossShowExitEditorPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player != null) {
                BossShowCommand.restorePreviousGameMode(player);
            }
        });
    }
}
