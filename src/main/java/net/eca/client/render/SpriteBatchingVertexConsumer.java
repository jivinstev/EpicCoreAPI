package net.eca.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class SpriteBatchingVertexConsumer implements VertexConsumer {

    private static final int FULL_BRIGHT = 0xF000F0;

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

    // 烘焙四边形的光照只经 QuadInstance 进入缓冲，所以发光只能在这里替换光照坐标
    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        QuadInstance target = instance;
        if (fullBright) {
            target = new QuadInstance();
            for (int vertex = 0; vertex < 4; vertex++) {
                target.setColor(vertex, instance.getColor(vertex));
            }
            target.setOverlayCoords(instance.overlayCoords());
            target.setLightCoords(FULL_BRIGHT);
        }
        builder(quad.materialInfo().sprite()).putBakedQuad(pose, quad, target);
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
            PrimitiveTopology.QUADS, format);
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
    public VertexConsumer setColor(int packedColor) {
        return setColor((packedColor >> 16) & 0xFF, (packedColor >> 8) & 0xFF,
            packedColor & 0xFF, (packedColor >>> 24) & 0xFF);
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

    @Override
    public VertexConsumer setLineWidth(float width) {
        direct().setLineWidth(width);
        return this;
    }




    public record SpriteBatch(BufferBuilder builder, MaskUvTransform uvTransform) {
    }
}
