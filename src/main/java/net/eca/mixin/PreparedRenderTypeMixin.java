package net.eca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/* 26.x 的 RenderType 绘制只绑定原版 uniform 块；ECA 管线还需要绑定 EcaUniforms 块与额外采样器。 */
@Mixin(PreparedRenderType.class)
public abstract class PreparedRenderTypeMixin {

    @WrapOperation(
        method = "drawFromBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;bindDefaultUniforms(Lcom/mojang/blaze3d/systems/RenderPass;)V")
    )
    private void eca$bindEcaUniforms(RenderPass renderPass, Operation<Void> original) {
        original.call(renderPass);
        EcaShaderInstance.bindPrepared((PreparedRenderType) (Object) this, renderPass);
    }
}
