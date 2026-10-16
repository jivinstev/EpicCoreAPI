// OVERLAY-ONLY: community fork multi-version layer, 26.3 only (#33). 26.3 draws every GUI, tooltip, oversized and
// picture-in-picture item into an offscreen texture -- GuiItemAtlas's slot (redrawn only when the item changes) or a
// PictureInPictureRenderer's -- in a render pass vanilla opens on that texture after the item was submitted. An item
// extension's shader passes submitted then are collected here and drawn INTO that pass, right after vanilla's features
// (GuiItemAtlasMixin, PictureInPictureRendererMixin), through the shared helper's net.eca.mv.LegacyGl.draw(mesh, pass):
// so they land in the texture, at its projection and scissor, with the item.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.commands.RenderPass;

import java.util.ArrayList;
import java.util.List;

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
        PENDING.add(new Entry(pass, builder, mesh, uvTransform));
    }

    /** Vanilla drew its features into {@code into} (still open): draw what was collected into it too. */
    public static void drawInto(RenderPass into) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Entry> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Entry e : now) {
            ShaderMaskRenderQueue.drawInto(e.pass(), e.builder(), e.mesh(), e.uvTransform(), into);
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

    private record Entry(ShaderMaskPass pass, BufferBuilder builder, MeshData mesh, MaskUvTransform uvTransform) {
    }
}
