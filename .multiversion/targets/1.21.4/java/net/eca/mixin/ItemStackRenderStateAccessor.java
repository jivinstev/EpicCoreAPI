// OVERLAY-ONLY: community fork multi-version layer (1.21.4): reads ItemStackRenderState's package-private
// displayContext for ItemRendererMixin. Added to this target's eca.mixins.json by target.properties mixin_add.
package net.eca.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("displayContext")
    ItemDisplayContext eca$displayContext();
}
