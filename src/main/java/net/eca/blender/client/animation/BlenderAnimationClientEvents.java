package net.eca.blender.client.animation;

import net.eca.EcaMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public final class BlenderAnimationClientEvents {
    private BlenderAnimationClientEvents() {
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            BlenderAnimationClientState.clear();
        }
    }
}
