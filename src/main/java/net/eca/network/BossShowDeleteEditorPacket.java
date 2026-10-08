package net.eca.network;

import net.eca.util.bossshow.BossShowManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


//C→S：客户端请求删除一个 BossShow 定义（同时删 JSON 文件）。删完服务端重发 Home 包刷新列表
public class BossShowDeleteEditorPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowDeleteEditorPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "boss_show_delete_editor_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowDeleteEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowDeleteEditorPacket.encode(msg, buf), BossShowDeleteEditorPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowDeleteEditorPacket> type() {
        return TYPE;
    }


    private final Identifier id;

    public BossShowDeleteEditorPacket(Identifier id) {
        this.id = id;
    }

    public Identifier id() { return id; }

    public static void encode(BossShowDeleteEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeIdentifier(msg.id);
    }

    public static BossShowDeleteEditorPacket decode(FriendlyByteBuf buf) {
        return new BossShowDeleteEditorPacket(buf.readIdentifier());
    }

    public static void handle(BossShowDeleteEditorPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player == null) return;
            boolean ok = BossShowManager.delete(msg.id);
            if (ok) {
                player.sendSystemMessage(Component.translatable("msg.eca.bossshow.deleted", msg.id.toString()));
                //重发 Home 包，客户端 Home 自动刷新
                NetworkHandler.sendToPlayer(
                    new BossShowOpenEditorHomePacket(BossShowManager.getAllDefinitions().values()),
                    player
                );
            } else {
                player.sendSystemMessage(Component.translatable("msg.eca.bossshow.delete_failed", msg.id.toString()));
            }
        });
    }
}
