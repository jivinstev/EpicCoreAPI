package net.eca.client;

import net.eca.EcaMod;
import net.eca.client.render.BlockExtensionRenderer;
import net.eca.blender.client.resource.BlenderModelManager;
import net.eca.client.render.preset.ShaderPresetRegistry;
import net.eca.compat.GeckoLibCompat;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.entity_extension.BlenderExtensionAdapter;
import net.eca.util.item_extension.ItemExtensionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = EcaMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EcaClientLifecycle {

    private EcaClientLifecycle() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BlenderExtensionAdapter.register();
            BlockExtensionRenderer.register();
            if (ModList.get().isLoaded("geckolib")) {
                GeckoLibCompat.register();
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BlenderModelManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(ItemExtensionManager::scanAndRegisterAll);
        event.enqueueWork(BlockExtensionManager::scanAndRegisterAll);
        event.enqueueWork(ShaderPresetRegistry::scanAndRegisterAll);
    }
}
