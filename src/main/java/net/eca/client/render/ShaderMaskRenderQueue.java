// Fork multi-version layer, 26.3 (#37): as 26.2's copy, the queue the level's extension passes go to (submitted before the
// clear pass, see net.eca.mv.SubmitPhase) is drawn INSIDE the level's main pass at AfterTranslucentFeatures (InLevelPass
// below) -- on 26.3 INTO that event's render pass, which is open there (net.eca.mv.RenderPasses). The after-level flushes
// (GameRendererPostLevelMixin) still draw, in a pass of their own, what is queued later (a first-person item, the block
// extension passes with Oculus) and everything under improved transparency, where 26.3 posts no AfterTranslucentFeatures.
// Carried from 1.21.11/26.1.2/26.2: in draw(), a vanilla RenderType in the queue is drawn with its own pipeline through the
// shared helper (see the comment there) -- on 26.3 into the open pass when there is one (MultiBufferSource.draw(type, mesh, into)).
package net.eca.client.render;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.vertex.VertexFormat;
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
            if (GuiTexturePasses.collecting()) {
                // 26.3 (ECA-020): submitted while a picture-in-picture (or GUI item) texture is drawn: into that texture
                GuiTexturePasses.add(pass, builder, renderedBuffer, uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform);
                queued = true;
                return;
            }
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
     * 26.3 (#37): queue a draw of the caller's own (a blend material's, with the uniforms it captured at submit), drawn
     * in submit order with the extension passes, under the model-view and projection captured now. {@code draw} takes
     * the mesh and closes it, and gets the render pass to draw INTO (the level's main pass) or null (a pass-free
     * flush: it opens its own); the builder is recycled after.
     */
    public static void enqueueDraw(BufferBuilder builder, MeshData renderedBuffer,
                                   java.util.function.BiConsumer<MeshData, com.mojang.renderpearl.api.commands.RenderPass> draw) {
        boolean queued = false;
        try {
            if (GuiTexturePasses.collecting()) {
                GuiTexturePasses.addDraw(builder, renderedBuffer, draw);   // 26.3 (ECA-020): see enqueue
                queued = true;
                return;
            }
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

    /** Order a translucent mesh built in {@link #acquireBuilder}'s builder back to front for the current projection. */
    public static void sortQuads(BufferBuilder builder, MeshData mesh) {
        ByteBufferBuilder backing = BUILDER_BACKING.get(builder);
        if (backing != null && mesh != null) {
            mesh.sortQuads(backing, RenderSystem.getProjectionType().vertexSorting());
        }
    }

    public static void drawNow(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer) {
        drawNow(pass, builder, renderedBuffer, MaskUvTransform.IDENTITY);
    }

    public static void drawNow(ShaderMaskPass pass, BufferBuilder builder,
                               MeshData renderedBuffer,
                               MaskUvTransform uvTransform) {
        if (GuiTexturePasses.collecting()) {
            // 26.3 (ECA-020): a picture-in-picture entity's pass, submitted before vanilla opens the texture's pass;
            // drawn now it went to the main target
            GuiTexturePasses.add(pass, builder, renderedBuffer, uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform);
            return;
        }
        try {
            draw(pass, renderedBuffer, uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform);
        } finally {
            recycle(builder);
        }
    }

    /** 26.3 (#33): drawn INTO {@code into}, a render pass vanilla already opened (an offscreen GUI texture's), which
     *  stays open; see GuiTexturePasses. */
    public static void drawInto(ShaderMaskPass pass, BufferBuilder builder,
                                MeshData renderedBuffer,
                                MaskUvTransform uvTransform,
                                com.mojang.renderpearl.api.commands.RenderPass into) {
        try {
            draw(pass, renderedBuffer, uvTransform == null ? MaskUvTransform.IDENTITY : uvTransform, into);
        } finally {
            recycle(builder);
        }
    }

    /** 26.3 (ECA-020): a collected draw of the caller's own (see enqueueDraw) drawn INTO {@code into}, which stays open. */
    public static void drawCustomInto(BufferBuilder builder, MeshData renderedBuffer,
                                      java.util.function.BiConsumer<MeshData, com.mojang.renderpearl.api.commands.RenderPass> draw,
                                      com.mojang.renderpearl.api.commands.RenderPass into) {
        try {
            draw.accept(renderedBuffer, into);
        } finally {
            recycle(builder);
        }
    }

    /** 26.3 (#33): a collected pass that is not drawn after all: its mesh is closed and its builder recycled. */
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

    /** 26.3 (#37): draw what is queued INTO {@code into}, a render pass that is open (the level's main pass). */
    public static void flushInto(com.mojang.renderpearl.api.commands.RenderPass into) {
        if (QUEUE.isEmpty()) {
            return;
        }
        List<QueuedPass> entries = new ArrayList<>(QUEUE);
        QUEUE.clear();
        flushEntries(entries, into);
    }

    private static void flushEntries(List<QueuedPass> entries) {
        flushEntries(entries, null);
    }

    private static void flushEntries(List<QueuedPass> entries, com.mojang.renderpearl.api.commands.RenderPass into) {
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        try {
            for (QueuedPass entry : entries) {
                RenderSystem.getModelViewStack().pushMatrix();
                try {
                    RenderSystem.getModelViewStack().identity();
                    RenderSystem.getModelViewStack().mul(entry.modelView());
                    RenderSystem.setProjectionMatrix(entry.projection(), com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
                    if (entry.customDraw() != null) {
                        entry.customDraw().accept(entry.renderedBuffer(), into);
                    } else {
                        draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform(), into);
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
        draw(pass, renderedBuffer, uvTransform, null);
    }

    // 26.3 (#33): into an open pass when one is given (net.eca.mv.LegacyGl.draw(mesh, pass)), else a pass of its own
    private static void draw(ShaderMaskPass pass, MeshData renderedBuffer,
                             MaskUvTransform uvTransform,
                             com.mojang.renderpearl.api.commands.RenderPass into) {
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
                // own uniforms there -- else the way the frame's buffers do;
                // 26.3: into the open pass when given, e.g. a picture-in-picture texture's (ECA-020) -- a copy's own
                // draw(MeshData, RenderPass) -- else a pass of its own)
                net.eca.mv.MultiBufferSource.draw(pass.renderType(), renderedBuffer, into);
                return;
            }
            net.eca.mv.LegacyGl.setupRenderState(pass.renderType());
            stateActive = true;
            if (into == null) {
                net.eca.mv.LegacyGl.drawWithShader(renderedBuffer);
            } else {
                net.eca.mv.LegacyGl.draw(renderedBuffer, into);
            }
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
        java.util.function.BiConsumer<MeshData, com.mojang.renderpearl.api.commands.RenderPass> customDraw
    ) {
    }

    /**
     * 26.3 (#37): draw the level's queued passes inside its main pass, as 26.2's copy does. 26.3 posts
     * AfterTranslucentFeatures from LevelRenderer.executeClassicTransparency, inside the "Main" render pass, after the clear
     * pass (FrameGraphBuilder runs "clear" before "main") and featureFrame.executeTranslucent, before translucent terrain,
     * AfterTranslucentBlocks, executeTranslucentAfterTerrain (particles), the clouds, the weather and the world border,
     * all drawn into that same pass. A pass is open there, so the queue is drawn INTO the event's pass (LegacyGl.draw(mesh,
     * pass)); the shared MultiBufferSource.Flush ends the main batch into it at this event too, and this listener (LOW)
     * runs after it, so a layer's textured part is under its shader passes. With improved transparency 26.3 posts no
     * AfterTranslucentFeatures and the after-level flush draws the queue, as before.
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
            net.eca.mv.MultiBufferSource.main().endBatch(event.getRenderPass());
            flushInto(event.getRenderPass());
        }
    }
}
