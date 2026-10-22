package net.eca.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.eca.mv.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.EcaLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * ECA 自定义 Boss 血条绘制器 —— 实体扩展与袭击系统共用。
 *
 * 绘制分两层：外框满宽渲染作为底层，填充按 progress 横向裁剪覆盖其上。
 * 同时提供贴图与 RenderType 时，着色器通过 alpha 通道遮罩叠加在贴图的非透明像素上。
 */
public final class EcaBossBarRenderer {

    // 原版血条尺寸，未提供任何尺寸信息时作为回退
    public static final int VANILLA_BAR_WIDTH = 182;
    public static final int VANILLA_BAR_HEIGHT = 5;

    private EcaBossBarRenderer() {}

    /*
     * 一条血条的完整外观参数。
     *
     * 尺寸为 0 时按贴图实际尺寸解析；仅提供 RenderType 而不提供贴图时必须显式给出尺寸，
     * 否则无法确定绘制范围。
     */
    public static final class BarAppearance {
        public Identifier frameTexture;
        public Identifier fillTexture;
        public RenderType frameRenderType;
        public RenderType fillRenderType;
        public int frameWidth;
        public int frameHeight;
        public int fillWidth;
        public int fillHeight;
        public int frameOffsetX;
        public int frameOffsetY;
        public int fillOffsetX;
        public int fillOffsetY;
        public float frameAlpha = 1.0f;
        public float fillAlpha = 1.0f;
        public Component valueText;

        // 是否未设置任何可绘制内容
        public boolean isEmpty() {
            return frameTexture == null && fillTexture == null
                    && frameRenderType == null && fillRenderType == null;
        }
    }

    // 绘制一条自定义血条
    /**
     * Draw a custom boss bar, resolving layout from the supplied appearance.
     * <p>
     * The bar is centered horizontally and scaled down when it would exceed the screen width.
     *
     * @param graphics   the GUI graphics context
     * @param y          the vertical position handed over by the vanilla bar layout
     * @param progress   fill ratio in range [0, 1]
     * @param appearance the resolved appearance
     * @param debugName  identifier used in warnings about missing sizes
     * @return true if the bar was drawn; false when required sizes are missing and the caller
     *         should fall back to the vanilla bar
     */
    public static boolean draw(GuiGraphicsExtractor graphics, int y, float progress,
                               BarAppearance appearance, String debugName) {
        if (appearance == null || appearance.isEmpty()) {
            return false;
        }

        int barWidth = VANILLA_BAR_WIDTH;
        int barHeight = VANILLA_BAR_HEIGHT;
        int fillTextureWidth;
        int fillTextureHeight;

        if (appearance.frameTexture != null) {
            TextureSizeCache.Size frameSize = TextureSizeCache.get(appearance.frameTexture);
            barWidth = frameSize.width();
            barHeight = frameSize.height();
        } else if (appearance.frameRenderType != null) {
            barWidth = appearance.frameWidth;
            barHeight = appearance.frameHeight;
        }

        if (appearance.fillTexture != null) {
            TextureSizeCache.Size fillSize = TextureSizeCache.get(appearance.fillTexture);
            fillTextureWidth = fillSize.width();
            fillTextureHeight = fillSize.height();
        } else if (appearance.fillRenderType != null) {
            fillTextureWidth = appearance.fillWidth;
            fillTextureHeight = appearance.fillHeight;
        } else {
            fillTextureWidth = barWidth;
            fillTextureHeight = barHeight;
        }

        if (appearance.frameRenderType != null && (barWidth <= 0 || barHeight <= 0)) {
            EcaLogger.warn("Custom boss bar frame size must be set for {}", debugName);
            return false;
        }
        if (appearance.fillRenderType != null && (fillTextureWidth <= 0 || fillTextureHeight <= 0)) {
            EcaLogger.warn("Custom boss bar fill size must be set for {}", debugName);
            return false;
        }

        float clamped = progress < 0.0f ? 0.0f : (progress > 1.0f ? 1.0f : progress);
        int fillWidth = (int) (clamped * (float) fillTextureWidth);

        int layoutWidth = barWidth;
        if (appearance.frameTexture == null && appearance.frameRenderType == null && fillTextureWidth > 0) {
            layoutWidth = fillTextureWidth;
        }

        float scale = 1.0f;
        int guiWidth = graphics.guiWidth();
        if (layoutWidth > 0) {
            float availableWidth = Math.max(1.0f, (float) guiWidth - 20.0f);
            scale = Math.min(1.0f, availableWidth / (float) layoutWidth);
        }

        float scaledWidth = layoutWidth * scale;
        float renderX = (guiWidth - scaledWidth) * 0.5f;

        graphics.pose().pushMatrix();
        graphics.pose().translate(renderX, y);
        graphics.pose().scale(scale, scale);

        int baseFillOffsetX = Math.max(0, (barWidth - fillTextureWidth) / 2);
        int baseFillOffsetY = Math.max(0, (barHeight - fillTextureHeight) / 2);
        int fillDrawX = baseFillOffsetX + appearance.fillOffsetX;
        int fillDrawY = baseFillOffsetY + appearance.fillOffsetY;

        // 外框：满宽渲染（先渲染作为底层）
        renderLayer(graphics, appearance.frameTexture, appearance.frameRenderType,
                appearance.frameOffsetX, appearance.frameOffsetY,
                barWidth, barHeight, barWidth, barHeight, appearance.frameAlpha);

        // 填充：按 progress 裁剪渲染（后渲染覆盖在外框上方）
        if (fillWidth > 0) {
            renderLayer(graphics, appearance.fillTexture, appearance.fillRenderType,
                    fillDrawX, fillDrawY,
                    fillWidth, fillTextureHeight, fillTextureWidth, fillTextureHeight, appearance.fillAlpha);
        }

        if (appearance.valueText != null) {
            flushBuffers();
            int textY = (barHeight - Minecraft.getInstance().font.lineHeight) / 2;
            graphics.centeredText(Minecraft.getInstance().font, appearance.valueText,
                    layoutWidth / 2, textY, 0xFFFFFFFF);
        }

        graphics.pose().popMatrix();
        return true;
    }

