// 26.3: the layer's quads are an ItemQuads (all / solid / translucent); the extension pass draws all() as before.
// Fork multi-version layer: 26.1.2 copy (tintLayers is an IntList) of the 1.21.10 copy of the 1.21.5 mixin. 1.21.9 draws items in two steps: LayerRenderState.submit hands the
// item's quads to a SubmitNodeCollector and a feature renderer draws them later. The extension pass is therefore built at
// the end of submit (the pose stack is only right now) and, when it must be drawn at once (a GUI / tooltip item, not a world
// one), the drawing is a custom-geometry task in the collector's order 1 -- after every order-0 task, the item's own among them.
// The 1.21.4 notes still hold: the extension pass runs once per item (net.eca.mv.ItemStacks, filled by ItemModelResolverMixin),
// with the layer's resolved tints.
// Once per SUBMIT of the state, not per fill (port of 1.21.5's 14fe0c4 / 1.21.4's 22fcf95): javap 1.21.10 --
// ItemEntityRenderer.submitMultipleFromCount fills one ItemStackRenderState and calls its submit once per copy (up to five),
// ItemStackRenderState keeps private LayerRenderState[] layers, clear() clears each active layer, LayerRenderState.submit
// ends with popPose -- the 1.21.5 shapes with render renamed submit. So the pass runs for the state's first layer on every
// submit; the stack is claimed once per fill and kept on that layer, reset at LayerRenderState.clear HEAD. The layers
// accessor (ItemStackRenderStateLayersAccessor) reaches this target from 1.21.5's mixin_add.
// 1.21.11 (port of 1.21.10's): ItemStackRenderState still keeps private LayerRenderState[] layers whose clear() runs on
// each refill, LayerRenderState.submit still ends with popPose, and ItemEntityRenderer.submitMultipleFromCount still submits
// one fill up to five times, so the once-per-submit pass applies unchanged.
// 26.1.2 (javap minecraft-patched-26.1.2.116): the same shapes (private LayerRenderState[] layers, LayerRenderState.clear,
// submit ending with popPose, submitMultipleFromCount submitting one fill per copy), so the pass applies unchanged.
// 26.2 (javap minecraft-patched-26.2.0.90): the same shapes again, so the once-per-submit pass applies unchanged.
// 26.3 (javap minecraft-patched-26.3.0.76): the same shapes again (private LayerRenderState[] layers, LayerRenderState.clear,
// private submit ending with popPose, submitMultipleFromCount submitting one fill per copy), so the pass applies unchanged.
// 26.3 (#33): no custom-geometry task any more. 26.3 runs a task's callback while the frame is PREPARED (prepareFrame), before
// anything is drawn, and the carrier put the pass's LegacyRenderType on vanilla's feature path. A pass submitted with the
// level is queued after the level (net.eca.mv.SubmitPhase); a GUI / tooltip pass is drawn into the offscreen texture's own
// pass (net.eca.client.render.GuiTexturePasses, see eca$renderItemExtension).
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
import org.spongepowered.asm.mixin.Unique;
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
    private net.minecraft.client.resources.model.geometry.ItemQuads quads;   // 26.3: all / solid / translucent

    // 26.1: the layer's tints are a nullable IntList (int[] up to 1.21.11)
    @Shadow
    private it.unimi.dsi.fastutil.ints.IntList tintLayers;

    @Shadow
    @Final
    ItemStackRenderState this$0;

    /* The stack this state was filled from, kept on its first layer: ItemStacks.claim hands it out once per fill, and
       the same fill is submitted once per copy of a dropped stack. Reset whenever the layer is cleared (the state's
       clear() clears every active layer before a refill), so a state refilled without a recorded stack draws no pass. */
    @Unique
    private ItemStack eca$stack;

    @Inject(method = "clear", at = @At("HEAD"))
    private void eca$forgetStack(CallbackInfo ci) {
        eca$stack = null;
    }

    @Inject(method = "submit",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                     shift = At.Shift.BEFORE))
    private void eca$renderItemExtension(PoseStack poseStack, SubmitNodeCollector collector,
                                         int combinedLight, int combinedOverlay, int outlineColor, CallbackInfo ci) {
        if (((ItemStackRenderStateLayersAccessor) this$0).eca$layers()[0] != (Object) this) return;
        ItemStack claimed = ItemStacks.claim(this$0);
        if (claimed != null) eca$stack = claimed;
        ItemStack stack = eca$stack;
        if (stack == null || quads.isEmpty()) return;
        ItemDisplayContext displayContext = ((ItemStackRenderStateAccessor) this$0).eca$displayContext();
        if (stack.isEmpty()) return;
        ItemExtension extension = ItemExtensionManager.getExtension(stack.getItem());
        if (extension == null || !extension.enabled() || !extension.shouldRender(stack)) return;

        List<ShaderMaskPass> passes = extension.getShaderPasses();
        if (passes == null || passes.isEmpty()) return;

        boolean queued = eca$isWorldContext(displayContext);
        if (!queued && !net.eca.mv.SubmitPhase.levelSubmit) {
            // 26.3 (#33): a GUI / tooltip item. On 26.3 every such item is drawn into an offscreen texture -- GuiItemAtlas's
            // slot (redrawn only when the item changes), an oversized item's or a picture-in-picture texture -- in a render
            // pass the dispatcher opens on that texture. The fork layer's helpers have no output override on 26.3 and draw
            // into the main target, so drawing the pass at submit (or from the old order-1 task, whose callback ran inside
            // prepareFrame) put it on the main framebuffer at the atlas' coordinates and projection. So the pass is
            // collected and drawn INTO the pass vanilla opens on that texture, right after its features
            // (GuiTexturePasses; the shared helper's LegacyGl.draw(mesh, pass)). A GUI submit outside those textures
            // (none is known on 26.3) is still not drawn (logged once).
            if (!net.eca.client.render.GuiTexturePasses.collecting()) {
                eca$warnGuiPassSkipped();
                return;
            }
            for (ShaderMaskPass pass : passes) {
                if (pass == null || pass.alpha() <= 0.0f) continue;
                try (SpriteBatchingVertexConsumer consumer = new SpriteBatchingVertexConsumer(pass.renderType().format())) {
                    ItemStacks.renderModelLists(quads.all(), tintLayers == null ? new int[0] : tintLayers.toIntArray(), combinedLight, combinedOverlay, poseStack, consumer);
                    consumer.finish(batch -> net.eca.client.render.GuiTexturePasses.add(pass, batch.builder(),
                        batch.builder().buildOrThrow(), batch.uvTransform()));
                }
            }
            return;
        }
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            try (SpriteBatchingVertexConsumer consumer = new SpriteBatchingVertexConsumer(pass.renderType().format())) {
                ItemStacks.renderModelLists(quads.all(), tintLayers == null ? new int[0] : tintLayers.toIntArray(), combinedLight, combinedOverlay, poseStack, consumer);
                // a world context, or (26.3, #33) any context submitted with the level (an item display, say), which is
                // submitted before the level's clear pass: the after-level queue draws it in the frame
                consumer.finish(batch -> ShaderMaskRenderQueue.enqueue(pass, batch.builder(), batch.builder().buildOrThrow(),
                    batch.uvTransform()));
            }
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean eca$guiPassWarned;

    @org.spongepowered.asm.mixin.Unique
    private static void eca$warnGuiPassSkipped() {
        if (!eca$guiPassWarned) {
            eca$guiPassWarned = true;
            com.mojang.logging.LogUtils.getLogger().warn("[mv 26.3] item extension shader passes are not drawn on a GUI item "
                + "submitted outside an offscreen GUI texture (GuiItemAtlas / picture-in-picture)");
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
