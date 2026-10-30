package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public final class SpriteBatchingVertexConsumer implements VertexConsumer, AutoCloseable {

    // 烘焙方块的光照只经 putBulkData 的 lightmap 数组进入缓冲，没有独立的 packedLight 形参，
    // 所以发光只能在这里替换该数组。VertexConsumer 的默认实现只读不写，共享常量数组安全。
    // LightTexture.FULL_BRIGHT (sky 15, block 15 packed lightmap coords), inlined because LightTexture is absent in 26.1.2.
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int[] FULL_BRIGHT_LIGHTMAP = {
        FULL_BRIGHT, FULL_BRIGHT,
        FULL_BRIGHT, FULL_BRIGHT
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
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        if (fullBright) {
            instance.setLightCoords(FULL_BRIGHT);
        }
        builder(quad.materialInfo().sprite()).putBakedQuad(pose, quad, instance);
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
        return ShaderMaskRenderQueue.acquireBuilder(PrimitiveTopology.QUADS, format);
    }

    private BufferBuilder direct() {
        return builder(null);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        direct().addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        direct().setColor(red, green, blue, alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        direct().setColor(argb);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        direct().setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        direct().setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        direct().setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        direct().setUv3(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        direct().setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        // Line width is not part of batched quad vertex data, so it is accepted and has no effect.
        return this;
    }




    public record SpriteBatch(BufferBuilder builder, MaskUvTransform uvTransform) {
    }
}
