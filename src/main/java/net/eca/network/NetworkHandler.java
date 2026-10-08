package net.eca.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.HandlerThread;

/**
 * Network handler for ECA mod.
 * Manages network communication between server and client.
 */
@SuppressWarnings("removal")
public class NetworkHandler {

    private static final String PROTOCOL_VERSION = "3";

    /**
     * Register all network packets.
     * Called during mod initialization.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(ClientRemovePacket.TYPE, ClientRemovePacket.STREAM_CODEC, ClientRemovePacket::handle);

        registrar.playToClient(EntityExtensionActiveTypePacket.TYPE, EntityExtensionActiveTypePacket.STREAM_CODEC, EntityExtensionActiveTypePacket::handle);

        registrar.playToClient(EntityExtensionBossEventTypePacket.TYPE, EntityExtensionBossEventTypePacket.STREAM_CODEC, EntityExtensionBossEventTypePacket::handle);

        registrar.playBidirectional(EntityExtensionOverridePacket.TYPE, EntityExtensionOverridePacket.STREAM_CODEC, EntityExtensionOverridePacket::handle);

        registrar.playToClient(EntityContainerCheckRequestPacket.TYPE, EntityContainerCheckRequestPacket.STREAM_CODEC, EntityContainerCheckRequestPacket::handle);

        registrar.executesOn(HandlerThread.NETWORK).playToServer(EntityContainerCheckResponsePacket.TYPE, EntityContainerCheckResponsePacket.STREAM_CODEC, EntityContainerCheckResponsePacket::handle);

        registrar.playToClient(BossShowStartPacket.TYPE, BossShowStartPacket.STREAM_CODEC, BossShowStartPacket::handle);

        registrar.playToClient(BossShowStopPacket.TYPE, BossShowStopPacket.STREAM_CODEC, BossShowStopPacket::handle);

        registrar.playToServer(BossShowSkipPacket.TYPE, BossShowSkipPacket.STREAM_CODEC, BossShowSkipPacket::handle);

        registrar.playToClient(BossShowOpenEditorHomePacket.TYPE, BossShowOpenEditorHomePacket.STREAM_CODEC, BossShowOpenEditorHomePacket::handle);

        registrar.playToServer(BossShowExitEditorPacket.TYPE, BossShowExitEditorPacket.STREAM_CODEC, BossShowExitEditorPacket::handle);

        registrar.playToServer(BossShowSaveEditorPacket.TYPE, BossShowSaveEditorPacket.STREAM_CODEC, BossShowSaveEditorPacket::handle);

        registrar.playToServer(BossShowDeleteEditorPacket.TYPE, BossShowDeleteEditorPacket.STREAM_CODEC, BossShowDeleteEditorPacket::handle);

        registrar.playToServer(BossShowPlaySelectionPacket.TYPE, BossShowPlaySelectionPacket.STREAM_CODEC, BossShowPlaySelectionPacket::handle);

        registrar.playToClient(BossShowSubtitlePacket.TYPE, BossShowSubtitlePacket.STREAM_CODEC, BossShowSubtitlePacket::handle);

        registrar.playToClient(ShaderGeneratorOpenPacket.TYPE, ShaderGeneratorOpenPacket.STREAM_CODEC, ShaderGeneratorOpenPacket::handle);

        registrar.playToClient(FilterSyncPacket.TYPE, FilterSyncPacket.STREAM_CODEC, FilterSyncPacket::handle);

        registrar.playToClient(SetHealthClientSyncPacket.TYPE, SetHealthClientSyncPacket.STREAM_CODEC, SetHealthClientSyncPacket::handle);

        registrar.playToClient(FactionGlowSyncPacket.TYPE, FactionGlowSyncPacket.STREAM_CODEC, FactionGlowSyncPacket::handle);

        registrar.playToClient(RaidBossBarSyncPacket.TYPE, RaidBossBarSyncPacket.STREAM_CODEC, RaidBossBarSyncPacket::handle);

        // 新包一律追加在末尾，插在中间会让其后所有包的 ID 顺移
        registrar.playToClient(ClientReviveContainersPacket.TYPE, ClientReviveContainersPacket.STREAM_CODEC, ClientReviveContainersPacket::handle);

        registrar.playToServer(BossShowEditorHeartbeatPacket.TYPE, BossShowEditorHeartbeatPacket.STREAM_CODEC, BossShowEditorHeartbeatPacket::handle);

        registrar.playToServer(HealthSyncResponsePacket.TYPE, HealthSyncResponsePacket.STREAM_CODEC, HealthSyncResponsePacket::handle);

        registrar.playToClient(BlenderAnimationSyncPacket.TYPE, BlenderAnimationSyncPacket.STREAM_CODEC, BlenderAnimationSyncPacket::handle);

        registrar.playToClient(EntityTeleportSyncPacket.TYPE, EntityTeleportSyncPacket.STREAM_CODEC, EntityTeleportSyncPacket::handle);

        registrar.playToServer(EntityTeleportAckPacket.TYPE, EntityTeleportAckPacket.STREAM_CODEC, EntityTeleportAckPacket::handle);
    }

    /**
     * Send a message to the server.
     * @param message the message to send
     */
    public static <MSG extends CustomPacketPayload> void sendToServer(MSG message) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(message);
    }

    /**
     * Send a message to a specific player.
     * @param message the message to send
     * @param player the target player
     */
    public static <MSG extends CustomPacketPayload> void sendToPlayer(MSG message, ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, message);
    }

    /**
     * Send a message to all clients tracking the given entity.
     * @param message the message to send
     * @param entity the entity being tracked
     */
    public static <MSG extends CustomPacketPayload> void sendToTrackingClients(MSG message, Entity entity) {
        if (entity.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntity(entity, message);
        }
    }

    /**
     * Send a message to every client tracking an entity, including the entity when it is a player.
     * @param message the message to send
     * @param entity the tracked entity
     */
    public static <MSG extends CustomPacketPayload> void sendToTrackingClientsAndSelf(MSG message, Entity entity) {
        if (entity.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, message);
        }
    }

    /**
     * Send a message to all players in a specific dimension.
     * @param message the message to send
     * @param level the server level (dimension)
     */
    public static <MSG extends CustomPacketPayload> void sendToDimension(MSG message, ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, message);
    }
}
