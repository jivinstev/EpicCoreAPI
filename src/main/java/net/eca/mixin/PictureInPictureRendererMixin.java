// OVERLAY-ONLY: community fork multi-version layer, 26.3 only (#33). PictureInPictureRenderer.prepare (an oversized item's
// renderer among them) submits into its own texture and draws the features in a pass of its own; an item extension's
// shader passes submitted there are drawn into that pass right after (net.eca.client.render.GuiTexturePasses).
// 26.3 (carried 26.2 picture-in-picture renderers): 26.3 dropped RenderSystem's output override, which 26.2's prepare set
// around renderToTexture; a renderer that draws at once there (ShaderPreviewRenderer.GuiPipRenderer) gets the fork layer's
// override instead (net.eca.mv.LegacyGl.setOutput), so its draws land in this renderer's texture, not the main target.
package net.eca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Shadow;
import net.eca.client.render.GuiTexturePasses;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
    @Shadow private GpuTextureView textureView;
    @Shadow private GpuTextureView depthTextureView;

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/pip/PictureInPictureRenderer;renderToTexture(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void eca$renderIntoTexture(PictureInPictureRenderer<?> self, PictureInPictureRenderState state, PoseStack pose,
                                       SubmitNodeCollector collector, Operation<Void> original) {
        GpuTextureView[] prior = net.eca.mv.LegacyGl.setOutput(textureView, depthTextureView);
        try {
            original.call(self, state, pose, collector);
        } finally {
            net.eca.mv.LegacyGl.setOutput(prior[0], prior[1]);
        }
    }

    @Inject(method = "prepare", at = @At("HEAD"))
    private void eca$beginTexture(CallbackInfo ci) {
        GuiTexturePasses.begin();
    }

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
    private void eca$drawPassesIntoTexture(RenderPass pass, FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original) {
        original.call(pass, frame);
        GuiTexturePasses.drawInto(pass);
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void eca$endTexture(CallbackInfo ci) {
        GuiTexturePasses.end();
    }
}
