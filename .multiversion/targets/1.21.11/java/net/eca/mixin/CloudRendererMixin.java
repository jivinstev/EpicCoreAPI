// 1.21.11: render() gained the game time (a long, before the partial tick), so the callback does too.
// 1.21.5: render() dropped the (unused here) modelView/projection Matrix4f params.
// OVERLAY-ONLY: community fork multi-version layer. 1.21.1's LevelRendererMixin cancelled renderClouds; in 1.21.2
// clouds are drawn by CloudRenderer.render inside a frame-graph pass that runs after the entities, so the skip
// moves here. Added to eca.mixins.json for this target by .multiversion (target.properties mixin_add).
package net.eca.mixin;

import net.eca.mv.CloudGate;
import net.minecraft.client.renderer.CloudRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void eca$skipOccludingClouds(int color, net.minecraft.client.CloudStatus status, float height,
                                        net.minecraft.world.phys.Vec3 cameraPosition, long gameTime, float ticks, CallbackInfo ci) {
        if (CloudGate.occlude) {
            ci.cancel();
        }
    }
}
