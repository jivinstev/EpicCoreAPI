package net.eca.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/* ECA 管线的 uniform 在记录几何体时取快照（见 EcaShaderInstance.onPrepare）；非 ECA 管线不受影响。 */
@Mixin(RenderType.class)
public abstract class RenderTypePrepareMixin {

    @Shadow
    public abstract RenderPipeline pipeline();

    @Inject(method = "prepare", at = @At("RETURN"))
    private void eca$snapshotUniforms(CallbackInfoReturnable<PreparedRenderType> cir) {
        EcaShaderInstance.onPrepare(this.pipeline(), cir.getReturnValue());
    }
}
