package net.eca.client.render.shader_generator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.List;

public final class ShaderPreviewRenderer {

    private static final ItemStack PREVIEW_ITEM = createPreviewItem();
    private static final ProjectionMatrixBuffer PREVIEW_PROJECTION = new ProjectionMatrixBuffer("eca_shader_preview");

    public static void render(
        GuiGraphicsExtractor graphics,
        ShaderPreviewSource source,
        ShaderPreviewTarget target,
        int left,
        int top,
        int right,
        int bottom,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        if (source == null || right <= left || bottom <= top) {
            return;
        }

        // 26.2: there is no immediate buffer source left to flush before the preview draws
        graphics.enableScissor(left, top, right, bottom);
        try {
            switch (target) {
                case PLANE -> renderBlockSurface(
                    source.skybox(),
                    left + 16,
                    top + 16,
                    right - 16,
                    bottom - 16
                );
                case SKYBOX -> renderBlockSurface(
                    source.skybox(), left, top, right, bottom
                );
                case BOSS_BAR -> {
                    int width = Math.max(80, Math.min(right - left - 40, 360));
                    int height = Math.max(16, Math.min(42, (bottom - top) / 5));
                    int centerX = (left + right) / 2;
                    int centerY = (top + bottom) / 2;
                    renderBlockSurface(
                        source.bossBar(),
                        centerX - width / 2,
                        centerY - height / 2,
                        centerX + width / 2,
                        centerY + height / 2
                    );
                }
                case ITEM -> renderItem(graphics, source, left, top, right, bottom);
                case ENTITY -> renderEntity(
                    graphics,
                    source,
                    left,
                    top,
                    right,
                    bottom,
                    mouseX,
                    mouseY,
                    partialTick
                );
            }
        } finally {
            graphics.disableScissor();
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        }
    }

