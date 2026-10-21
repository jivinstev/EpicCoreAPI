// OVERLAY-ONLY: community fork multi-version layer (1.21.4 and up): reads ItemStackRenderState's private layers so
// ItemRendererMixin can run its pass for the state's first layer only (once per render of the state). Registered in
// eca.mixins.json by .port/1.21.4/branch.properties mixin_add; the later version branches inherit it with the merge
// and keep it registered (1.21.5's own ItemRendererMixin, inherited by 1.21.8, 1.21.10 and 1.21.11, reads the layers too).
package net.eca.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateLayersAccessor {
    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] eca$layers();
}
