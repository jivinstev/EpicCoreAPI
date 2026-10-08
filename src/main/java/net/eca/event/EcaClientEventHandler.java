package net.eca.event;

import net.eca.client.FactionGlowData;
import net.eca.client.HealthClientSync;
import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.raid.RaidClientState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

//客户端事件处理：断开连接时清空实体扩展客户端状态，防止单人模式下静态状态跨存档残留
@EventBusSubscriber(modid = "eca", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class EcaClientEventHandler {

    private EcaClientEventHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        HealthClientSync.tick();
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HealthClientSync.clear();
        EntityExtensionClientState.clearAll();
        FactionGlowData.clear();
        RaidClientState.clearAll();
    }
}
