// Fork multi-version layer: 1.21.2 version of the authors' file (render state instead of entity).
// 26.2 (#33): 26.2 submits the level's entities before the frame graph (and its clear pass) runs, so a shader pass drawn at
// once from a level submit was erased. With the level (net.eca.mv.SubmitPhase.levelSubmit) the pass now goes to the
// level's queue, as the Oculus path already did, drawn through LegacyGl with the pass shader's own uniform blocks inside the
// level's main pass at AfterTranslucentFeatures (#37; ShaderMaskRenderQueue.InLevelPass); the
// textured layer is an order-1 model submit (ECA-006 below). Outside the level (a GUI entity) the pass is drawn after the
// body (ECA-006 below).
// 1.21.9+ (ECA-006, carried from 26.1.2): the non-Oculus textured pass is an order-1 model submit drawn after the body; outside the
// level (26.2: levelSubmit false) a GUI entity's shader pass is drawn after the body through GuiTexturePasses, not at once.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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

        Identifier texture = layerExtension.getTexture();
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
            RenderType texturedLayer = RenderTypes.entityTranslucent(texture);
            if (oculus) {
                BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(com.mojang.blaze3d.PrimitiveTopology.QUADS, texturedLayer.format());
                this.getParentModel().renderToBuffer(
                    poseStack, builder, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255)
                );
                ShaderMaskRenderQueue.enqueue(ShaderMaskPass.unmasked(texturedLayer, 1.0f),
                    builder, builder.buildOrThrow());
            } else {
                // order 1: drawn by the feature dispatch after every order-0 model (the body), like vanilla's own layers
                collector.order(1).submitModel(this.getParentModel(), state, poseStack, texturedLayer, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255), null, state.outlineColor, null);
            }
        }

        for (ShaderMaskPass pass : shaderPasses) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            if (oculus || net.eca.mv.SubmitPhase.levelSubmit) {
                BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(com.mojang.blaze3d.PrimitiveTopology.QUADS, pass.renderType().format());
                this.getParentModel().renderToBuffer(
                    poseStack, builder, light, overlay, 0xFFFFFFFF
                );
                ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
            } else {
                // 1.21.9+ (ECA-006): the body is only submitted here and drawn later by the feature dispatch, so drawing the
                // pass now (as 1.21.1's endBatch() + drawNow did) put it under the body. 26.1.2 drew it from an order-1
                // custom-geometry submit; 26.2 runs those callbacks while renderAllFeatures PREPARES the storage (before
                // the body is drawn) and puts the pass's LegacyRenderType on vanilla's feature path, which draws nothing
                // (see ItemRendererMixin). A GUI entity is submitted inside a picture-in-picture renderer's prepare, so the
                // pass is collected and drawn right after that renderer's renderAllFeatures, over the body, into its
                // texture (GuiTexturePasses, PictureInPictureFeaturesMixin); anything else still draws at once.
                BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(com.mojang.blaze3d.PrimitiveTopology.QUADS, pass.renderType().format());
                this.getParentModel().renderToBuffer(
                    poseStack, builder, light, overlay, 0xFFFFFFFF
                );
                if (GuiTexturePasses.collecting()) {
                    GuiTexturePasses.add(pass, builder, builder.buildOrThrow(), MaskUvTransform.IDENTITY);
                } else {
                    net.eca.mv.MultiBufferSource.main().endBatch();
                    ShaderMaskRenderQueue.drawNow(pass, builder, builder.buildOrThrow());
                }
            }
        }
    }

}
