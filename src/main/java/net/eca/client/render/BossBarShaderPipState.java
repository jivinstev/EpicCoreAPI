package net.eca.client.render;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/*
 * 26.x 的 GUI 只记录绘制状态，不能在提取阶段改混合/颜色掩码后立即绘制。
 * 自定义血条的着色器层因此作为画中画（PIP）状态提交：先在离屏纹理里完成原先靠 GL 状态做的合成，
 * 再按 GUI 顺序贴回屏幕。坐标为屏幕（GUI）坐标；left/top/right/bottom 保留小数部分，
 * x0..y1 是其整数外包矩形。
 *
 * 只有 renderType（maskTexture 为空）时着色器直接铺满矩形；同时给出 maskTexture 时，
 * 着色器只显示在该贴图非透明的像素上，权重为 0.5 * 贴图 alpha（与 1.21 中 DST_ALPHA 叠加一致）。
 */
@OnlyIn(Dist.CLIENT)
public record BossBarShaderPipState(
    int x0, int y0, int x1, int y1,
    float left, float top, float right, float bottom,
    @Nullable Identifier maskTexture,
    RenderType renderType,
    float u1, float v1,
    float alpha,
    @Nullable ScreenRectangle scissorArea
) implements PictureInPictureRenderState {

    @Override
    public float scale() {
        return 1.0f;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        return PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea);
    }
}
