package net.eca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.eca.util.shader_generator.ai.ShaderAiToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

final class ShaderPreviewCapture {

    static ShaderAiToolResult capture(int left, int top, int right, int bottom) {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage screenshot = net.eca.mv.Screenshots.take(minecraft.gameRenderer.mainRenderTarget());
        try {
            double scale = minecraft.getWindow().getGuiScale();
            int pixelLeft = clamp(
                (int) Math.round(left * scale), 0, screenshot.getWidth() - 1
            );
            int pixelTop = clamp(
                (int) Math.round(top * scale), 0, screenshot.getHeight() - 1
            );
            int pixelRight = clamp(
                (int) Math.round(right * scale), pixelLeft + 1, screenshot.getWidth()
            );
            int pixelBottom = clamp(
                (int) Math.round(bottom * scale), pixelTop + 1, screenshot.getHeight()
            );
            try (NativeImage crop = new NativeImage(
                pixelRight - pixelLeft,
                pixelBottom - pixelTop,
                false
            )) {
                for (int y = 0; y < crop.getHeight(); y++) {
                    for (int x = 0; x < crop.getWidth(); x++) {
                        crop.setPixel(x, y, net.minecraft.util.ARGB.fromABGR(net.minecraft.util.ARGB.toABGR(screenshot.getPixel(pixelLeft + x, pixelTop + y))
                        ));
                    }
                }
                String image = Base64.getEncoder().encodeToString(net.eca.mv.Mv.pngBytes(crop));
                return new ShaderAiToolResult(
                    "Captured the current ECA preview",
                    List.of(image)
                );
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to encode preview image", exception);
            }
        } finally {
            screenshot.close();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private ShaderPreviewCapture() {}
}
