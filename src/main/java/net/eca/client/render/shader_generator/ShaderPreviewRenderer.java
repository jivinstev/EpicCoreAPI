package net.eca.client.render.shader_generator;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ShaderPreviewRenderer {

    private static final ItemStack PREVIEW_ITEM = createPreviewItem();

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
        }
    }

    private static void renderBlockSurface(
        RenderType renderType,
        int left,
        int top,
        int right,
        int bottom
    ) {
        try (ByteBufferBuilder byteBuilder = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 8)) {
            BufferBuilder builder = new BufferBuilder(byteBuilder, renderType.primitiveTopology(), DefaultVertexFormat.BLOCK);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
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
        // ItemRenderer API was removed in 26.2, item rendering now uses recorded render state
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
        // Immediate-mode entity rendering (PoseStack, MultiBufferSource, dispatcher.render) is gone in 26.2;
        // entity previews must be submitted as recorded render state. Not yet ported.
    }

    private static ItemStack createPreviewItem() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    private static float[] computeUvBounds(List<BakedQuad> modelQuads) {
        float uMin = Float.POSITIVE_INFINITY;
        float vMin = Float.POSITIVE_INFINITY;
        float uMax = Float.NEGATIVE_INFINITY;
        float vMax = Float.NEGATIVE_INFINITY;

        float[] bounds = includeQuads(
            modelQuads,
            uMin,
            vMin,
            uMax,
            vMax
        );
        uMin = bounds[0];
        vMin = bounds[1];
        uMax = bounds[2];
        vMax = bounds[3];

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
                long packed = quad.packedUV(vertex);
                float u = Float.intBitsToFloat((int) (packed & 0xFFFFFFFFL));
                float v = Float.intBitsToFloat((int) (packed >>> 32));
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