    public static Component formatValueText(Number currentValue, Number maxValue) {
        if (currentValue == null || maxValue == null) {
            return null;
        }

        double current = currentValue.doubleValue();
        double maximum = maxValue.doubleValue();
        if (!Double.isFinite(current) || !Double.isFinite(maximum)) {
            return null;
        }

        return Component.literal(formatNumber(current) + "/" + formatNumber(maximum));
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static void renderLayer(GuiGraphicsExtractor graphics, Identifier texture, RenderType renderType,
                                    int x, int y, int drawWidth, int drawHeight, int fullWidth, int fullHeight,
                                    float alpha) {
        if (texture == null && renderType == null) {
            return;
        }
        if (renderType == null) {
            // a deferred blit; 1.21.1's shader colour alpha is its vertex colour
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, drawWidth, drawHeight,
                    fullWidth, fullHeight, net.minecraft.util.ARGB.white(alpha));
            return;
        }
        // 1.21.6+: GuiGraphics only records; the shader layers are drawn into a picture-in-picture texture of this
        // layer's rectangle when GuiRenderer prepares the frame, then blitted at this place in the GUI order with the
        // bar's pose (translate/scale) -- see ShaderPreviewRenderer.submit. The painter draws in the layer's own
        // coordinates (identity matrix); the PIP's projection and model-view map them onto its texture.
        net.eca.client.render.shader_generator.ShaderPreviewRenderer.submit(graphics, x, y, x + drawWidth, y + drawHeight,
                (x0, y0, x1, y1) -> {
            EcaShaderInstance.setOpacity(alpha);
            net.eca.mv.LegacyGl.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            try {
                Matrix4f matrix = new Matrix4f();
                if (texture != null) {
                    drawTextureWithShaderMask(matrix, texture, renderType,
                            x, y, drawWidth, drawHeight, fullWidth, fullHeight);
                } else {
                    drawRenderType(matrix, renderType, x, y, drawWidth, drawHeight, fullWidth);
                }
            } finally {
                net.eca.mv.LegacyGl.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                EcaShaderInstance.clearOpacity();
            }
        });
    }

