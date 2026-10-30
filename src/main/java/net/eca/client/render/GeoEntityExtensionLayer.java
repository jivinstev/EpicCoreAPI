// Fork multi-version layer: 1.21.5 version of the authors' file (GeckoLib 5: a layer works on a GeoRenderState, not on the
// animatable; the entity/extension lookup moved to addRenderData, which runs while the state is compiled, and
// reRender takes the state). The drawing and the bone-visibility logic are the authors', unchanged.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.EntityLayerExtension;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.BiFunction;
import java.util.List;

@OnlyIn(Dist.CLIENT)
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
    public void addRenderData(T animatable, O relatedObject, R renderState) {
        renderState.addGeckolibData(EXTENSION, findExtension(entityResolver.apply(animatable, relatedObject)));
    }

    @Override
    public void preRender(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          int packedLight, int packedOverlay, int renderColor) {
        EntityLayerExtension extension = renderState.getGeckolibData(EXTENSION);
        if (extension != null) {
            boneVisibility.begin(bakedModel, extension);
        }
    }

    @Override
    public void render(R renderState, PoseStack poseStack, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       int packedLight, int packedOverlay, int renderColor) {

        EntityLayerExtension layerExtension = renderState.getGeckolibData(EXTENSION);
        try {
            if (layerExtension == null) return;
            renderOverlay(poseStack, renderState, bakedModel, bufferSource, packedLight,
                    packedOverlay, layerExtension);
        } finally {
            boneVisibility.restore();
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

    private void renderOverlay(PoseStack poseStack, R renderState, BakedGeoModel bakedModel,
                               MultiBufferSource bufferSource, int packedLight,
                               int packedOverlay, EntityLayerExtension layerExtension) {
        ResourceLocation texture = layerExtension.getTexture();
        List<ShaderMaskPass> shaderPasses = layerExtension.getShaderPasses();
        if (shaderPasses == null) shaderPasses = List.of();
        if (shaderPasses.isEmpty() && texture == null) return;
        boneVisibility.restrictOverlay(bakedModel, layerExtension.overlayGeoBones());

        int light = layerExtension.isGlow() ? 15728880 : packedLight;
        int overlay = layerExtension.isHurtOverlay() ? packedOverlay : OverlayTexture.NO_OVERLAY;
        float alpha = layerExtension.getAlpha();
        boolean oculus = EcaShaderInstance.isOculusShadersActive();

        renderTexturePass(poseStack, renderState, bakedModel, bufferSource, light, overlay,
            alpha, texture == null ? null : RenderType.entityTranslucent(texture), oculus);
        for (ShaderMaskPass pass : shaderPasses) {
            renderShaderPass(poseStack, renderState, bakedModel, bufferSource,
                light, overlay, pass, oculus);
        }
    }

    private void renderTexturePass(PoseStack poseStack, R renderState, BakedGeoModel bakedModel,
                                   MultiBufferSource bufferSource, int light, int overlay,
                                   float alpha, RenderType type, boolean oculus) {
        if (type == null) return;
        if (oculus) {
            ShaderMaskRenderQueue.render(ShaderMaskPass.unmasked(type, 1.0f), true,
                builder -> this.renderer.reRender(renderState, poseStack, bakedModel, rt -> builder,
                    type, builder, light, overlay,
                    ARGB.colorFromFloat(alpha, 1.0f, 1.0f, 1.0f)));
        } else {
            this.renderer.reRender(renderState, poseStack, bakedModel, bufferSource, type,
                    bufferSource.getBuffer(type), light, overlay, ARGB.colorFromFloat(alpha, 1.0f, 1.0f, 1.0f));
        }
    }

    private void renderShaderPass(PoseStack poseStack, R renderState, BakedGeoModel bakedModel,
                                  MultiBufferSource bufferSource, int light, int overlay,
                                  ShaderMaskPass pass, boolean oculus) {
        if (pass == null || pass.alpha() <= 0.0f) return;
        RenderType type = pass.renderType();
        ShaderMaskRenderQueue.render(pass, oculus, builder -> {
            this.renderer.reRender(renderState, poseStack, bakedModel, ignored -> builder, type, builder,
                light, overlay, -1);
            if (!oculus && bufferSource instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        });
    }
}
