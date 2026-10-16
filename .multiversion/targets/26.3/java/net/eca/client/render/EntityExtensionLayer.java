// Fork multi-version layer: 1.21.2 version of the authors' file (render state instead of entity).
// 26.3 (#33): 26.3 submits the level's entities (submitFeatures) before the frame graph and its clear pass run, so a shader
// pass drawn at once from a level submit was erased. With the level (net.eca.mv.SubmitPhase.levelSubmit) both the textured
// layer and the shader passes now take the after-level queue the Oculus path already used (drawn through LegacyGl after
// LevelRenderer.render returns, outside any render pass). The textured layer no longer waits in the shared
// MultiBufferSource during the level either: 26.3 posts AfterTranslucentFeatures inside the main render pass, where the
// shared helper's flush cannot open a pass of its own. Outside the level (a GUI entity) it still draws at once.
// 26.3 (#37): that queue is now drawn INTO the level's main pass at AfterTranslucentFeatures (ShaderMaskRenderQueue.InLevelPass),
// so glass, water, particles and weather draw over the layers again; the after-level flush is left for improved
// transparency (no AfterTranslucentFeatures is posted) and Oculus.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
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
        boolean deferred = oculus || net.eca.mv.SubmitPhase.levelSubmit;   // 26.3 (#33): the after-level queue

        if (hasTexture) {
            RenderType texturedLayer = RenderTypes.entityTranslucent(texture);
            if (deferred) {
                BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(PrimitiveTopology.QUADS, texturedLayer.format());
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
            BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(PrimitiveTopology.QUADS, pass.renderType().format());
            this.getParentModel().renderToBuffer(
                poseStack, builder, light, overlay, 0xFFFFFFFF
            );
            if (deferred) {
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
