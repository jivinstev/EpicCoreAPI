// Fork multi-version layer: 1.21.2 version of the authors' file (render state instead of entity).
// 26.2 (#33): 26.2 submits the level's entities before the frame graph (and its clear pass) runs, so a shader pass drawn at
// once from a level submit was erased. With the level (net.eca.mv.SubmitPhase.levelSubmit) the pass now goes to the
// after-level queue, as the Oculus path already did, drawn through LegacyGl with the pass shader's own uniform blocks; the
// textured layer stays in the shared buffer source, which is drawn after the translucent features. Outside the level (a
// GUI entity) it still draws at once.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.minecraft.client.Minecraft;
import net.eca.mv.MultiBufferSource;
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
        MultiBufferSource bufferSource = net.eca.mv.MultiBufferSource.main();
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
                VertexConsumer texConsumer = bufferSource.getBuffer(texturedLayer);
                this.getParentModel().renderToBuffer(
                    poseStack, texConsumer, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255)
                );
            }
        }

        for (ShaderMaskPass pass : shaderPasses) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(com.mojang.blaze3d.PrimitiveTopology.QUADS, pass.renderType().format());
            this.getParentModel().renderToBuffer(
                poseStack, builder, light, overlay, 0xFFFFFFFF
            );
            if (oculus || net.eca.mv.SubmitPhase.levelSubmit) {
                ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
            } else {
                if (bufferSource instanceof MultiBufferSource.BufferSource source) {
                    source.endBatch();
                }
                ShaderMaskRenderQueue.drawNow(pass, builder, builder.buildOrThrow());
            }
        }
    }

}
