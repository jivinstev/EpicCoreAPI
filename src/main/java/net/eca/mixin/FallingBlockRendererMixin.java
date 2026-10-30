// Fork multi-version layer: 1.21.10 copy of the 1.21.5 version (BakedModel is BlockStateModel/BlockModelParts; ModelData comes from collectParts) of the authors' mixin.
// 1.21.9: FallingBlockRenderer SUBMITS the render state to a SubmitNodeCollector (render -> submit, and a CameraRenderState); the
// extension passes are built here, at the end of submit, exactly as before (they were never drawn through the buffer source).
package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

    @Inject(method = "submit", at = @At("TAIL"))
    private void eca$renderBlockExtension(FallingBlockRenderState renderState, PoseStack poseStack,
                                          SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
        // fork layer (1.21.2 render state): the entity is parked on the state by net.eca.mv.EntityOf
        if (!(net.eca.mv.EntityOf.get(renderState) instanceof FallingBlockEntity entity)) {
            return;
        }
        BlockState state = entity.getBlockState();
        net.minecraft.client.renderer.block.BlockAndTintGetter tintLevel =
            (net.minecraft.client.renderer.block.BlockAndTintGetter) entity.level();
        BlockExtension extension = BlockExtensionManager.getExtension(state.getBlock());
        if (extension == null || state.getRenderShape() != RenderShape.MODEL
            || state == entity.level().getBlockState(entity.blockPosition())
            || !BlockExtensionSafeAccess.shouldRender(extension, state, tintLevel, entity.blockPosition())) {
            return;
        }
        List<ShaderMaskPass> passes = extension.getBlockShaderPasses();
        if (passes == null || passes.isEmpty()) return;

        BlockPos renderPos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        ModelBlockRenderer blockRenderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
        long seed = state.getSeed(entity.getStartPos());
        boolean fullBright = BlockExtensionSafeAccess.isGlow(extension);
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            try (SpriteBatchingVertexConsumer consumer =
                     new SpriteBatchingVertexConsumer(pass.renderType().format(), fullBright)) {
                poseStack.pushPose();
                try {
                    poseStack.translate(-0.5, 0.0, -0.5);
                    blockRenderer.tesselateBlock((x, y, z, quad, instance) -> {
                        poseStack.pushPose();
                        poseStack.translate(x, y, z);
                        consumer.putBakedQuad(poseStack.last(), quad, instance);
                        poseStack.popPose();
                    }, 0.0F, 0.0F, 0.0F, tintLevel, renderPos, state, model, seed);
                } finally {
                    poseStack.popPose();
                }
                consumer.finish(batch -> ShaderMaskRenderQueue.enqueue(pass, batch.builder(),
                    batch.builder().buildOrThrow(), batch.uvTransform()));
            }
        }
    }
}
