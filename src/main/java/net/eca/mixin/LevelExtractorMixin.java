// OVERLAY-ONLY: fork layer (26.2), not the authors' code. Added for 26.2 by target.properties mixin_add.
package net.eca.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 moved {@code extractVisibleEntities} from LevelRenderer to LevelExtractor: the authors' frustum capture, here. */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void eca$captureCullingFrustum(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                           LevelRenderState levelRenderState, CallbackInfo ci) {
        net.eca.mv.CullingFrustum.current = frustum;
    }
}
