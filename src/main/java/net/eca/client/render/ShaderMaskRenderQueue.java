// Fork multi-version layer: 1.21.8 version of the authors' file (1.21.5's overlay, with 1.21.8's GpuBufferSlice projection).
// In draw(): a vanilla RenderType in the queue is drawn with its own draw(mesh) (see the comment there), as on 1.21.5.
package net.eca.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ShaderMaskRenderQueue {

    private static final List<QueuedPass> QUEUE = new ArrayList<>();
    private static final RenderBufferPool BUILDER_POOL = new RenderBufferPool();

    private ShaderMaskRenderQueue() {
    }

    public static BufferBuilder acquireBuilder(VertexFormat.Mode mode, VertexFormat format) {
        return BUILDER_POOL.acquire(mode, format);
    }

    public static void releaseBuilder(BufferBuilder builder) {
        BUILDER_POOL.release(builder);
    }

    public static void discardPending() {
        QUEUE.clear();
        BUILDER_POOL.releaseAll();
    }

    public static void render(ShaderMaskPass pass, boolean queued, Consumer<BufferBuilder> writer) {
        BufferBuilder builder = acquireBuilder(VertexFormat.Mode.QUADS, pass.renderType().format());
        boolean submitted = false;
        try {
            writer.accept(builder);
            // 1.21: build() 在没有顶点时返回 null（1.20 的 end() 返回空缓冲），此时不提交。
            MeshData renderedBuffer = builder.build();
            if (renderedBuffer != null) {
                if (queued) {
                    enqueue(pass, builder, renderedBuffer);
                } else {
                    drawNow(pass, builder, renderedBuffer);
                }
                submitted = true;
            }
        } finally {
            if (!submitted) {
                recycle(builder);
            }
        }
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer) {
        enqueue(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer,
                               MaskUvTransform uvTransform) {
        boolean queued = false;
        try {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
            QUEUE.add(new QueuedPass(pass, builder, renderedBuffer, modelView, projection,
                uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform));
            queued = true;
        } finally {
            if (!queued) {
                recycle(builder);
            }
        }
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
            net.eca.mv.LegacyGl.recordRenderCall(work::run);
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
                    RenderSystem.setProjectionMatrix(entry.projection(), com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
                    draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform());
                } finally {
                    RenderSystem.getModelViewStack().popMatrix();
                }
            }
        } finally {
            try {
                RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
            } finally {
                // 绘制中断时，尚未上传的批次也必须归还。
                for (QueuedPass entry : entries) {
                    recycle(entry.builder());
                }
            }
        }
    }

    private static void draw(ShaderMaskPass pass, MeshData renderedBuffer,
                             MaskUvTransform uvTransform) {
        applyMask(pass);
        EcaShaderInstance.setLocalUvBounds(uvTransform.minU(), uvTransform.minV(),
            uvTransform.scaleU(), uvTransform.scaleV());
        EcaShaderInstance.setOpacity(pass.alpha());
        boolean stateActive = false;
        try {
            if (!(pass.renderType() instanceof net.eca.mv.LegacyRenderType)) {
                // [mv] 1.21.5+: a vanilla RenderType's setupRenderState no longer sets its shader or its blend / depth /
                // cull state (they are its RenderPipeline), so LegacyGl.drawWithShader would draw it with whatever
                // shader was set last; its own draw(mesh) sets it up, draws with its pipeline and clears it, as 1.21.1's
                // setupRenderState + BufferUploader.drawWithShader + clearRenderState did
                pass.renderType().draw(renderedBuffer);
                return;
            }
            pass.renderType().setupRenderState();
            stateActive = true;
            net.eca.mv.LegacyGl.drawWithShader(renderedBuffer);
        } finally {
            try {
                if (stateActive) {
                    pass.renderType().clearRenderState();
                }
            } finally {
                EcaShaderInstance.clearColorKey();
                EcaShaderInstance.clearShaderMask();
                EcaShaderInstance.clearLocalUvBounds();
                EcaShaderInstance.clearOpacity();
            }
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
        BUILDER_POOL.release(builder);
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
