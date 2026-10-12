// Fork multi-version layer: 1.21.10 version of the authors' file (GeckoLib 5.3: a layer submits render tasks that run later,
// at draw time; there is no reRender, and the block renderer is GeoBlockRenderer<T, R> with a block-entity render state).
// Built on the 1.21.5 overlay. See GeoEntityExtensionLayer (same folder) for why the bone hiding is a pair of custom-geometry
// tasks around GeckoLib's own model task and why the shader-mask passes are always queued instead of drawn at once.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

@OnlyIn(Dist.CLIENT)
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
        renderState.addGeckolibData(EXTENSION, extension != null && animatable.getLevel() != null
            && BlockExtensionSafeAccess.shouldRender(extension, animatable.getBlockState(),
                animatable.getLevel(), animatable.getBlockPos()) ? extension : null);
    }

    /** Before GeckoLib submits the model: queue the bone hiding to run right before the model's own task. */
    @Override
    public void preRender(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, SubmitNodeCollector renderTasks,
                          CameraRenderState cameraState, int packedLight, int packedOverlay, int renderColor,
                          boolean didRenderModel) {
        BlockExtension extension = renderState.getGeckolibData(EXTENSION);
        RenderType modelType = extension != null && didRenderModel ? modelRenderType(renderState) : null;
        if (modelType == null) {
            return;
        }
        renderTasks.submitCustomGeometry(poseStack, modelType,
            (pose, ignored) -> boneVisibility.begin(bakedModel, extension.hiddenGeoBones()));
    }

    /** After GeckoLib submitted the model: queue the shader-mask passes, then the restore, behind it. */
    @Override
    public void submitRenderTask(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, SubmitNodeCollector renderTasks,
                                 CameraRenderState cameraState, int packedLight, int packedOverlay, int renderColor,
                                 boolean didRenderModel) {
        BlockExtension extension = renderState.getGeckolibData(EXTENSION);
        RenderType modelType = extension != null && didRenderModel ? modelRenderType(renderState) : null;
        if (modelType == null) {
            return;
        }
        ResourceLocation texture = getTextureResource(renderState);
        List<ShaderMaskPass> passes = extension.getGeoShaderPasses(texture);
        if (passes == null || passes.isEmpty()) {
            // nothing to draw; still undo the hiding preRender queued
            renderTasks.submitCustomGeometry(poseStack, modelType, (pose, ignored) -> boneVisibility.restore());
            return;
        }
        int light = extension.isGlow() ? 15728880 : packedLight;
        renderTasks.submitCustomGeometry(poseStack, modelType, (pose, ignored) -> {
            try {
                boneVisibility.restrictOverlay(bakedModel, extension.overlayGeoBones());
                for (ShaderMaskPass pass : passes) {
                    renderPass(pose, renderState, bakedModel, cameraState, light, pass);
                }
            } finally {
                boneVisibility.restore();
            }
        });
    }

    /** What GeckoLib's own model task draws with: the render type its renderer picks for this state. */
    private RenderType modelRenderType(R renderState) {
        return this.renderer.getRenderType(renderState, this.renderer.getTextureLocation(renderState));
    }

    private void renderPass(PoseStack.Pose pose, R renderState, BakedGeoModel bakedModel, CameraRenderState cameraState,
                            int light, ShaderMaskPass pass) {
        if (pass == null || pass.alpha() <= 0.0f) return;
        RenderType renderType = pass.renderType();
        BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(VertexFormat.Mode.QUADS, renderType.format());
        // GeckoLib's draw callback for one model: the bones, from the pose the task was submitted with, into our buffer.
        // Always queued (flushed after the level): drawing at once would have to endBatch() the buffer the feature renderer
        // is writing into at this very moment.
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        VertexConsumer consumer = builder;
        for (GeoBone bone : bakedModel.topLevelBones()) {
            this.renderer.renderBone(renderState, stack, bone, consumer, cameraState, light, OverlayTexture.NO_OVERLAY, -1);
        }
        ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
    }
}
