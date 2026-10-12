// Fork multi-version layer: 1.21.5 copy of the 1.21.4 mixin (the layer holds its quads, not a BakedModel). 1.21.4 renders an item layer by layer from an
// ItemStackRenderState (no stack on it): the extension pass runs at the end of LayerRenderState.render, once per
// item (net.eca.mv.ItemStacks, filled by ItemModelResolverMixin), with the layer's resolved tints.
package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.item_extension.ItemExtension;
import net.eca.util.item_extension.ItemExtensionManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.eca.mv.ItemStacks;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
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

@OnlyIn(Dist.CLIENT)
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

    @Inject(method = "render",
            at = @At(value = "INVOKE",
                     target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                     shift = At.Shift.BEFORE))
    private void eca$renderItemExtension(PoseStack poseStack, MultiBufferSource bufferSource,
                                         int combinedLight, int combinedOverlay, CallbackInfo ci) {
        ItemStack stack = ItemStacks.claim(this$0);
        if (stack == null || quads.isEmpty()) return;
        ItemDisplayContext displayContext = ((ItemStackRenderStateAccessor) this$0).eca$displayContext();
        if (stack.isEmpty()) return;
        ItemExtension extension = ItemExtensionManager.getExtension(stack.getItem());
        if (extension == null || !extension.enabled() || !extension.shouldRender(stack)) return;

        List<ShaderMaskPass> passes = extension.getShaderPasses();
        if (passes == null || passes.isEmpty()) return;
        if (bufferSource instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }

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
