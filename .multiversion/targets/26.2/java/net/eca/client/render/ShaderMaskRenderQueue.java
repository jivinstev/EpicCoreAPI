// Fork multi-version layer, 26.2 (#37): the queue the level's extension passes go to (submitted before the clear pass, see
// net.eca.mv.SubmitPhase) is drawn INSIDE the level's main pass, at AfterTranslucentFeatures (InLevelPass below): after
// the entities, before translucent terrain, particles and weather, as 26.1.2's entity pass drew them. The after-level flushes
// (GameRendererPostLevelMixin) still draw what is queued later (a first-person item) and, with Oculus shaders, everything.
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
                    draw(entry.pass(), entry.renderedBuffer(), entry.uvTransform());
                } finally {
                    RenderSystem.getModelViewStack().popMatrix();
                    recycle(entry.builder());
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
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
            net.eca.mv.LegacyGl.setupRenderState(pass.renderType());
            stateActive = true;
            net.eca.mv.LegacyGl.drawWithShader(renderedBuffer);
        } finally {
            if (stateActive) {
                net.eca.mv.LegacyGl.clearRenderState(pass.renderType());
            }
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
