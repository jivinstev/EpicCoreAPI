package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SpriteBatchingVertexConsumer implements VertexConsumer {

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
        builders.forEach((sprite, builder) ->
            consumer.accept(new SpriteBatch(builder, MaskUvTransform.fromSprite(sprite))));
        if (fallback != null) {
            consumer.accept(new SpriteBatch(fallback, MaskUvTransform.IDENTITY));
        }
        builders.clear();
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
        return new BufferBuilder(new ByteBufferBuilder(format.getVertexSize() * 256),
            VertexFormat.Mode.QUADS, format);
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
    public VertexConsumer setNormal(float x, float y, float z) {
        direct().setNormal(x, y, z);
        return this;
    }




    public record SpriteBatch(BufferBuilder builder, MaskUvTransform uvTransform) {
    }
}
