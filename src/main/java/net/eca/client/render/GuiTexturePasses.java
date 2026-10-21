// OVERLAY-ONLY: community fork multi-version layer, 26.3 only (#33). 26.3 draws every GUI, tooltip, oversized and
// picture-in-picture item into an offscreen texture -- GuiItemAtlas's slot (redrawn only when the item changes) or a
// PictureInPictureRenderer's -- in a render pass vanilla opens on that texture after the item was submitted. An item
// extension's shader passes submitted then are collected here and drawn INTO that pass, right after vanilla's features
// (GuiItemAtlasMixin, PictureInPictureRendererMixin), through the shared helper's net.eca.mv.LegacyGl.draw(mesh, pass):
// so they land in the texture, at its projection and scissor, with the item.
// 26.3 (ECA-020): not only a GUI item's passes. Everything the after-level queue would take while a texture is drawn -- a
// picture-in-picture entity's extension layers, the world-context passes of the items it holds, a blend material's draw --
// comes here too (ShaderMaskRenderQueue.enqueue / enqueueDraw / drawNow), so it lands in the picture-in-picture texture
// instead of on the main target.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.commands.RenderPass;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class GuiTexturePasses {
    private static boolean collecting;
    private static final List<Entry> PENDING = new ArrayList<>();

    private GuiTexturePasses() {
    }

    /** Vanilla starts drawing an item into an offscreen texture (GuiItemAtlas.drawToSlot, PictureInPictureRenderer.prepare). */
    public static void begin() {
        discard();   // what an earlier texture left (it threw before its end)
        collecting = true;
    }

    /** Whether an item submitted now is drawn into an offscreen texture whose pass {@link #drawInto} will be handed. */
    public static boolean collecting() {
        return collecting;
    }

    public static void add(ShaderMaskPass pass, BufferBuilder builder, MeshData mesh, MaskUvTransform uvTransform) {
        PENDING.add(new Entry(pass, builder, mesh, uvTransform, null));
    }

    /** 26.3 (ECA-020): a draw of the caller's own (a blend material's), handed the texture's pass; see
     *  ShaderMaskRenderQueue.enqueueDraw. */
    public static void addDraw(BufferBuilder builder, MeshData mesh, BiConsumer<MeshData, RenderPass> draw) {
        PENDING.add(new Entry(null, builder, mesh, MaskUvTransform.IDENTITY, draw));
    }

    /** Vanilla drew its features into {@code into} (still open): draw what was collected into it too. */
    public static void drawInto(RenderPass into) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Entry> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Entry e : now) {
            if (e.customDraw() != null) {
                ShaderMaskRenderQueue.drawCustomInto(e.builder(), e.mesh(), e.customDraw(), into);
            } else {
                ShaderMaskRenderQueue.drawInto(e.pass(), e.builder(), e.mesh(), e.uvTransform(), into);
            }
        }
    }

    /** The texture is done; anything its pass did not take is dropped. */
    public static void end() {
        collecting = false;
        discard();
    }

    private static void discard() {
        for (Entry e : PENDING) {
            ShaderMaskRenderQueue.release(e.builder(), e.mesh());
        }
        PENDING.clear();
    }

    private record Entry(ShaderMaskPass pass, BufferBuilder builder, MeshData mesh, MaskUvTransform uvTransform,
                         BiConsumer<MeshData, RenderPass> customDraw) {
    }
}
