package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.ShaderMaskPass;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.SpriteBatchingVertexConsumer;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void eca$renderBlockExtension(FallingBlockEntity entity, float yaw, float partialTick,
                                          PoseStack poseStack, SubmitNodeCollector bufferSource,
                                          int packedLight, CallbackInfo ci) {
        BlockState state = entity.getBlockState();
        BlockExtension extension = BlockExtensionManager.getExtension(state.getBlock());
        if (!(entity.level() instanceof ClientLevel level) || extension == null
            || state.getRenderShape() != RenderShape.MODEL
            || state == level.getBlockState(entity.blockPosition())
            || !BlockExtensionSafeAccess.shouldRender(extension, state, level, entity.blockPosition())) {
            return;
        }
        List<ShaderMaskPass> passes = extension.getBlockShaderPasses();
        if (passes == null || passes.isEmpty()) return;

        BlockPos renderPos = BlockPos.containing(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
        Minecraft minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        boolean fullBright = BlockExtensionSafeAccess.isGlow(extension);
        for (ShaderMaskPass pass : passes) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            SpriteBatchingVertexConsumer consumer =
                new SpriteBatchingVertexConsumer(pass.renderType().format(), fullBright);
            poseStack.pushPose();
            poseStack.translate(-0.5, 0.0, -0.5);
            BlockQuadOutput output = (x, y, z, quad, instance) -> {
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                consumer.putBakedQuad(poseStack.last(), quad, instance);
                poseStack.popPose();
            };
            new ModelBlockRenderer(true, false, minecraft.getBlockColors()).tesselateBlock(output, 0.0F, 0.0F, 0.0F,
                level, renderPos, state, model, state.getSeed(entity.getStartPos()));
            poseStack.popPose();
            consumer.finish(batch -> ShaderMaskRenderQueue.enqueue(pass, batch.builder(),
                batch.builder().buildOrThrow(), batch.uvTransform()));
        }
    }
}
