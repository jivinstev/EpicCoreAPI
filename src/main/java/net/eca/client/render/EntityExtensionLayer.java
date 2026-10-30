// Fork multi-version layer: 1.21.2 version of the authors' file (render state instead of entity).
// 1.21.9+ (ECA-006): the layer only submits; the non-Oculus textured pass and shader passes are order-1 submits drawn after the body.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.eca.mv.EntityOf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class EntityExtensionLayer<S extends LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super S>>
    extends RenderLayer<S, M> {

    public EntityExtensionLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, S state,
                       float netHeadYaw, float headPitch) {
        // fork layer (1.21.2 render state): the entity is parked on the state by net.eca.mv.EntityOf
        if (!(EntityOf.get(state) instanceof LivingEntity entity)) {
            return;
        }

        EntityExtension extension = EntityExtensionManager.getExtension(entity.getType());
        if (extension == null) {
            return;
        }

        if (BlenderEntityBindings.replacesBody(BlenderEntityBindings.resolve(entity))) {
            return;
        }

        EntityLayerExtension layerExtension = EntityExtensionSafeAccess.entityLayerExtension(extension, entity);
        if (layerExtension == null || !layerExtension.enabled() || !layerExtension.shouldRender(entity)) {
            return;
        }

        ResourceLocation texture = layerExtension.getTexture();
        List<ShaderMaskPass> shaderPasses = layerExtension.getShaderPasses();
        if (shaderPasses == null) {
            shaderPasses = List.of();
        }
        if (shaderPasses.isEmpty() && texture == null) {
            return;
        }

        int light = layerExtension.isGlow() ? 15728880 : packedLight;
        int overlay = layerExtension.isHurtOverlay()
            ? LivingEntityRenderer.getOverlayCoords(state, 0.0f)
            : OverlayTexture.NO_OVERLAY;
        float alpha = layerExtension.getAlpha();

        boolean hasTexture = texture != null;
        boolean oculus = EcaShaderInstance.isOculusShadersActive();

        if (hasTexture) {
            RenderType texturedLayer = RenderType.entityTranslucent(texture);
            if (oculus) {
                ShaderMaskRenderQueue.render(ShaderMaskPass.unmasked(texturedLayer, 1.0f), true,
                    builder -> this.getParentModel().renderToBuffer(
                        poseStack, builder, light, overlay,
                        ARGB.color((int) (alpha * 255.0f), 255, 255, 255)));
            } else {
                // order 1: drawn by the feature dispatch after every order-0 model (the body), like vanilla's own layers
                collector.order(1).submitModel(this.getParentModel(), state, poseStack, texturedLayer, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255), null, state.outlineColor, null);
            }
        }

        for (ShaderMaskPass pass : shaderPasses) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            if (oculus) {
                ShaderMaskRenderQueue.render(pass, true, builder -> this.getParentModel().renderToBuffer(
                    poseStack, builder, light, overlay, 0xFFFFFFFF));
            } else {
                // 1.21.9+: the body is only submitted here and drawn later by the feature dispatch, so drawing the pass now
                // (as 1.21.1's endBatch() + drawNow did) put it under the body. An order-1 task runs after every order-0
                // model, inside whatever bracket dispatches the features (level, force-loaded fog, filter spotlight);
                // it re-poses the shared model for this state (as vanilla's model feature does) and draws the pass at once.
                // It does not end the dispatcher's batch: the pass is drawn from the mod's own builder.
                collector.order(1).submitCustomGeometry(poseStack, pass.renderType(), (pose, ignored) -> {
                    M model = this.getParentModel();
                    model.setupAnim(state);
                    PoseStack stack = new PoseStack();
                    stack.last().set(pose);
                    ShaderMaskRenderQueue.render(pass, false,
                        builder -> model.renderToBuffer(stack, builder, light, overlay, 0xFFFFFFFF));
                });
            }
        }
    }

}