    private static void renderBlockSurface(
        RenderType renderType,
        int left,
        int top,
        int right,
        int bottom
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        // 26.2: Tesselator is gone; the builder writes into its own buffer, freed once the mesh is drawn
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 8)) {
            modelView.identity();

            float centerX = ((left + right) / (float) minecraft.getWindow().getGuiScaledWidth()) - 1.0F;
            float centerY = 1.0F - ((top + bottom) / (float) minecraft.getWindow().getGuiScaledHeight());
            float scaleX = (right - left) / (float) minecraft.getWindow().getGuiScaledWidth();
            float scaleY = (bottom - top) / (float) minecraft.getWindow().getGuiScaledHeight();
            Matrix4f projection = new Matrix4f()
                .translation(centerX, centerY, 0.0F)
                .scale(scaleX, scaleY, 1.0F);
            RenderSystem.setProjectionMatrix(PREVIEW_PROJECTION.getBuffer(projection), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            BufferBuilder builder = new BufferBuilder(buffer, renderType.primitiveTopology(), DefaultVertexFormat.BLOCK);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            draw(renderType, builder.buildOrThrow());
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            modelView.popMatrix();
        }
    }

    /** 1.21.1's {@code RenderType.draw(MeshData)}: uploads the mesh and draws it with the type's pipeline and textures. */
    private static void draw(RenderType renderType, MeshData mesh) {
        try (MeshData m = mesh) {
            MeshData.DrawState state = m.drawState();
            GpuBuffer vertices = RenderSystem.getDevice()
                .createBuffer(() -> "ECA shader preview vertices", GpuBuffer.USAGE_VERTEX, m.vertexBuffer());
            GpuBuffer ownIndices = null;
            try {
                GpuBuffer indices;
                IndexType indexType;
                if (m.indexBuffer() == null) {
                    RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(state.primitiveTopology());
                    indices = sequential.getBuffer(state.indexCount());
                    indexType = sequential.type();
                } else {
                    ownIndices = RenderSystem.getDevice()
                        .createBuffer(() -> "ECA shader preview indices", GpuBuffer.USAGE_INDEX, m.indexBuffer());
                    indices = ownIndices;
                    indexType = state.indexType();
                }
                renderType.prepare().drawFromBuffer(vertices, indices, indexType, 0, 0, state.indexCount());
            } finally {
                vertices.close();
                if (ownIndices != null) {
                    ownIndices.close();
                }
            }
        }
    }

    private static void blockVertex(
        BufferBuilder builder,
        float x,
        float y,
        float u,
        float v
    ) {
        builder.addVertex((float) (x), (float) (y), (float) (0.5F))
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setLight(0xF000F0)
            .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void renderItem(
        GuiGraphicsExtractor graphics,
        ShaderPreviewSource source,
        int left,
        int top,
        int right,
        int bottom
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        List<BakedQuad> quads = net.eca.mv.ItemModels.quads(PREVIEW_ITEM, level, minecraft.player, 0);
        // 1.21.10: an item is submitted as render state and drawn by a feature dispatcher; 26.2 has no buffer source to
        // force, so the submits themselves carry the preview's render type, as the forced MultiBufferSource did
        SubmitNodeStorage submits = new ForcedSubmits(source.item());
        FeatureRenderDispatcher features = featureDispatcher();
        ItemStackRenderState itemState = new ItemStackRenderState();

        int size = Math.max(40, Math.min(right - left, bottom - top) * 2 / 5);
        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            float[] bounds = computeUvBounds(quads);
            EcaShaderInstance.setLocalUvBounds(
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3]
            );
            pose.translate((left + right) / 2.0, (top + bottom) / 2.0, 180.0);
            pose.scale(size, -size, size);
            pose.mulPose(new Quaternionf().rotateY(0.65F).rotateX(-0.35F));
            minecraft.getItemModelResolver().updateForTopItem(itemState, PREVIEW_ITEM, ItemDisplayContext.GUI, level, minecraft.player, 0);
            itemState.submit(pose, submits, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            features.renderAllFeatures(submits);
        } finally {
            EcaShaderInstance.clearLocalUvBounds();
            pose.popPose();
        }
    }

    private static void renderEntity(
        GuiGraphicsExtractor graphics,
        ShaderPreviewSource source,
        int left,
        int top,
        int right,
        int bottom,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RenderType entityType = source.entity(minecraft.player.getSkin().body().texturePath());
        SubmitNodeStorage submits = new ForcedSubmits(entityType);
        FeatureRenderDispatcher features = featureDispatcher();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        int size = Math.max(24, Math.min(right - left, bottom - top) / 3);
        float horizontal = (float) Math.atan(((left + right) * 0.5F - mouseX) / 80.0F);
        float vertical = (float) Math.atan(((top + bottom) * 0.5F - mouseY) / 80.0F);
        Quaternionf orientation = new Quaternionf()
            .rotateZ((float) Math.PI)
            .rotateX(vertical * 0.35F)
            .rotateY(horizontal * 0.35F);

        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            pose.translate((left + right) / 2.0, bottom - 28.0, 220.0);
            pose.mulPose(new Matrix4f().scaling(size, size, -size));
            pose.mulPose(orientation);
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            // 1.21.10: the camera orientation, light and shadow are per-submit render state, not dispatcher switches
            CameraRenderState camera = new CameraRenderState();
            camera.orientation = new Quaternionf().rotateX(vertical * 0.35F);
            EntityRenderState state = dispatcher.extractEntity(minecraft.player, partialTick);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            net.eca.mv.Mv.run(() -> {
                dispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submits);
                features.renderAllFeatures(submits);
            });
        } finally {
            pose.popPose();
        }
    }

    // 26.2: a feature dispatcher draws through the game renderer's staged vertex buffer, so the game renderer's own is used
    private static FeatureRenderDispatcher featureDispatcher() {
        return Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
    }

    /** Hands every order the forcing collection below. */
    private static final class ForcedSubmits extends SubmitNodeStorage {
        private final RenderType forced;

        ForcedSubmits(RenderType forced) {
            this.forced = forced;
        }

        @Override
        public SubmitNodeCollection order(int order) {
            return this.getSubmitsPerOrder().computeIfAbsent(order, ignored -> new ForcedCollection(this.forced));
        }
    }

    /** Submits every model, block and item with the forced render type, as the forced buffer source handed it out. */
    private static final class ForcedCollection extends SubmitNodeCollection {
        private final RenderType forced;

        ForcedCollection(RenderType forced) {
            this.forced = forced;
        }

        @Override
        public <S> void submitModel(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType ignored,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
        ) {
            super.submitModel(model, state, poseStack, this.forced, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay);
        }

        @Override
        public void submitBlockModel(
            PoseStack poseStack,
            RenderType ignored,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor
        ) {
            super.submitBlockModel(poseStack, this.forced, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType ignored, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            super.submitCustomGeometry(poseStack, this.forced, renderer);
        }

        // 26.2: an item submit names no render type (each quad carries its own), so its quads go out as custom geometry
        @Override
        public void submitItem(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType
        ) {
            super.submitCustomGeometry(poseStack, this.forced, (pose, buffer) -> {
                QuadInstance instance = new QuadInstance();
                instance.setLightCoords(lightCoords);
                instance.setOverlayCoords(overlayCoords);
                for (BakedQuad quad : quads) {
                    BakedQuad.MaterialInfo material = quad.materialInfo();
                    int layer = material.tintIndex();
                    instance.setColor(material.isTinted() && layer >= 0 && layer < tintLayers.length ? tintLayers[layer] : -1);
                    buffer.putBakedQuad(pose, quad, instance);
                }
            });
        }
    }

    private static ItemStack createPreviewItem() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    // 1.21.5: an item layer holds the model's quads directly (one list, every face), so the per-direction getQuads loop is gone
    private static float[] computeUvBounds(List<BakedQuad> quads) {
        float uMin = Float.POSITIVE_INFINITY;
        float vMin = Float.POSITIVE_INFINITY;
        float uMax = Float.NEGATIVE_INFINITY;
        float vMax = Float.NEGATIVE_INFINITY;

        if (quads != null) {
            float[] bounds = includeQuads(quads, uMin, vMin, uMax, vMax);
            uMin = bounds[0];
            vMin = bounds[1];
            uMax = bounds[2];
            vMax = bounds[3];
        }

        if (uMin > uMax || vMin > vMax) {
            return new float[]{0.0F, 0.0F, 1.0F, 1.0F};
        }
        return new float[]{
            uMin,
            vMin,
            1.0F / Math.max(uMax - uMin, 1.0E-6F),
            1.0F / Math.max(vMax - vMin, 1.0E-6F)
        };
    }

    private static float[] includeQuads(
        List<BakedQuad> quads,
        float uMin,
        float vMin,
        float uMax,
        float vMax
    ) {
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < 4; vertex++) {
                long packedUv = switch (vertex) {
                    case 0 -> quad.packedUV0();
                    case 1 -> quad.packedUV1();
                    case 2 -> quad.packedUV2();
                    default -> quad.packedUV3();
                };
                float u = net.minecraft.client.model.geom.builders.UVPair.unpackU(packedUv);
                float v = net.minecraft.client.model.geom.builders.UVPair.unpackV(packedUv);
                uMin = Math.min(uMin, u);
                vMin = Math.min(vMin, v);
                uMax = Math.max(uMax, u);
                vMax = Math.max(vMax, v);
            }
        }
        return new float[]{uMin, vMin, uMax, vMax};
    }

    private ShaderPreviewRenderer() {}
}
