package net.eca.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ShaderMaskRenderQueue {

    private static final int MAX_POOL_SIZE = 64;
    private static final List<QueuedPass> QUEUE = new ArrayList<>();
    private static final Deque<ByteBufferBuilder> BUILDER_POOL = new ArrayDeque<>();
    private static final Map<BufferBuilder, ByteBufferBuilder> BUILDER_BACKING = new IdentityHashMap<>();

    private ShaderMaskRenderQueue() {
    }

    public static BufferBuilder acquireBuilder(PrimitiveTopology mode, VertexFormat format) {
        ByteBufferBuilder backing = BUILDER_POOL.pollFirst();
        if (backing == null) {
            backing = new ByteBufferBuilder(262144);
        }
        BufferBuilder builder = new BufferBuilder(backing, mode, format);
        BUILDER_BACKING.put(builder, backing);
        return builder;
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer) {
        enqueue(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer,
                               MaskUvTransform uvTransform) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        QUEUE.add(new QueuedPass(pass, builder, renderedBuffer, modelView, projection,
            uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform));
    }

    public static void drawNow(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer) {
        drawNow(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void drawNow(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer,
                               MaskUvTransform uvTransform) {
        try {
            draw(pass, renderedBuffer, uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform);
        } finally {
            recycle(builder);
        }
    }

    public static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<QueuedPass> entries = new ArrayList<>(QUEUE);
        QUEUE.clear();
        Runnable work = () -> flushEntries(entries);
        if (RenderSystem.isOnRenderThread()) {
            work.run();
        } else {
            work.run();
        }
    }

    private static void flushEntries(List<QueuedPass> entries) {
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        try {
            for (QueuedPass entry : entries) {
                RenderSystem.getModelViewStack().pushMatrix();
                try {
                    RenderSystem.getModelViewStack().identity();
                    RenderSystem.getModelViewStack().mul(entry.modelView());
                    RenderSystem.setProjectionMatrix(entry.projection(), ProjectionType.PERSPECTIVE);
                    draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform());
                } finally {
                    RenderSystem.getModelViewStack().popMatrix();
                    recycle(entry.builder());
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.PERSPECTIVE);
        }
    }

    private static void draw(ShaderMaskPass pass, MeshData renderedBuffer,
                             MaskUvTransform uvTransform) {
        applyMask(pass);
        EcaShaderInstance.setLocalUvBounds(uvTransform.minU(), uvTransform.minV(),
            uvTransform.scaleU(), uvTransform.scaleV());
        EcaShaderInstance.setOpacity(pass.alpha());
        try (MeshData mesh = renderedBuffer;
             GpuBuffer vertexBuffer = RenderSystem.getDevice()
                 .createBuffer(() -> "ECA shader mask pass", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
            int indexCount = mesh.drawState().indexCount();
            pass.renderType().prepare().drawFromBuffer(vertexBuffer, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
        } finally {
            EcaShaderInstance.clearColorKey();
            EcaShaderInstance.clearShaderMask();
            EcaShaderInstance.clearLocalUvBounds();
            EcaShaderInstance.clearOpacity();
        }
    }

    private static void applyMask(ShaderMaskPass pass) {
        if (pass.maskSource() == ShaderMaskSource.BASE_TEXTURE) {
            int color = pass.maskColor();
            EcaShaderInstance.setColorKey(
                (color >> 16 & 0xFF) / 255.0f,
                (color >> 8 & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                pass.maskTolerance()
            );
            EcaShaderInstance.clearShaderMask();
            return;
        }
        EcaShaderInstance.clearColorKey();
        EcaShaderInstance.setShaderMask(pass.maskSource(), pass.maskTexture(),
            pass.maskColor(), pass.maskTolerance());
    }

    private static void recycle(BufferBuilder builder) {
        if (builder == null) {
            return;
        }
        ByteBufferBuilder backing = BUILDER_BACKING.remove(builder);
        if (backing != null) {
            if (BUILDER_POOL.size() < MAX_POOL_SIZE) {
                BUILDER_POOL.addLast(backing);
            } else {
                backing.close();
            }
        }
    }

    private record QueuedPass(
        ShaderMaskPass pass,
        BufferBuilder builder,
        MeshData renderedBuffer,
        Matrix4f modelView,
        GpuBufferSlice projection,
        MaskUvTransform uvTransform
    ) {
    }
}
