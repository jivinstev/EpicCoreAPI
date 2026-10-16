package net.eca.util.entity_extension;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlobalEffectRegistry {

    private static final Map<Identifier, RenderType> SKYBOX_PRESETS = new ConcurrentHashMap<>();
    private static final Map<RenderType, Identifier> SKYBOX_REVERSE = new ConcurrentHashMap<>();

    public static void registerSkyboxPreset(Identifier id, RenderType renderType) {
        if (id == null || renderType == null) {
            return;
        }
        SKYBOX_PRESETS.put(id, renderType);
        SKYBOX_REVERSE.put(renderType, id);
    }

    public static RenderType getSkyboxPreset(Identifier id) {
        if (id == null) {
            return null;
        }
        return SKYBOX_PRESETS.get(id);
    }

    public static Identifier getSkyboxPresetId(RenderType renderType) {
        if (renderType == null) {
            return null;
        }
        return SKYBOX_REVERSE.get(renderType);
    }

    public static Set<Identifier> getAllSkyboxPresetIds() {
        return Collections.unmodifiableSet(SKYBOX_PRESETS.keySet());
    }

    private GlobalEffectRegistry() {}
}
