// MV-STAND-IN: 1.21.11's prepared copy of the 1.21.10 ItemModelResolverMixin overlay as it was before 1.21.10's GUI item atlas
// MV-STAND-IN: setAnimated for stacks with item extension passes, unchanged; only so that 1.21.10 fix does not reach 1.21.11 (and 26.x, which inherit it) unported and ungated.
// OVERLAY-ONLY: community fork multi-version layer, 1.21.10 copy of the 1.21.4 one. 1.21.4 renders items from an ItemStackRenderState
// without the stack on it; this records the stack for ItemRendererMixin (shared helper net.eca.mv.ItemStacks). 1.21.9 changed
// appendItemLayers to take an ItemOwner (the holder of the item) where it took the LivingEntity. Added to this target's
// eca.mixins.json by the 1.21.4 target.properties mixin_add.
package net.eca.mixin;

import net.eca.mv.ItemStacks;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void eca$recordStack(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                 ItemOwner owner, int seed, CallbackInfo ci) {
        ItemStacks.record(state, stack);
    }
}
