package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class SpriteBatchingVertexConsumer implements VertexConsumer, AutoCloseable {

    // 烘焙方块的光照只经 putBulkData 的 lightmap 数组进入缓冲，没有独立的 packedLight 形参，
    // 所以发光只能在这里替换该数组。VertexConsumer 的默认实现只读不写，共享常量数组安全。
    private static final int[] FULL_BRIGHT_LIGHTMAP = {
        LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT,
        LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT
    };

    private final VertexFormat format;
    private final boolean fullBright;
    private final Map<TextureAtlasSprite, BufferBuilder> builders = new IdentityHashMap<>();
    private BufferBuilder fallback;

    public SpriteBatchingVertexConsumer(VertexFormat format) {
        this(format, false);
    }

    public SpriteBatchingVertexConsumer(VertexFormat format, boolean fullBright) {
        this.format = format;
        this.fullBright = fullBright;
    }

    public void finish(Consumer<SpriteBatch> consumer) {
        try {
            Iterator<Map.Entry<TextureAtlasSprite, BufferBuilder>> iterator = builders.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<TextureAtlasSprite, BufferBuilder> entry = iterator.next();
                BufferBuilder builder = entry.getValue();
                MaskUvTransform uvTransform = MaskUvTransform.fromSprite(entry.getKey());
                iterator.remove();
                submit(consumer, builder, uvTransform);
            }
            if (fallback != null) {
                BufferBuilder builder = fallback;
                fallback = null;
                submit(consumer, builder, MaskUvTransform.IDENTITY);
            }
        } finally {
            close();
        }
    }

    private static void submit(Consumer<SpriteBatch> consumer, BufferBuilder builder,
                               MaskUvTransform uvTransform) {
        boolean submitted = false;
        try {
            consumer.accept(new SpriteBatch(builder, uvTransform));
            submitted = true;
        } finally {
            if (!submitted) {
                ShaderMaskRenderQueue.releaseBuilder(builder);
            }
        }
    }

    @Override
    public void close() {
        builders.values().forEach(ShaderMaskRenderQueue::releaseBuilder);
        builders.clear();
        ShaderMaskRenderQueue.releaseBuilder(fallback);
        fallback = null;
    }

    @Override
    public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                            float red, float green, float blue, float alpha,
                            int[] lights, int overlay, boolean readExistingColor) {
        builder(quad.getSprite()).putBulkData(pose, quad, brightness, red, green, blue,
            alpha, fullBright ? FULL_BRIGHT_LIGHTMAP : lights, overlay, readExistingColor);
    }

    private BufferBuilder builder(TextureAtlasSprite sprite) {
        if (sprite == null) {
            if (fallback == null) {
                fallback = newBuilder();
            }
            return fallback;
        }
        return builders.computeIfAbsent(sprite, ignored -> newBuilder());
    }

    private BufferBuilder newBuilder() {
        BufferBuilder builder = ShaderMaskRenderQueue.acquireBuilder();
        builder.begin(VertexFormat.Mode.QUADS, format);
        return builder;
    }

    private BufferBuilder direct() {
        return builder(null);
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        direct().vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        direct().color(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        direct().uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        direct().overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        direct().uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        direct().normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        direct().endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        direct().defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        direct().unsetDefaultColor();
    }

    public record SpriteBatch(BufferBuilder builder, MaskUvTransform uvTransform) {
    }
}
