// OVERLAY-ONLY: community fork multi-version layer. 1.21.1's GameRendererPostLevelMixin raised GameRenderer.getDepthFar()
// to the force-loading render distance; 26.1 removed getDepthFar and keeps the far plane in Camera.depthFar, which
// Camera.update sets and then uses for the culling projection and the perspective. Raising it right after that store
// gives both the extended far plane, as 1.21.1's getDepthFar override did. Added to eca.mixins.json for this target by
// .multiversion (target.properties mixin_add).
package net.eca.mixin;

import net.eca.config.EcaConfiguration;
import net.minecraft.client.Camera;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraDepthFarMixin {
    @Shadow
    private float depthFar;

    @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void eca$extendForceLoadedDepthFar(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        float forceLoadedFar = EcaConfiguration.getForceLoadingMaxRenderDistanceSafely() + 32.0f;
        if (this.depthFar < forceLoadedFar) {
            this.depthFar = forceLoadedFar;
        }
    }
}
