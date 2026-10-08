package net.eca.network;

import net.eca.util.bossshow.BossShowDefinition;
import net.eca.util.bossshow.BossShowManager;
import net.eca.util.bossshow.BossShowPlaybackTracker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

//C→S：玩家在编辑器 Home 点击 Play 后，对选中的实体请求播放某个 cutscene
public class BossShowPlaySelectionPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowPlaySelectionPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_play_selection_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowPlaySelectionPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowPlaySelectionPacket.encode(msg, buf), BossShowPlaySelectionPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowPlaySelectionPacket> type() {
        return TYPE;
    }


    //和 /eca bossShow edit 保持一致的 64 格扫描半径
    private static final double SCAN_RADIUS = 64.0;

    private final ResourceLocation defId;
    private final UUID targetUuid;

    public BossShowPlaySelectionPacket(ResourceLocation defId, UUID targetUuid) {
        this.defId = defId;
        this.targetUuid = targetUuid;
    }

    public static void encode(BossShowPlaySelectionPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.defId);
        buf.writeUUID(msg.targetUuid);
    }

    public static BossShowPlaySelectionPacket decode(FriendlyByteBuf buf) {
        return new BossShowPlaySelectionPacket(buf.readResourceLocation(), buf.readUUID());
    }

    public static void handle(BossShowPlaySelectionPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player == null) return;

            BossShowDefinition def = BossShowManager.get(msg.defId);
            if (def == null) {
                player.sendSystemMessage(Component.literal("§cNo BossShow definition for id: " + msg.defId));
                return;
            }

            //在玩家所在维度按 64 格半径查找目标实体
            ServerLevel level = player.serverLevel();
            AABB box = AABB.ofSize(player.position(), SCAN_RADIUS * 2, SCAN_RADIUS * 2, SCAN_RADIUS * 2);
            List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e != null && e.isAlive() && e.getUUID().equals(msg.targetUuid));

            if (nearby.isEmpty()) {
                player.sendSystemMessage(Component.literal("§cTarget entity not found within " + (int) SCAN_RADIUS + " blocks"));
                return;
            }

            LivingEntity target = nearby.get(0);
            boolean ok = BossShowPlaybackTracker.start(player, target, def, true);
            if (!ok) {
                player.sendSystemMessage(Component.literal("§cFailed to start BossShow (already playing or empty definition)"));
            }
        });
    }
}
