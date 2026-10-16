// OVERLAY-ONLY: community fork multi-version layer, 26.3 only (#33). GuiItemAtlas.drawToSlot submits the item, prepares the
// frame and draws its features into the atlas texture in a pass of its own; an item extension's shader passes are drawn
// into that same pass right after (net.eca.client.render.GuiTexturePasses).
package net.eca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.eca.client.render.GuiTexturePasses;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {
    @Inject(method = "drawToSlot", at = @At("HEAD"))
    private void eca$beginSlot(CallbackInfo ci) {
        GuiTexturePasses.begin();
    }

    @WrapOperation(method = "drawToSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderAllFeatures(Lcom/mojang/renderpearl/api/commands/RenderPass;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"))
    private void eca$drawPassesIntoSlot(RenderPass pass, FeatureRenderDispatcher.PreparedFrame frame, Operation<Void> original) {
        original.call(pass, frame);
        GuiTexturePasses.drawInto(pass);
    }

    @Inject(method = "drawToSlot", at = @At("RETURN"))
    private void eca$endSlot(CallbackInfo ci) {
        GuiTexturePasses.end();
    }
}
