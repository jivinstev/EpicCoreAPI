package net.eca.client.render;

import net.eca.util.EcaLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2f;

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
        graphics.pose().translate(renderX, (float) y);
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
        if (texture != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0f, 0.0f, drawWidth, drawHeight,
                    fullWidth, fullHeight, ARGB.white(alpha));
        }
        if (renderType != null) {
            submitShaderLayer(graphics, texture, renderType, x, y, drawWidth, drawHeight, fullWidth, fullHeight, alpha);
        }
    }

    /*
     * 着色器层：1.21 在提取阶段立即用 GL 状态绘制，26.x 的 GUI 只记录状态，所以交给画中画渲染器离屏合成
     * （见 BossBarShaderPipRenderer）。同时有贴图时，贴图先按普通方式画出，着色器只叠在其非透明像素上。
     */
    private static void submitShaderLayer(GuiGraphicsExtractor graphics, Identifier texture, RenderType renderType,
                                          int x, int y, int drawWidth, int drawHeight, int fullWidth, int fullHeight,
                                          float alpha) {
        Vector2f topLeft = graphics.pose().transformPosition(x, y, new Vector2f());
        Vector2f bottomRight = graphics.pose().transformPosition(x + drawWidth, y + drawHeight, new Vector2f());
        int x0 = (int) Math.floor(topLeft.x);
        int y0 = (int) Math.floor(topLeft.y);
        int x1 = (int) Math.ceil(bottomRight.x);
        int y1 = (int) Math.ceil(bottomRight.y);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        float u1 = fullWidth <= 0 ? 0.0f : (float) drawWidth / (float) fullWidth;
        float v1 = texture == null || fullHeight <= 0 ? 1.0f : (float) drawHeight / (float) fullHeight;
        graphics.submitPictureInPictureRenderState(new BossBarShaderPipState(x0, y0, x1, y1,
                topLeft.x, topLeft.y, bottomRight.x, bottomRight.y,
                texture, renderType, u1, v1, alpha, graphics.peekScissorStack()));
    }
}
