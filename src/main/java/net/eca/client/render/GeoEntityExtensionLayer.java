// Fork multi-version layer: 1.21.10 version of the authors' file (GeckoLib 5.3: a layer SUBMITS render tasks that run later,
// at draw time, instead of drawing into a buffer it is handed; there is no reRender). Built on the 1.21.5 overlay (GeckoLib 5:
// the extension lookup lives in addRenderData and is parked on the GeoRenderState).
//
// What changed in how the authors' drawing is driven, and why it is arranged this way:
//  * GeckoLib 5.3 submits the model as custom geometry and renders it LATER, after every entity has been submitted, and it
//    re-poses the shared BakedGeoModel at that moment. Bone visibility is live state on the shared model, so it can no longer
//    be hidden around a call. Instead the hide / restore steps are themselves custom-geometry tasks submitted under the SAME
//    RenderType as the model: the collector runs one RenderType's tasks in submission order, so for every entity the order is
//    hide -> GeckoLib's model task -> overlay passes -> restore, which is the order the 1.21.1 code ran in.
//  * The passes that draw into the mod's own BufferBuilder (the shader-mask passes, and the textured pass when Oculus shaders
//    are active) are drawn inside such a task, with GeckoLib's own renderBone, and are always QUEUED
//    (ShaderMaskRenderQueue.enqueue, flushed after the level) -- the old non-Oculus path called endBatch() and drawNow(),
//    and endBatch() in the middle of a feature renderer's own batch would close the buffer it is still writing into.
//  * The plain textured pass (no Oculus) is a second GeckoLib model task with the translucent render type, bracketed by the
//    same hide / restore tasks.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

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

    /** Before GeckoLib submits the model: queue the bone hiding to run right before the model's own task. */
    @Override
    public void preRender(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, SubmitNodeCollector renderTasks,
                          CameraRenderState cameraState, int packedLight, int packedOverlay, int renderColor,
                          boolean didRenderModel) {
        EntityLayerExtension extension = renderState.getGeckolibData(EXTENSION);
        RenderType modelType = extension != null && didRenderModel ? modelRenderType(renderState) : null;
        if (modelType == null) {
            return;
        }
        renderTasks.submitCustomGeometry(poseStack, modelType,
            (pose, ignored) -> boneVisibility.begin(bakedModel, extension));
    }

    /** After GeckoLib submitted the model: queue the overlay passes, then the restore, behind it. */
    @Override
    public void submitRenderTask(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, SubmitNodeCollector renderTasks,
                                 CameraRenderState cameraState, int packedLight, int packedOverlay, int renderColor,
                                 boolean didRenderModel) {
        EntityLayerExtension extension = renderState.getGeckolibData(EXTENSION);
        RenderType modelType = extension != null && didRenderModel ? modelRenderType(renderState) : null;
        if (modelType == null) {
            return;
        }

        ResourceLocation texture = extension.getTexture();
        List<ShaderMaskPass> shaderPasses = extension.getShaderPasses();
        if (shaderPasses == null) shaderPasses = List.of();
        boolean hasTexture = texture != null;
        if (shaderPasses.isEmpty() && !hasTexture) {
            // nothing to draw; still undo the hiding preRender queued
            renderTasks.submitCustomGeometry(poseStack, modelType, (pose, ignored) -> boneVisibility.restore());
            return;
        }

        int light = extension.isGlow() ? 15728880 : packedLight;
        int overlay = extension.isHurtOverlay() ? packedOverlay : OverlayTexture.NO_OVERLAY;
        int alphaColor = ARGB.colorFromFloat(extension.getAlpha(), 1.0f, 1.0f, 1.0f);
        boolean oculus = EcaShaderInstance.isOculusShadersActive();
        RenderType texturedType = hasTexture ? RenderType.entityTranslucent(texture) : null;
        List<ShaderMaskPass> passes = shaderPasses;

        // the plain textured pass is a model task of its own, in its own render type's list, so it carries its own bone
        // visibility (hide the configured bones, keep only the overlay roots) and undoes it afterwards
        if (texturedType != null && !oculus) {
            // order 1: after every order-0 task, like GeckoLib's own texture layer, so the translucent pass lands over the models
            OrderedSubmitNodeCollector later = renderTasks.order(1);
            later.submitCustomGeometry(poseStack, texturedType, (pose, ignored) -> {
                boneVisibility.begin(bakedModel, extension);
                boneVisibility.restrictOverlay(bakedModel, extension.overlayGeoBones());
            });
            this.renderer.buildRenderTask(renderState, poseStack, bakedModel, this.renderer.getGeoModel(), later,
                cameraState, texturedType, light, overlay, alphaColor, null);
            later.submitCustomGeometry(poseStack, texturedType, (pose, ignored) -> boneVisibility.restore());
        }

        // everything that draws into the mod's own builder runs behind the model's task in the model's own list
        renderTasks.submitCustomGeometry(poseStack, modelType, (pose, ignored) -> {
            try {
                boneVisibility.restrictOverlay(bakedModel, extension.overlayGeoBones());
                if (texturedType != null && oculus) {
                    ShaderMaskRenderQueue.render(ShaderMaskPass.unmasked(texturedType, 1.0f), true,
                        builder -> drawModel(pose, renderState, bakedModel, cameraState, builder, light, overlay, alphaColor));
                }
                for (ShaderMaskPass pass : passes) {
                    if (pass == null || pass.alpha() <= 0.0f) continue;
                    ShaderMaskRenderQueue.render(pass, true,
                        builder -> drawModel(pose, renderState, bakedModel, cameraState, builder, light, overlay, -1));
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

    /** GeckoLib's draw callback for one model (the bones, from the pose the task was submitted with) into a buffer of ours. */
    private void drawModel(PoseStack.Pose pose, R renderState, BakedGeoModel bakedModel, CameraRenderState cameraState,
                           VertexConsumer consumer, int light, int overlay, int color) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        for (GeoBone bone : bakedModel.topLevelBones()) {
            this.renderer.renderBone(renderState, stack, bone, consumer, cameraState, light, overlay, color);
        }
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