    private static void drawTextureWithShaderMask(Matrix4f matrix, Identifier texture, RenderType renderType,
                                                  int x, int y, int drawWidth, int drawHeight,
                                                  int fullWidth, int fullHeight) {
        flushBuffers();

        // 清除渲染区域的 alpha 通道为 0
        net.eca.mv.LegacyGl.colorMask(false, false, false, true);
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.ZERO,
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.ZERO
        );
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_COLOR);
        BufferBuilder clearBuilder = Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        clearBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x, y, 0).setColor(0, 0, 0, 255);
        net.eca.mv.LegacyGl.drawWithShader(clearBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.colorMask(true, true, true, true);

        // 渲染贴图，alpha 通道直接写入帧缓冲区
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.BlendFactor.SRC_ALPHA, com.mojang.blaze3d.platform.BlendFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.BlendFactor.ONE, com.mojang.blaze3d.platform.BlendFactor.ZERO
        );
        net.eca.mv.LegacyGl.setShaderTexture(0, texture);
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_TEX);
        float texU1 = fullWidth <= 0 ? 0.0f : (float) drawWidth / (float) fullWidth;
        float texV1 = fullHeight <= 0 ? 0.0f : (float) drawHeight / (float) fullHeight;
        BufferBuilder texBuilder = Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
        texBuilder.addVertex(matrix, x, y + drawHeight, 0).setUv(0.0f, texV1);
        texBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setUv(texU1, texV1);
        texBuilder.addVertex(matrix, x + drawWidth, y, 0).setUv(texU1, 0.0f);
        texBuilder.addVertex(matrix, x, y, 0).setUv(0.0f, 0.0f);
        net.eca.mv.LegacyGl.drawWithShader(texBuilder.buildOrThrow());

        // 将 alpha 缩放到 0.5，使着色器半透明叠加在贴图上
        net.eca.mv.LegacyGl.colorMask(false, false, false, true);
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.ONE,
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.SRC_ALPHA
        );
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_COLOR);
        BufferBuilder scaleBuilder = Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        scaleBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x, y, 0).setColor(0, 0, 0, 127);
        net.eca.mv.LegacyGl.drawWithShader(scaleBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.colorMask(true, true, true, true);

        // 渲染着色器，使用 DST_ALPHA 混合（只在贴图非透明区域显示）
        // 1.21.11: RenderType has no setupRenderState(); LegacyGl runs LegacyRenderType's (shards, key, shader, target)
        net.eca.mv.LegacyGl.setupRenderState(renderType);
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.BlendFactor.DST_ALPHA, com.mojang.blaze3d.platform.BlendFactor.ONE_MINUS_DST_ALPHA,
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.ONE
        );
        float shaderU1 = fullWidth <= 0 ? 0.0f : (float) drawWidth / (float) fullWidth;
        int light = 0xF000F0; // LightTexture.FULL_BRIGHT
        BufferBuilder shaderBuilder = Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
        shaderBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(shaderU1, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(shaderU1, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        net.eca.mv.LegacyGl.drawWithShader(shaderBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.clearRenderState(renderType);

        // 1.21.8: the steps above ran on a cleared (transparent) picture-in-picture texture, not on the screen, so its
        // colour is the premultiplied bar but its alpha is the 0.5 mask d. On 1.21.1 the screen under the bar kept
        // (1 - a)(1 - d) of itself (a: the texture's alpha); write that coverage, a + d - a*d, as the alpha the
        // premultiplied blit composites with: the texture again, alpha only, blended ONE_MINUS_DST_ALPHA / ONE.
        net.eca.mv.LegacyGl.colorMask(false, false, false, true);
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.BlendFactor.ZERO, com.mojang.blaze3d.platform.BlendFactor.ONE,
                com.mojang.blaze3d.platform.BlendFactor.ONE_MINUS_DST_ALPHA, com.mojang.blaze3d.platform.BlendFactor.ONE
        );
        net.eca.mv.LegacyGl.setShaderTexture(0, texture);
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_TEX);
        BufferBuilder coverageBuilder = Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
        coverageBuilder.addVertex(matrix, x, y + drawHeight, 0).setUv(0.0f, texV1);
        coverageBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setUv(texU1, texV1);
        coverageBuilder.addVertex(matrix, x + drawWidth, y, 0).setUv(texU1, 0.0f);
        coverageBuilder.addVertex(matrix, x, y, 0).setUv(0.0f, 0.0f);
        net.eca.mv.LegacyGl.drawWithShader(coverageBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.colorMask(true, true, true, true);

        net.eca.mv.LegacyGl.defaultBlendFunc();
    }

    private static void flushBuffers() {
        net.eca.mv.MultiBufferSource.main().endBatch();
    }

    private static void drawRenderType(Matrix4f matrix, RenderType renderType,
                                       int x, int y, int width, int height, int fullWidth) {
        float u1 = fullWidth <= 0 ? 0.0f : (float) width / (float) fullWidth;
        VertexConsumer consumer = net.eca.mv.MultiBufferSource.main().getBuffer(renderType);
        int light = 0xF000F0; // LightTexture.FULL_BRIGHT
        consumer.addVertex(matrix, x, y + height, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x + width, y + height, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(u1, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x + width, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(u1, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        flushBuffers();
    }
}
