package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import it.unimi.dsi.fastutil.ints.IntList;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.item_extension.ItemExtension;
import net.eca.util.item_extension.ItemExtensionManager;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/* 26.x 的物品渲染状态按图层提交（LayerRenderState.submit），模型四边形与着色参数都在图层上，
   所以挂在图层的 submit 里、位于 popPose 之前（此时位姿栈已应用该图层的变换）。 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class ItemRendererMixin {

    @Shadow
    private List<BakedQuad> quads;

    @Shadow
    private IntList tintLayers;

    @Inject(method = "submit", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void eca$renderItemExtension(PoseStack poseStack, SubmitNodeCollector collector,
                                         int combinedLight, int combinedOverlay, int outlineColor,
                                         CallbackInfo ci) {
        // TODO: ItemStack and ItemDisplayContext are no longer parameters; they must be obtained from the render state
        ItemStack stack = ItemStack.EMPTY;
        ItemDisplayContext displayContext = ItemDisplayContext.NONE;
        if (stack.isEmpty()) return;
        ItemExtension extension = ItemExtensionManager.getExtension(stack.getItem());
        if (extension == null || !extension.enabled() || !extension.shouldRender(stack)) return;

        List<ShaderMaskPass> passes = extension.getShaderPasses();
        if (passes == null || passes.isEmpty()) return;
        boolean queued = eca$isWorldContext(displayContext);
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            SpriteBatchingVertexConsumer consumer = new SpriteBatchingVertexConsumer(pass.renderType().format());
            for (BakedQuad quad : quads) {
                QuadInstance instance = new QuadInstance();
                instance.setLightCoords(combinedLight);
                instance.setOverlayCoords(combinedOverlay);
                int tint = quad.materialInfo().tintIndex();
                if (tintLayers != null && tint >= 0 && tint < tintLayers.size()) {
                    instance.setColor(tintLayers.getInt(tint));
                }
                consumer.putBakedQuad(poseStack.last(), quad, instance);
            }
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
