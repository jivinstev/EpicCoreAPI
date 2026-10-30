// Fork multi-version layer: 1.21.5 version of the authors' file (GeckoLib 5: a layer works on a GeoRenderState, not on the
// animatable; the entity/extension lookup moved to addRenderData, which runs while the state is compiled, and
// reRender takes the state). The drawing and the bone-visibility logic are the authors', unchanged.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

public class GeoBlockExtensionLayer<T extends BlockEntity & GeoAnimatable> extends GeoRenderLayer<T, Void, GeoRenderState> {

    /** The extension that applies to this frame's block entity, parked on the render state by {@link #addRenderData}. */
    private static final DataTicket<BlockExtension> EXTENSION =
        DataTicket.create("eca_block_extension", BlockExtension.class);

    private final GeoBoneVisibilityController boneVisibility = new GeoBoneVisibilityController();

    public GeoBlockExtensionLayer(GeoBlockRenderer<T> renderer) {
        super(renderer);
    }

    @Override
    public void addRenderData(T animatable, Void relatedObject, GeoRenderState renderState) {
        BlockExtension extension = BlockExtensionManager.getExtension(animatable.getBlockState().getBlock());
        renderState.addGeckolibData(EXTENSION, extension != null && animatable.getLevel() != null
            && BlockExtensionSafeAccess.shouldRender(extension, animatable.getBlockState(),
                animatable.getLevel(), animatable.getBlockPos()) ? extension : null);
    }

    @Override
    public void preRender(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel bakedModel, RenderType renderType,
                          MultiBufferSource bufferSource, VertexConsumer buffer,
                          int packedLight, int packedOverlay, int renderColor) {
        BlockExtension activeExtension = renderState.getGeckolibData(EXTENSION);
        if (activeExtension != null) {
            boneVisibility.begin(bakedModel, activeExtension.hiddenGeoBones());
        }
    }

    @Override
    public void render(GeoRenderState renderState, PoseStack poseStack, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer,
                       int packedLight, int packedOverlay, int renderColor) {
        BlockExtension activeExtension = renderState.getGeckolibData(EXTENSION);
        try {
            if (activeExtension == null) {
                return;
            }
            ResourceLocation texture = getTextureResource(renderState);
            List<ShaderMaskPass> passes = activeExtension.getGeoShaderPasses(texture);
            if (passes == null || passes.isEmpty()) {
                return;
            }
            boneVisibility.restrictOverlay(bakedModel, activeExtension.overlayGeoBones());
            int light = activeExtension.isGlow() ? 15728880 : packedLight;
            boolean queued = EcaShaderInstance.isOculusShadersActive();
            for (ShaderMaskPass pass : passes) {
                renderPass(poseStack, renderState, bakedModel, bufferSource, light,
                    pass, queued);
            }
        } finally {
            boneVisibility.restore();
        }
    }

    private void renderPass(PoseStack poseStack, GeoRenderState renderState, BakedGeoModel bakedModel,
                            MultiBufferSource bufferSource, int light,
                            ShaderMaskPass pass, boolean queued) {
        if (pass == null || pass.alpha() <= 0.0f) return;
        RenderType renderType = pass.renderType();
        ShaderMaskRenderQueue.render(pass, queued, builder -> {
            renderer.reRender(renderState, poseStack, bakedModel, ignored -> builder, renderType, builder,
                light, OverlayTexture.NO_OVERLAY, -1);
            if (!queued && bufferSource instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        });
    }
}
