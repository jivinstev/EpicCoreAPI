package net.eca.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

public class GeoBlockExtensionLayer<T extends BlockEntity & GeoAnimatable, R extends BlockEntityRenderState & GeoRenderState> extends GeoRenderLayer<T, Void, R> {

    private final GeoBoneVisibilityController boneVisibility = new GeoBoneVisibilityController();
    private BlockExtension activeExtension;

    public GeoBlockExtensionLayer(GeoBlockRenderer<T, R> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                          VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        BlockExtension extension = BlockExtensionManager.getExtension(animatable.getBlockState().getBlock());
        activeExtension = extension != null && animatable.getLevel() != null
            && BlockExtensionSafeAccess.shouldRender(extension, animatable.getBlockState(),
                animatable.getLevel(), animatable.getBlockPos()) ? extension : null;
        if (activeExtension != null) {
            boneVisibility.begin(bakedModel, activeExtension.hiddenGeoBones());
        }
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        try {
            if (activeExtension == null) {
                return;
            }
            Identifier texture = getRenderer().getTextureLocation(animatable);
            List<ShaderMaskPass> passes = activeExtension.getGeoShaderPasses(texture);
            if (passes == null || passes.isEmpty()) {
                return;
            }
            boneVisibility.restrictOverlay(bakedModel, activeExtension.overlayGeoBones());
            int light = activeExtension.isGlow() ? 15728880 : packedLight;
            boolean queued = EcaShaderInstance.isOculusShadersActive();
            for (ShaderMaskPass pass : passes) {
                renderPass(poseStack, animatable, bakedModel, partialTick, light,
                    pass, queued);
            }
        } finally {
            boneVisibility.restore();
            activeExtension = null;
        }
    }

    private void renderPass(PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                            float partialTick, int light,
                            ShaderMaskPass pass, boolean queued) {
        if (pass == null || pass.alpha() <= 0.0f) return;
        RenderType renderType = pass.renderType();
        BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder(PrimitiveTopology.QUADS, renderType.format());
        getRenderer().reRender(bakedModel, poseStack, ignored -> builder, animatable, renderType, builder,
            partialTick, light, OverlayTexture.NO_OVERLAY, -1);
        if (queued) {
            ShaderMaskRenderQueue.enqueue(pass, builder, builder.buildOrThrow());
        } else {
            ShaderMaskRenderQueue.drawNow(pass, builder, builder.buildOrThrow());
        }
    }
}
