package net.eca.client.render.shader_generator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.LinkedHashMap;
import java.util.List;

public final class ShaderPreviewRenderer {

    private static final ItemStack PREVIEW_ITEM = createPreviewItem();
    private static final PerspectiveProjectionMatrixBuffer PREVIEW_PROJECTION = new PerspectiveProjectionMatrixBuffer("eca_shader_preview");

    public static void render(
        GuiGraphics graphics,
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

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
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
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
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
        try {
            modelView.identity();

            float centerX = ((left + right) / (float) minecraft.getWindow().getGuiScaledWidth()) - 1.0F;
            float centerY = 1.0F - ((top + bottom) / (float) minecraft.getWindow().getGuiScaledHeight());
            float scaleX = (right - left) / (float) minecraft.getWindow().getGuiScaledWidth();
            float scaleY = (bottom - top) / (float) minecraft.getWindow().getGuiScaledHeight();
            Matrix4f projection = new Matrix4f()
                .translation(centerX, centerY, 0.0F)
                .scale(scaleX, scaleY, 1.0F);
            RenderSystem.setProjectionMatrix(PREVIEW_PROJECTION.getBuffer(projection), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            BufferBuilder builder = Tesselator.getInstance().begin(renderType.mode(), DefaultVertexFormat.BLOCK);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            renderType.draw(builder.buildOrThrow());
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            modelView.popMatrix();
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
        GuiGraphics graphics,
        ShaderPreviewSource source,
        int left,
        int top,
        int right,
        int bottom
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        List<BakedQuad> quads = net.eca.mv.ItemModels.quads(PREVIEW_ITEM, level, minecraft.player, 0);
        // 1.21.10: an item is submitted as render state and drawn by a feature dispatcher; this one draws into a buffer
        // source that forces the preview's render type, as the forced MultiBufferSource did
        MultiBufferSource.BufferSource delegate = new ForcedBufferSource(new ByteBufferBuilder(4096), source.item());
        FeatureRenderDispatcher features = featureDispatcher(delegate);
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
            itemState.submit(pose, features.getSubmitNodeStorage(), 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            features.renderAllFeatures();
            delegate.endBatch();
        } finally {
            EcaShaderInstance.clearLocalUvBounds();
            pose.popPose();
        }
    }

    private static void renderEntity(
        GuiGraphics graphics,
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
        MultiBufferSource.BufferSource delegate = new ForcedBufferSource(new ByteBufferBuilder(8192), entityType);
        FeatureRenderDispatcher features = featureDispatcher(delegate);
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
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            // 1.21.10: the camera orientation, light and shadow are per-submit render state, not dispatcher switches
            CameraRenderState camera = new CameraRenderState();
            camera.orientation = new Quaternionf().rotateX(vertical * 0.35F);
            EntityRenderState state = dispatcher.extractEntity(minecraft.player, partialTick);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            net.eca.mv.Mv.run(() -> {
                dispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.getSubmitNodeStorage());
                features.renderAllFeatures();
            });
            delegate.endBatch();
        } finally {
            pose.popPose();
        }
    }

    private static FeatureRenderDispatcher featureDispatcher(MultiBufferSource.BufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        return new FeatureRenderDispatcher(
            new SubmitNodeStorage(),
            minecraft.getBlockRenderer(),
            buffers,
            minecraft.getAtlasManager(),
            minecraft.renderBuffers().outlineBufferSource(),
            minecraft.renderBuffers().crumblingBufferSource(),
            minecraft.font
        );
    }

    /** Hands out the forced render type's buffer for every request. */
    private static final class ForcedBufferSource extends MultiBufferSource.BufferSource {
        private final RenderType forced;

        ForcedBufferSource(ByteBufferBuilder buffer, RenderType forced) {
            super(buffer, new LinkedHashMap<>());
            this.forced = forced;
        }

        @Override
        public VertexConsumer getBuffer(RenderType ignored) {
            return super.getBuffer(forced);
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
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
        int uvOffset = 4;
        for (BakedQuad quad : quads) {
            int[] vertices = quad.vertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * stride + uvOffset;
                if (base + 1 >= vertices.length) {
                    continue;
                }
                float u = Float.intBitsToFloat(vertices[base]);
                float v = Float.intBitsToFloat(vertices[base + 1]);
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
