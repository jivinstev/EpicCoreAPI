package net.eca;

import net.eca.event.EcaEventHandler;
import net.eca.event.LoadCompleteHandler;
import net.eca.init.ModConfigs;
import net.eca.network.NetworkHandler;
import net.eca.util.selector.EcaSelectorRegistry;
import net.eca.util.entity_extension.ForceLoadingManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

@SuppressWarnings("removal")
@Mod(EcaMod.MOD_ID)
public final class EcaMod {
    public static final String MOD_ID = "eca";
    private static volatile boolean loadComplete;

    public static boolean isLoadComplete() { return loadComplete; }

    public static void setLoadComplete(boolean value) { loadComplete = value; }

    public EcaMod(IEventBus modEventBus, ModContainer modContainer) {
        ModConfigs.register();
        modEventBus.addListener(NetworkHandler::register);
        NeoForge.EVENT_BUS.register(new EcaEventHandler());
        EcaSelectorRegistry.register();
        modEventBus.addListener(ForceLoadingManager::registerValidationCallback);
        LoadCompleteHandler loadCompleteHandler = new LoadCompleteHandler();
        modEventBus.addListener(loadCompleteHandler::onLoadComplete);
    }
}
