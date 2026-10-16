// Fork multi-version layer: 1.21.11 version of the authors' file (GeckoLib 5.4: a layer hook gets one RenderPassInfo, bone hiding is
// a flag on the pass's BoneSnapshots, and GeoRenderer.renderBone is gone; the block renderer is GeoBlockRenderer<T, R> with a
// block-entity render state). Built on the 1.21.10 overlay. See GeoEntityExtensionLayer (same folder) for why the configured
// bones are hidden by a BoneUpdater, why the overlay draw runs inside renderPosed, and why the shader-mask passes are always
// queued instead of drawn at once.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import com.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

public class GeoBlockExtensionLayer<T extends BlockEntity & GeoAnimatable, R extends BlockEntityRenderState & GeoRenderState>
    extends GeoRenderLayer<T, Void, R> {

    /** The extension that applies to this frame's block entity, parked on the render state by {@link #addRenderData}. */
    private static final DataTicket<BlockExtension> EXTENSION =
        DataTicket.create("eca_block_extension", BlockExtension.class);

    private final GeoBoneVisibilityController boneVisibility = new GeoBoneVisibilityController();

    public GeoBlockExtensionLayer(GeoBlockRenderer<T, R> renderer) {
        super(renderer);
    }

    @Override
    public void addRenderData(T animatable, Void relatedObject, R renderState, float partialTick) {
        BlockExtension extension = BlockExtensionManager.getExtension(animatable.getBlockState().getBlock());
        renderState.addGeckolibData(EXTENSION, extension != null && animatable.getLevel() instanceof BlockAndTintGetter level
            && BlockExtensionSafeAccess.shouldRender(extension, animatable.getBlockState(),
                level, animatable.getBlockPos()) ? extension : null);
    }

    /** Before GeckoLib submits the model: hide the configured bones in the pass's snapshots, for every task of the pass. */
    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        BlockExtension extension = renderPassInfo.getGeckolibData(EXTENSION);
        if (extension == null || !renderPassInfo.willRender()) {
            return;
        }
        renderPassInfo.addBoneUpdater((info, snapshots) -> GeoBoneVisibilityController.hideInPass(snapshots, extension.hiddenGeoBones()));
    }

    /** After GeckoLib submitted the model: queue the shader-mask passes behind it. */
    @Override
    public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        BlockExtension extension = renderPassInfo.getGeckolibData(EXTENSION);
        R renderState = renderPassInfo.renderState();
        RenderType modelType = extension != null && renderPassInfo.willRender() ? modelRenderType(renderState) : null;
        if (modelType == null) {
            return;
        }
        Identifier texture = getTextureResource(renderState);
        List<ShaderMaskPass> passes = extension.getGeoShaderPasses(texture);
        if (passes == null || passes.isEmpty()) {
            return;
        }
        int light = extension.isGlow() ? 15728880 : renderPassInfo.packedLight();
        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), modelType, (pose, ignored) -> {
            for (ShaderMaskPass pass : passes) {
                renderPass(renderPassInfo, extension, pose, light, pass);
            }
        });
    }

    /** What GeckoLib's own model task draws with: the render type its renderer picks for this state. */
    private RenderType modelRenderType(R renderState) {
        return getRenderer().getRenderType(renderState, getRenderer().getTextureLocation(renderState));
    }

    private void renderPass(RenderPassInfo<R> renderPassInfo, BlockExtension extension, PoseStack.Pose pose, int light,
                            ShaderMaskPass pass) {
        if (pass == null || pass.alpha() <= 0.0f) return;
        RenderType renderType = pass.renderType();
        BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS, renderType.format());
        // GeckoLib's own model task (GeoRenderer.submitRenderTasks), into our buffer: push the pass's pose stack, set it to the
        // pose the task was submitted with, draw inside renderPosed where the bones' snapshots are live. Always queued
        // (flushed after the level): drawing at once would have to endBatch() the buffer the feature renderer is writing into.
        PoseStack poseStack = renderPassInfo.poseStack();
        poseStack.pushPose();
        poseStack.last().set(pose);
        renderPassInfo.renderPosed(() -> {
            try {
                boneVisibility.begin(renderPassInfo.model(), extension.hiddenGeoBones());
                boneVisibility.restrictOverlay(renderPassInfo.model(), extension.overlayGeoBones());
                renderPassInfo.model().render(renderPassInfo, builder, light, OverlayTexture.NO_OVERLAY, -1);
            } finally {
                boneVisibility.restore();
            }
        });
        poseStack.popPose();
        ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
    }
}
