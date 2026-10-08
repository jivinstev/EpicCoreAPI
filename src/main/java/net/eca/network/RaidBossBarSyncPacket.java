package net.eca.network;

import net.eca.util.raid.RaidBarState;
import net.eca.util.raid.RaidClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/*
 * 服务端 → 客户端：同步一条 Boss 血条所属的袭击状态。
 *
 * 客户端不持有袭击实例，此包是渲染层识别"这条血条属于哪场袭击"并驱动
 * RaidBossBarExtension 条件方法的唯一途径。state 为 null 表示解除映射
 * （袭击结束或玩家离开参与范围）。
 */
public final class RaidBossBarSyncPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RaidBossBarSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "raid_boss_bar_sync_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RaidBossBarSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> RaidBossBarSyncPacket.encode(msg, buf), RaidBossBarSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<RaidBossBarSyncPacket> type() {
        return TYPE;
    }


    private final UUID bossEventId;
    private final RaidBarState state;

    public RaidBossBarSyncPacket(UUID bossEventId, RaidBarState state) {
        this.bossEventId = bossEventId;
        this.state = state;
    }

    public static void encode(RaidBossBarSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.bossEventId);
        buf.writeBoolean(msg.state != null);
        if (msg.state != null) {
            RaidBarState.encode(msg.state, buf);
        }
    }

    public static RaidBossBarSyncPacket decode(FriendlyByteBuf buf) {
        UUID bossEventId = buf.readUUID();
        RaidBarState state = buf.readBoolean() ? RaidBarState.decode(buf) : null;
        return new RaidBossBarSyncPacket(bossEventId, state);
    }

    public static void handle(RaidBossBarSyncPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> { if (FMLEnvironment.getDist() == Dist.CLIENT) RaidClientState.setBarState(msg.bossEventId, msg.state); });
    }
}
