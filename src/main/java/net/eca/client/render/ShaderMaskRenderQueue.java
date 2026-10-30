// Fork multi-version layer, 26.2 (#37): the queue the level's extension passes go to (submitted before the clear pass, see
// net.eca.mv.SubmitPhase) is drawn INSIDE the level's main pass, at AfterTranslucentFeatures (InLevelPass below): after
// the entities, before translucent terrain, particles and weather, as 26.1.2's entity pass drew them. The after-level flushes
// (GameRendererPostLevelMixin) still draw what is queued later (a first-person item) and, with Oculus shaders, everything.
// Carried from 1.21.11/26.1.2: the 1.21.11 version of the authors' file (1.21.5's overlay, with 1.21.8's GpuBufferSlice projection).
// In draw(): a vanilla RenderType in the queue is drawn with its own draw(mesh) (see the comment there), as on 1.21.5-1.21.10.
package net.eca.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ShaderMaskRenderQueue {

    private static final List<QueuedPass> QUEUE = new ArrayList<>();
    private static final RenderBufferPool BUILDER_POOL = new RenderBufferPool();
    // [mv] 26.2+: sortQuads needs each builder's ByteBufferBuilder, so the queue takes bare buffers from the pool
    // (acquireBuffer) and keeps the builder -> buffer map itself
    private static final Map<BufferBuilder, ByteBufferBuilder> BUILDER_BACKING = new IdentityHashMap<>();

    private ShaderMaskRenderQueue() {
    }

    public static BufferBuilder acquireBuilder(PrimitiveTopology mode, VertexFormat format) {
        ByteBufferBuilder backing = BUILDER_POOL.acquireBuffer();
        BufferBuilder builder = new BufferBuilder(backing, mode, format);
        BUILDER_BACKING.put(builder, backing);
        return builder;
    }

    public static void releaseBuilder(BufferBuilder builder) {
        if (builder == null) {
            return;
        }
        BUILDER_POOL.release(BUILDER_BACKING.remove(builder));
    }

    public static void discardPending() {
        QUEUE.clear();
        BUILDER_BACKING.clear();
        BUILDER_POOL.releaseAll();
    }

    public static void render(ShaderMaskPass pass, boolean queued, Consumer<BufferBuilder> writer) {
        BufferBuilder builder = acquireBuilder(PrimitiveTopology.QUADS, pass.renderType().format());
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
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
            GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
            QUEUE.add(new QueuedPass(pass, builder, renderedBuffer, modelView, projection,
                uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform, null));
            queued = true;
        } finally {
            if (!queued) {
                recycle(builder);
            }
        }
    }

    /**
     * 26.2 (#37): queue a draw of the caller's own (a blend material's, with the uniforms it captured at submit), drawn
     * in submit order with the extension passes, under the model-view and projection captured now. {@code draw} takes
     * the mesh and closes it; the builder is recycled after.
     */
    public static void enqueueDraw(BufferBuilder builder, MeshData renderedBuffer,
                                   java.util.function.Consumer<MeshData> draw) {
        boolean queued = false;
        try {
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
            GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
            QUEUE.add(new QueuedPass(null, builder, renderedBuffer, modelView, projection, MaskUvTransform.IDENTITY, draw));
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

    /** Order a translucent mesh built in {@link #acquireBuilder}'s builder back to front for the current projection. */
    public static void sortQuads(BufferBuilder builder, MeshData mesh) {
        ByteBufferBuilder backing = BUILDER_BACKING.get(builder);
        if (backing != null && mesh != null) {
            mesh.sortQuads(backing, RenderSystem.getProjectionType().vertexSorting());
        }
    }

    /** A collected pass that will not be drawn (GuiTexturePasses): free its mesh and builder. */
    public static void release(BufferBuilder builder, MeshData renderedBuffer) {
        try {
            if (renderedBuffer != null) {
                renderedBuffer.close();
            }
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
                    if (entry.customDraw() != null) {
                        entry.customDraw().accept(entry.renderedBuffer());
                    } else {
                        draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform());
                    }
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
                // [mv] 1.21.5+: a vanilla RenderType's set-up no longer sets its shader or its blend / depth / cull state
                // (they are its RenderPipeline; on 1.21.11 it has no setupRenderState at all), so LegacyGl.drawWithShader
                // would draw it with whatever shader was set last; its own draw(mesh) sets it up, draws with its pipeline
                // and clears it, as 1.21.1's setupRenderState + BufferUploader.drawWithShader + clearRenderState did
                // (26.2: RenderType.draw(MeshData) is gone; the shared helper draws it through the type's own
                // draw(MeshData) when it has one -- a dependent's relocated LegacyRenderType copy binds its shader's
                // own uniforms there -- else the way the frame's buffers do)
                net.eca.mv.MultiBufferSource.draw(pass.renderType(), renderedBuffer);
                return;
            }
            net.eca.mv.LegacyGl.setupRenderState(pass.renderType());
            stateActive = true;
            net.eca.mv.LegacyGl.drawWithShader(renderedBuffer);
        } finally {
            try {
                if (stateActive) {
                    net.eca.mv.LegacyGl.clearRenderState(pass.renderType());
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
        releaseBuilder(builder);
    }

    private record QueuedPass(
        ShaderMaskPass pass,
        BufferBuilder builder,
        MeshData renderedBuffer,
        Matrix4f modelView,
        GpuBufferSlice projection,
        MaskUvTransform uvTransform,
        java.util.function.Consumer<MeshData> customDraw
    ) {
    }

    /**
     * 26.2 (#37): draw the level's queued passes inside its main frame pass. 26.2 posts AfterTranslucentFeatures from
     * LevelRenderer's main-pass lambda, after the clear and PreparedFrame.executeTranslucent, before translucent terrain,
     * executeTranslucentAfterTerrain (particles) and the separate weather pass (javap of the 26.2 client). LegacyGl may
     * draw there on 26.2 (no GPU render pass is held open between those calls): the shared helper's
     * MultiBufferSource.Flush already ends the main batch through LegacyGl at this same event. It is ended here first
     * (this listener runs at LOW priority after it anyway), so a layer's textured part is under its shader passes.
     */
    @net.neoforged.fml.common.EventBusSubscriber(modid = net.eca.EcaMod.MOD_ID, value = Dist.CLIENT)
    public static final class InLevelPass {
        private InLevelPass() {
        }

        @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
        public static void afterTranslucentFeatures(net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentFeatures event) {
            if (QUEUE.isEmpty() || EcaShaderInstance.isOculusShadersActive()) {
                return;
            }
            net.eca.mv.MultiBufferSource.main().endBatch();
            flush();
        }
    }
}
