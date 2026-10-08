package net.eca.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import java.util.Map;
import java.util.WeakHashMap;

/** Side table linking a living-entity render state to the id of the entity it was extracted from. */
public final class EcaRenderStateEntities {

    private static final Map<LivingEntityRenderState, Integer> IDS = new WeakHashMap<>();

    private EcaRenderStateEntities() {}

    public static synchronized void put(LivingEntityRenderState state, int entityId) {
        if (state != null) {
            IDS.put(state, entityId);
        }
    }

    public static synchronized int getId(LivingEntityRenderState state) {
        Integer id = state == null ? null : IDS.get(state);
        return id == null ? -1 : id;
    }
}
