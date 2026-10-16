// OVERLAY-ONLY: community fork multi-version layer, 1.21.10 copy of the 1.21.4 one. 1.21.4 renders items from an ItemStackRenderState
// without the stack on it; this records the stack for ItemRendererMixin (shared helper net.eca.mv.ItemStacks). 1.21.9 changed
// appendItemLayers to take an ItemOwner (the holder of the item) where it took the LivingEntity. Added to this target's
// eca.mixins.json by the 1.21.4 target.properties mixin_add.
// 1.21.10 (port of 1.21.8's): GUI items are drawn into GuiRenderer's cached item atlas, and a slot is redrawn only while its
// state isAnimated() (GuiRenderer.prepareItemElements); a stack whose item extension draws shader passes (time-driven) marks
// the state animated, as a foil or an animated texture does, so the pass runs every frame in the inventory, hotbar and
// tooltips as on 1.21.1.
package net.eca.mixin;

import net.eca.mv.ItemStacks;
import net.eca.client.render.ShaderMaskPass;
import net.eca.util.item_extension.ItemExtension;
import net.eca.util.item_extension.ItemExtensionManager;
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

import java.util.List;

@OnlyIn(Dist.CLIENT)
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"))
    private void eca$recordStack(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                 ItemOwner owner, int seed, CallbackInfo ci) {
        ItemStacks.record(state, stack);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void eca$markExtensionAnimated(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                           ItemOwner owner, int seed, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        ItemExtension extension = ItemExtensionManager.getExtension(stack.getItem());
        if (extension == null || !extension.enabled() || !extension.shouldRender(stack)) return;
        List<ShaderMaskPass> passes = extension.getShaderPasses();
        if (passes == null || passes.isEmpty()) return;
        state.setAnimated();
    }
}
