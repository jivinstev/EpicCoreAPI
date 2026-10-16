// OVERLAY-ONLY: community fork multi-version layer, 26.3 only (#33). PictureInPictureRenderer.prepare (an oversized item's
// renderer among them) submits into its own texture and draws the features in a pass of its own; an item extension's
// shader passes submitted there are drawn into that pass right after (net.eca.client.render.GuiTexturePasses).
package net.eca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.eca.client.render.GuiTexturePasses;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureRendererMixin {
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
