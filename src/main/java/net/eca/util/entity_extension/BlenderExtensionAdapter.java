package net.eca.util.entity_extension;

import net.eca.blender.client.entity.BlenderEntityBindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Installs the extension bridge without introducing a reverse dependency in the core. */
public final class BlenderExtensionAdapter {
    private BlenderExtensionAdapter() { }

    public static void register() {
        BlenderEntityBindings.setFallbackResolver(entity -> EntityExtensionSafeAccess.blenderModelExtension(
            EntityExtensionManager.getExtension(entity.getType()), entity));
    }
}
