package net.eca.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class ShaderMaskRenderQueue {

    private static final List<QueuedPass> QUEUE = new ArrayList<>();
    private static final RenderBufferPool BUILDER_POOL = new RenderBufferPool();

    private ShaderMaskRenderQueue() {
    }

    public static BufferBuilder acquireBuilder() {
        return BUILDER_POOL.acquire();
    }

    public static void releaseBuilder(BufferBuilder builder) {
        BUILDER_POOL.release(builder);
    }

    public static void discardPending() {
        QUEUE.clear();
        BUILDER_POOL.releaseAll();
    }

    public static void render(ShaderMaskPass pass, boolean queued, Consumer<BufferBuilder> writer) {
        BufferBuilder builder = acquireBuilder();
        boolean submitted = false;
        try {
            builder.begin(VertexFormat.Mode.QUADS, pass.renderType().format());
            writer.accept(builder);
            BufferBuilder.RenderedBuffer renderedBuffer = builder.end();
            if (queued) {
                enqueue(pass, builder, renderedBuffer);
            } else {
                drawNow(pass, builder, renderedBuffer);
            }
            submitted = true;
        } finally {
            if (!submitted) {
                recycle(builder);
            }
        }
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               BufferBuilder.RenderedBuffer renderedBuffer) {
        enqueue(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void enqueue(ShaderMaskPass pass, BufferBuilder builder,
                               BufferBuilder.RenderedBuffer renderedBuffer,
                               MaskUvTransform uvTransform) {
        boolean queued = false;
        try {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
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
                               BufferBuilder.RenderedBuffer renderedBuffer) {
        drawNow(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void drawNow(ShaderMaskPass pass, BufferBuilder builder,
                               BufferBuilder.RenderedBuffer renderedBuffer,
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
            RenderSystem.recordRenderCall(work::run);
        }
    }

    private static void flushEntries(List<QueuedPass> entries) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        try {
            for (QueuedPass entry : entries) {
                RenderSystem.getModelViewStack().pushPose();
                try {
                    RenderSystem.getModelViewStack().setIdentity();
                    RenderSystem.getModelViewStack().mulPoseMatrix(entry.modelView());
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setProjectionMatrix(entry.projection(), VertexSorting.DISTANCE_TO_ORIGIN);
                    draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform());
                } finally {
                    RenderSystem.getModelViewStack().popPose();
                    RenderSystem.applyModelViewMatrix();
                }
            }
        } finally {
            try {
                RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            } finally {
                // 绘制中断时，尚未上传的批次也必须归还。
                for (QueuedPass entry : entries) {
                    recycle(entry.builder());
                }
            }
        }
    }

    private static void draw(ShaderMaskPass pass, BufferBuilder.RenderedBuffer renderedBuffer,
                             MaskUvTransform uvTransform) {
        applyMask(pass);
        EcaShaderInstance.setLocalUvBounds(uvTransform.minU(), uvTransform.minV(),
            uvTransform.scaleU(), uvTransform.scaleV());
        EcaShaderInstance.setOpacity(pass.alpha());
        boolean stateActive = false;
        try {
            pass.renderType().setupRenderState();
            stateActive = true;
            BufferUploader.drawWithShader(renderedBuffer);
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
        BufferBuilder.RenderedBuffer renderedBuffer,
        Matrix4f modelView,
        Matrix4f projection,
        MaskUvTransform uvTransform
    ) {
    }
}
