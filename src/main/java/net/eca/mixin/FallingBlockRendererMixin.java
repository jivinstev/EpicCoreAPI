// Fork multi-version layer: 1.21.5 copy of the 1.21.2 version (BakedModel is BlockStateModel/BlockModelParts; ModelData comes from collectParts) of the authors' mixin (FallingBlockRenderer renders a render state).
package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void eca$renderBlockExtension(FallingBlockRenderState renderState,
                                          PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, CallbackInfo ci) {
        // fork layer (1.21.2 render state): the entity is parked on the state by net.eca.mv.EntityOf
        if (!(net.eca.mv.EntityOf.get(renderState) instanceof FallingBlockEntity entity)) {
            return;
        }
        BlockState state = entity.getBlockState();
        BlockExtension extension = BlockExtensionManager.getExtension(state.getBlock());
        if (extension == null || state.getRenderShape() != RenderShape.MODEL
            || state == entity.level().getBlockState(entity.blockPosition())
            || !BlockExtensionSafeAccess.shouldRender(extension, state, entity.level(), entity.blockPosition())) {
            return;
        }
        List<ShaderMaskPass> passes = extension.getBlockShaderPasses();
        if (passes == null || passes.isEmpty()) return;

        BlockPos renderPos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        BlockStateModel model = minecraft.getBlockRenderer().getBlockModel(state);
        List<BlockModelPart> parts = model.collectParts(entity.level(), renderPos, state, RandomSource.create(state.getSeed(entity.getStartPos())));
        boolean fullBright = BlockExtensionSafeAccess.isGlow(extension);
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            try (SpriteBatchingVertexConsumer consumer =
                     new SpriteBatchingVertexConsumer(pass.renderType().format(), fullBright)) {
                poseStack.pushPose();
                try {
                    poseStack.translate(-0.5, 0.0, -0.5);
                    minecraft.getBlockRenderer().getModelRenderer().tesselateBlock(entity.level(), parts, state, renderPos,
                        poseStack, consumer, false, 0);
                } finally {
                    poseStack.popPose();
                }
                consumer.finish(batch -> ShaderMaskRenderQueue.enqueue(pass, batch.builder(),
                    batch.builder().buildOrThrow(), batch.uvTransform()));
            }
        }
    }
}
