// OVERLAY-ONLY: fork layer (26.2), not the authors' code.
package net.eca.mv;

import net.minecraft.client.renderer.culling.Frustum;

/**
 * This frame's entity-culling frustum. Through 26.1.2 LevelRendererMixin took it from
 * {@code LevelRenderer.extractVisibleEntities}'s argument; 26.2 moved that method to {@code LevelExtractor}, so
 * {@code LevelExtractorMixin} stores it here and LevelRendererMixin reads it (mixin classes cannot share a field).
 */
public final class CullingFrustum {
    public static Frustum current;

    private CullingFrustum() {
    }
}
