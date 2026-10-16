// OVERLAY-ONLY: community fork multi-version layer, 26.2 copy (#37). 26.2 draws every GUI, tooltip, oversized and
// picture-in-picture item into an offscreen texture -- GuiItemAtlas.drawToSlot or PictureInPictureRenderer.prepare -- by
// submitting it, then calling FeatureRenderDispatcher.renderAllFeatures(storage) with RenderSystem's output overrides set to
// that texture. An item extension's shader passes drawn at submit therefore went into the texture BEFORE the item and the
// item covered them (a regression from 26.1.2). They are collected here at submit instead and drawn right after
// renderAllFeatures returns (GuiItemAtlasFeaturesMixin, PictureInPictureFeaturesMixin, kept to 26.2 by mixin_add_only),
// with the overrides, scissor and projection still the texture's, and the model-view captured at submit.
// 26.3 has its own copy (its features draw into a render pass it hands over).
package net.eca.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class GuiTexturePasses {
    private static int depth;
    private static final List<Entry> PENDING = new ArrayList<>();

    private GuiTexturePasses() {
    }

    /** Vanilla starts drawing an item into an offscreen texture (GuiItemAtlas.drawToSlot, PictureInPictureRenderer.prepare). */
    public static void begin() {
        if (depth++ == 0) {
            discard();   // what an earlier texture left (it threw before its end)
        }
    }

    /** Whether a pass submitted now is drawn by {@link #drawAfterFeatures} after vanilla's features. */
    public static boolean collecting() {
        return depth > 0;
    }

    public static void add(ShaderMaskPass pass, BufferBuilder builder, MeshData mesh, MaskUvTransform uvTransform) {
        PENDING.add(new Entry(pass, builder, mesh, uvTransform, new Matrix4f(RenderSystem.getModelViewStack()),
            RenderSystem.getProjectionMatrixBuffer(), RenderSystem.getProjectionType()));
    }

    /** renderAllFeatures returned: the item is in the texture. Draw what was collected over it, in submit order. */
    public static void drawAfterFeatures() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Entry> now = new ArrayList<>(PENDING);
        PENDING.clear();
        // the item may have gone into the shared buffer source; end it first so the passes land over it
        net.eca.mv.MultiBufferSource.main().endBatch();
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedType = RenderSystem.getProjectionType();
        try {
            for (Entry e : now) {
                RenderSystem.getModelViewStack().pushMatrix();
                try {
                    RenderSystem.getModelViewStack().set(e.modelView());
                    RenderSystem.setProjectionMatrix(e.projection(), e.projectionType());
                    ShaderMaskRenderQueue.drawNow(e.pass(), e.builder(), e.mesh(), e.uvTransform());
                } finally {
                    RenderSystem.getModelViewStack().popMatrix();
                }
            }
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, savedType);
        }
    }

    /** The texture is done; anything not drawn is dropped. */
    public static void end() {
        if (depth > 0 && --depth == 0) {
            discard();
        }
    }

    private static void discard() {
        for (Entry e : PENDING) {
            ShaderMaskRenderQueue.release(e.builder(), e.mesh());
        }
        PENDING.clear();
    }

    private record Entry(ShaderMaskPass pass, BufferBuilder builder, MeshData mesh, MaskUvTransform uvTransform,
                         Matrix4f modelView, GpuBufferSlice projection, ProjectionType projectionType) {
    }
}
