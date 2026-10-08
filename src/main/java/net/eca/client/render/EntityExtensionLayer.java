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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class EntityExtensionLayer<T extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState, M extends net.minecraft.client.model.EntityModel<? super T>>
    extends RenderLayer<T, M> {

    public EntityExtensionLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, T entity,
                       float yRot, float xRot) {

        EntityExtension extension = EntityExtensionManager.getExtension(entity.entityType);
        if (extension == null) {
            return;
        }

        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!(minecraft.level.getEntity(EcaRenderStateEntities.getId(entity)) instanceof LivingEntity livingEntity)) {
            return;
        }

        if (BlenderEntityBindings.replacesBody(BlenderEntityBindings.resolve(livingEntity))) {
            return;
        }

        EntityLayerExtension layerExtension = EntityExtensionSafeAccess.entityLayerExtension(extension, livingEntity);
        if (layerExtension == null || !layerExtension.enabled() || !layerExtension.shouldRender(livingEntity)) {
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
            ? LivingEntityRenderer.getOverlayCoords(entity, 0.0f)
            : OverlayTexture.NO_OVERLAY;
        float alpha = layerExtension.getAlpha();

        boolean hasTexture = texture != null;
        boolean oculus = EcaShaderInstance.isOculusShadersActive();

        if (hasTexture) {
            RenderType texturedLayer = RenderTypes.entityTranslucent(texture);
            if (oculus) {
                BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(texturedLayer.primitiveTopology(), texturedLayer.format());
                this.getParentModel().renderToBuffer(
                    poseStack, builder, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255)
                );
                ShaderMaskRenderQueue.enqueue(ShaderMaskPass.unmasked(texturedLayer, 1.0f),
                    builder, builder.buildOrThrow());
            } else {
                submitNodeCollector.submitModel(
                    this.getParentModel(), entity, poseStack, texturedLayer, light, overlay,
                    ARGB.color((int) (alpha * 255.0f), 255, 255, 255), null, entity.outlineColor, null
                );
            }
        }

        for (ShaderMaskPass pass : shaderPasses) {
            if (pass == null || pass.alpha() <= 0.0f) continue;
            BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(pass.renderType().primitiveTopology(), pass.renderType().format());
            this.getParentModel().renderToBuffer(
                poseStack, builder, light, overlay, 0xFFFFFFFF
            );
            if (oculus) {
                ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
            } else {
                ShaderMaskRenderQueue.drawNow(pass, builder, builder.buildOrThrow());
            }
        }
    }

}
