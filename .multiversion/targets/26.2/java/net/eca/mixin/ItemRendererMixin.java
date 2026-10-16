// Fork multi-version layer: 26.1.2 copy (tintLayers is an IntList) of the 1.21.10 copy of the 1.21.5 mixin. 1.21.9 draws items in two steps: LayerRenderState.submit hands the
// item's quads to a SubmitNodeCollector and a feature renderer draws them later. The extension pass is therefore built at
// the end of submit (the pose stack is only right now) and, when it must be drawn at once (a GUI / tooltip item, not a world
// one), the drawing is a custom-geometry task in the collector's order 1 -- after every order-0 task, the item's own among them.
// The 1.21.4 notes still hold: the extension pass runs once per item (net.eca.mv.ItemStacks, filled by ItemModelResolverMixin),
// with the layer's resolved tints.
// 26.2 (#33): the at-once drawing is no longer a custom-geometry task (26.2 runs those while PREPARING, before any draw, and the
// carrier put a LegacyRenderType on vanilla's feature path); it is drawn at submit, or queued after the level when submitted
// with it (net.eca.mv.SubmitPhase).
// 26.2 (#37): a GUI item's pass is drawn after the item, once renderAllFeatures has drawn it into the atlas or
// picture-in-picture texture (net.eca.client.render.GuiTexturePasses).
package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.item_extension.ItemExtension;
import net.eca.util.item_extension.ItemExtensionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.eca.mv.ItemStacks;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemRendererMixin {

    @Shadow
    @Final
    private List<BakedQuad> quads;

    // 26.1: the layer's tints are a nullable IntList (int[] up to 1.21.11)
    @Shadow
    private it.unimi.dsi.fastutil.ints.IntList tintLayers;

    @Shadow
    @Final
    ItemStackRenderState this$0;

    @Inject(method = "submit",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                     shift = At.Shift.BEFORE))
    private void eca$renderItemExtension(PoseStack poseStack, SubmitNodeCollector collector,
                                         int combinedLight, int combinedOverlay, int outlineColor, CallbackInfo ci) {
        ItemStack stack = ItemStacks.claim(this$0);
        if (stack == null || quads.isEmpty()) return;
        ItemDisplayContext displayContext = ((ItemStackRenderStateAccessor) this$0).eca$displayContext();
        if (stack.isEmpty()) return;
        ItemExtension extension = ItemExtensionManager.getExtension(stack.getItem());
        if (extension == null || !extension.enabled() || !extension.shouldRender(stack)) return;

        List<ShaderMaskPass> passes = extension.getShaderPasses();
        if (passes == null || passes.isEmpty()) return;

        boolean queued = eca$isWorldContext(displayContext);
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            SpriteBatchingVertexConsumer consumer = new SpriteBatchingVertexConsumer(pass.renderType().format());
            ItemStacks.renderModelLists(quads, tintLayers == null ? new int[0] : tintLayers.toIntArray(), combinedLight, combinedOverlay, poseStack, consumer);
            consumer.finish(batch -> {
                if (queued) {
                    ShaderMaskRenderQueue.enqueue(pass, batch.builder(), batch.builder().buildOrThrow(),
                        batch.uvTransform());
                } else if (net.eca.mv.SubmitPhase.levelSubmit) {
                    // 26.2 (#33): a non-world display context submitted with the level (an item display, say) is
                    // submitted before the level's clear pass; the after-level queue draws it in the frame
                    ShaderMaskRenderQueue.enqueue(pass, batch.builder(), batch.builder().buildOrThrow(),
                        batch.uvTransform());
                } else if (net.eca.client.render.GuiTexturePasses.collecting()) {
                    // A GUI / tooltip item (#37). 26.1.2 drew this from an order-1 custom-geometry task under
                    // pass.renderType() (after the item). On 26.2 that task's callback runs while renderAllFeatures
                    // PREPARES the storage, before anything is drawn, and the carrier put the pass's LegacyRenderType --
                    // whose shader has uniform blocks of its own -- on vanilla's feature path, where prepare() warns and
                    // draws nothing. Drawn here at submit, the item (drawn later by renderAllFeatures) covered it. So it
                    // is collected and drawn through LegacyGl right after renderAllFeatures returns into the GUI item
                    // atlas slot or picture-in-picture texture (GuiItemAtlasFeaturesMixin, PictureInPictureFeaturesMixin).
                    net.eca.client.render.GuiTexturePasses.add(pass, batch.builder(), batch.builder().buildOrThrow(),
                        batch.uvTransform());
                } else {
                    // A GUI submit outside those textures (none is known on 26.2): drawn at once, as before. The item
                    // may have gone into the shared buffer source, so its batch is ended first.
                    net.eca.mv.MultiBufferSource.main().endBatch();
                    ShaderMaskRenderQueue.drawNow(pass, batch.builder(), batch.builder().buildOrThrow(),
                        batch.uvTransform());
                }
            });
        }
    }

    private static boolean eca$isWorldContext(ItemDisplayContext context) {
        return switch (context) {
            case GROUND, FIXED, HEAD,
                 FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> true;
            default -> false;
        };
    }
}
