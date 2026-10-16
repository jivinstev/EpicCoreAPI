package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Compatibility facade for the former entity-only delayed queue.
 *
 * @deprecated Use {@link ShaderMaskRenderQueue}; all extension domains now share that queue.
 */
@Deprecated
public final class EntityLayerRenderQueue {

    private EntityLayerRenderQueue() {
    }

    public static BufferBuilder acquireBuilder(VertexFormat.Mode mode, VertexFormat format) {
        return ShaderMaskRenderQueue.acquireBuilder(mode, format);
    }

    public static void enqueue(RenderType renderType, BufferBuilder builder,
                               MeshData renderedBuffer) {
        ShaderMaskRenderQueue.enqueue(ShaderMaskPass.unmasked(renderType, 1.0f), builder, renderedBuffer,
            MaskUvTransform.IDENTITY);
    }

    public static void enqueue(RenderType renderType, BufferBuilder builder,
                               MeshData renderedBuffer, Identifier maskTexture,
                               int maskColor, float maskTolerance) {
        ShaderMaskPass pass = ShaderMaskPass.masked(renderType, maskTexture, maskColor, maskTolerance, 1.0f);
        ShaderMaskRenderQueue.enqueue(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void drawNow(RenderType renderType, BufferBuilder builder,
                               MeshData renderedBuffer, Identifier maskTexture,
                               int maskColor, float maskTolerance) {
        ShaderMaskPass pass = ShaderMaskPass.masked(renderType, maskTexture, maskColor, maskTolerance, 1.0f);
        ShaderMaskRenderQueue.drawNow(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void flush() {
        ShaderMaskRenderQueue.flush();
    }
}
