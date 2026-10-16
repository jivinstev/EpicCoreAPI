// OVERLAY-ONLY: community fork multi-version layer (1.21.4): reads ItemStackRenderState's private layers so
// ItemRendererMixin can run its pass for the state's first layer only (once per render of the state). Added to this
// target's eca.mixins.json alone by target.properties mixin_add_only (1.21.5 has its own ItemRendererMixin).
package net.eca.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateLayersAccessor {
    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] eca$layers();
}
