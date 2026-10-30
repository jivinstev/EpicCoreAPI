// Fork multi-version layer: 1.21.11 version of the authors' file (GeckoLib 5.4). Built on the 1.21.10 overlay, whose design is
// kept: a layer SUBMITS render tasks that run later, at draw time, and every overlay pass is drawn by a custom-geometry task
// submitted under the model's own RenderType, so for one entity the order stays model -> overlay passes.
//
// What GeckoLib 5.4 changed under that design:
//  * A layer hook gets one RenderPassInfo<R> (state, pose stack, baked model, camera, willRender) instead of eight loose
//    parameters; addPerBoneRender is the third hook and is not needed here.
//  * Bone hiding is no longer state on the shared GeoBone. A pass owns BoneSnapshots (GeoBone.frameSnapshot, live only inside
//    RenderPassInfo.renderPosed), so the configured bones are hidden by a BoneUpdater added in preRender -- which hides them for
//    the model's own task as well, as before -- and each overlay draw runs inside renderPosed and flips / restores the
//    overlay-only flags (restrictOverlay) itself. See GeoBoneVisibilityController.
//  * GeoRenderer.buildRenderTask / renderBone are gone. The overlay draws exactly what GeckoLib's own task does
//    (pose-stack push, set to the task's pose, renderPosed -> BakedGeoModel.render), with the layer's light / overlay / colour.
//  * The plain textured pass (no Oculus) is a task of its own at order(1), like GeckoLib's TextureLayerGeoLayer, so the
//    translucent pass lands over the models; with Oculus, and for the shader-mask passes, the geometry goes into the mod's own
//    BufferBuilder and is QUEUED (ShaderMaskRenderQueue.enqueue, flushed at AfterTranslucentFeatures or after the level) -- endBatch() in the middle of a
//    feature renderer's own batch would close the buffer it is still writing into.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;
import java.util.function.BiFunction;

public class GeoEntityExtensionLayer<T extends GeoAnimatable, O, R extends GeoRenderState> extends GeoRenderLayer<T, O, R> {

    /** The extension that applies to this frame's entity, parked on the render state by {@link #addRenderData}. */
    private static final DataTicket<EntityLayerExtension> EXTENSION =
        DataTicket.create("eca_entity_layer_extension", EntityLayerExtension.class);

    private final GeoBoneVisibilityController boneVisibility = new GeoBoneVisibilityController();
    /** (animatable, related object) -> the entity: the animatable itself for a GeoEntity, the replaced entity otherwise. */
    private final BiFunction<T, O, Entity> entityResolver;

    public GeoEntityExtensionLayer(GeoRenderer<T, O, R> renderer, BiFunction<T, O, Entity> entityResolver) {
        super(renderer);
        this.entityResolver = entityResolver;
    }

    @Override
    public void addRenderData(T animatable, O relatedObject, R renderState, float partialTick) {
        renderState.addGeckolibData(EXTENSION, findExtension(entityResolver.apply(animatable, relatedObject)));
    }

    /** Before GeckoLib submits the model: hide the configured bones in the pass's snapshots, for every task of the pass. */
    @Override
    public void preRender(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        EntityLayerExtension extension = renderPassInfo.getGeckolibData(EXTENSION);
        if (extension == null || !renderPassInfo.willRender()) {
            return;
        }
        renderPassInfo.addBoneUpdater((info, snapshots) -> GeoBoneVisibilityController.hideInPass(snapshots, extension.hiddenGeoBones()));
    }

    /** After GeckoLib submitted the model: queue the overlay passes behind it. */
    @Override
    public void submitRenderTask(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
        EntityLayerExtension extension = renderPassInfo.getGeckolibData(EXTENSION);
        RenderType modelType = extension != null && renderPassInfo.willRender() ? modelRenderType(renderPassInfo.renderState()) : null;
        if (modelType == null) {
            return;
        }

        Identifier texture = extension.getTexture();
        List<ShaderMaskPass> shaderPasses = extension.getShaderPasses();
        if (shaderPasses == null) shaderPasses = List.of();
        boolean hasTexture = texture != null;
        if (shaderPasses.isEmpty() && !hasTexture) {
            return;
        }

        int light = extension.isGlow() ? 15728880 : renderPassInfo.packedLight();
        int overlay = extension.isHurtOverlay() ? renderPassInfo.packedOverlay() : OverlayTexture.NO_OVERLAY;
        int alphaColor = ARGB.colorFromFloat(extension.getAlpha(), 1.0f, 1.0f, 1.0f);
        boolean oculus = EcaShaderInstance.isOculusShadersActive();
        RenderType texturedType = hasTexture ? RenderTypes.entityTranslucent(texture) : null;
        List<ShaderMaskPass> passes = shaderPasses;

        if (texturedType != null && !oculus) {
            // order 1: after every order-0 task, like GeckoLib's own texture layer, so the translucent pass lands over the models
            OrderedSubmitNodeCollector later = renderTasks.order(1);
            later.submitCustomGeometry(renderPassInfo.poseStack(), texturedType,
                (pose, consumer) -> drawModel(renderPassInfo, extension, pose, consumer, light, overlay, alphaColor));
        }

        // everything that draws into the mod's own builder runs behind the model's task in the model's own list
        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), modelType, (pose, ignored) -> {
            if (texturedType != null && oculus) {
                ShaderMaskRenderQueue.render(ShaderMaskPass.unmasked(texturedType, 1.0f), true,
                    builder -> drawModel(renderPassInfo, extension, pose, builder, light, overlay, alphaColor));
            }
            for (ShaderMaskPass pass : passes) {
                if (pass == null || pass.alpha() <= 0.0f) continue;
                ShaderMaskRenderQueue.render(pass, true,
                    builder -> drawModel(renderPassInfo, extension, pose, builder, light, overlay, -1));
            }
        });
    }

    /** What GeckoLib's own model task draws with: the render type its renderer picks for this state. */
    private RenderType modelRenderType(R renderState) {
        return getRenderer().getRenderType(renderState, getRenderer().getTextureLocation(renderState));
    }

    /**
     * GeckoLib's own model task (its GeoRenderer.submitRenderTasks), into a consumer of ours: push the pass's pose stack, set it
     * to the pose the task was submitted with, and draw inside renderPosed, where the bones' snapshots are live. The overlay-only
     * bone flags are flipped there and restored before the snapshots go back to the next task.
     */
    private void drawModel(RenderPassInfo<R> renderPassInfo, EntityLayerExtension extension, PoseStack.Pose pose,
                           VertexConsumer consumer, int light, int overlay, int color) {
        PoseStack poseStack = renderPassInfo.poseStack();
        poseStack.pushPose();
        poseStack.last().set(pose);
        renderPassInfo.renderPosed(() -> {
            try {
                boneVisibility.begin(renderPassInfo.model(), extension);
                boneVisibility.restrictOverlay(renderPassInfo.model(), extension.overlayGeoBones());
                renderPassInfo.model().render(renderPassInfo, consumer, light, overlay, color);
            } finally {
                boneVisibility.restore();
            }
        });
        poseStack.popPose();
    }

    private EntityLayerExtension findExtension(Entity entity) {
        if (entity == null) return null;
        EntityExtension extension = EntityExtensionManager.getExtension(entity.getType());
        if (extension == null) return null;
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        EntityLayerExtension layer = EntityExtensionSafeAccess.entityLayerExtension(extension, living);
        return layer != null && layer.enabled() && layer.shouldRender(living) ? layer : null;
    }
}
