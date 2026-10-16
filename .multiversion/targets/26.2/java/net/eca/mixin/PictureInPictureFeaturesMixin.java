// OVERLAY-ONLY: community fork multi-version layer, 26.2 only (#37; mixin_add_only, so it never reaches 26.3, whose
// prepare hands renderAllFeatures a render pass). PictureInPictureRenderer.prepare (an oversized item's renderer among
// them) submits into its own texture, then draws the features with renderAllFeatures(storage); an item extension's
// shader passes collected at submit are drawn right after that returns, over the item (net.eca.client.render.GuiTexturePasses).
package net.eca.mixin;

import net.eca.client.render.GuiTexturePasses;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureFeaturesMixin {
    @Inject(method = "prepare", at = @At("HEAD"))
    private void eca$beginTexture(CallbackInfo ci) {
        GuiTexturePasses.begin();
    }

    @Inject(method = "prepare", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void eca$drawPassesOverItem(CallbackInfo ci) {
        GuiTexturePasses.drawAfterFeatures();
    }

    @Inject(method = "prepare", at = @At("RETURN"))
    private void eca$endTexture(CallbackInfo ci) {
        GuiTexturePasses.end();
    }
}
