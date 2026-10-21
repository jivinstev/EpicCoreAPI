// Fork multi-version layer: 1.21.10 copy of the 1.21.5 mixin. 1.21.9 draws items in two steps: LayerRenderState.submit hands the
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
import net.minecraft.client.renderer.block.model.BakedQuad;
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

    @Shadow
    private int[] tintLayers;

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
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            SpriteBatchingVertexConsumer consumer = new SpriteBatchingVertexConsumer(pass.renderType().format());
            ItemStacks.renderModelLists(quads, tintLayers, combinedLight, combinedOverlay, poseStack, consumer);
            consumer.finish(batch -> {
                if (queued) {
                    ShaderMaskRenderQueue.enqueue(pass, batch.builder(), batch.builder().buildOrThrow(),
                        batch.uvTransform());
                } else {
                    // after the item: the collector runs order 1 behind order 0. The item went into the shared buffer source,
                    // so end its batch first (the task's own buffer, for pass.renderType(), is never written to, so ending
                    // the batch under it is harmless).
                    collector.order(1).submitCustomGeometry(poseStack, pass.renderType(), (pose, ignored) -> {
                        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                        ShaderMaskRenderQueue.drawNow(pass, batch.builder(), batch.builder().buildOrThrow(),
                            batch.uvTransform());
                    });
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
