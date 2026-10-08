package net.eca.blender.animation;

import net.eca.EcaMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = EcaMod.MOD_ID)
public final class BlenderControllerEvents {
    private BlenderControllerEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.level() instanceof ServerLevel) BlenderControllers.discover(living);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        // Read cancellation after every subscriber has had a chance to handle the event.
        BlenderControllers.queueHurt(event.getEntity(), () -> !event.isCanceled() && event.getAmount() > 0);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) BlenderControllers.tick(level);
    }
}
