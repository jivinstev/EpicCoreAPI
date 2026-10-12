// OVERLAY-ONLY: community fork multi-version layer. 1.21.4 renders items from an ItemStackRenderState without the
// stack on it; this records the stack for ItemRendererMixin (shared helper net.eca.mv.ItemStacks). Added to this
// target's eca.mixins.json by target.properties mixin_add.
package net.eca.mixin;

import net.eca.mv.ItemStacks;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void eca$recordStack(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                 LivingEntity entity, int seed, CallbackInfo ci) {
        ItemStacks.record(state, stack);
    }
}
