// OVERLAY-ONLY: community fork multi-version layer (not the authors' code).
package net.eca.mv;

import net.eca.EcaMod;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * 1.21.2 renders entities from a render state, and ECA's layers and mixins decide from the ENTITY (its type's
 * extension, its Blender binding). This parks the entity on every render state. It must be parked from a
 * render-state modifier: NeoForge clears render data after extractRenderState and before the modifiers run.
 */
@EventBusSubscriber(modid = EcaMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EntityOf {
    public static final ContextKey<Entity> KEY = new ContextKey<>(ResourceLocation.fromNamespaceAndPath(EcaMod.MOD_ID, "entity"));

    private EntityOf() {
    }

    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onRegister(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) EntityRenderer.class, (entity, state) ->
                ((EntityRenderState) state).setRenderData(KEY, (Entity) entity));
    }

    public static Entity get(EntityRenderState state) {
        return state.getRenderData(KEY);
    }
}
