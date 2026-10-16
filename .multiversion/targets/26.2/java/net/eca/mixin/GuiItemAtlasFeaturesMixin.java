// OVERLAY-ONLY: community fork multi-version layer, 26.2 only (#37; mixin_add_only, so it never reaches 26.3, whose
// drawToSlot hands renderAllFeatures a render pass). GuiItemAtlas.drawToSlot submits the item, then draws its features
// into the atlas slot with renderAllFeatures(storage); an item extension's shader passes collected at submit are drawn
// right after that returns, over the item (net.eca.client.render.GuiTexturePasses).
package net.eca.mixin;

import net.eca.client.render.GuiTexturePasses;
import net.minecraft.client.gui.render.GuiItemAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasFeaturesMixin {
    @Inject(method = "drawToSlot", at = @At("HEAD"))
    private void eca$beginSlot(CallbackInfo ci) {
        GuiTexturePasses.begin();
    }

    @Inject(method = "drawToSlot", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
    private void eca$drawPassesOverItem(CallbackInfo ci) {
        GuiTexturePasses.drawAfterFeatures();
    }

    @Inject(method = "drawToSlot", at = @At("RETURN"))
    private void eca$endSlot(CallbackInfo ci) {
        GuiTexturePasses.end();
    }
}
