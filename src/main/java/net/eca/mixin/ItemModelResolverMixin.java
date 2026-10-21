// OVERLAY-ONLY: community fork multi-version layer, 1.21.10 copy of the 1.21.4 one. 1.21.4 renders items from an ItemStackRenderState
// without the stack on it; this records the stack for ItemRendererMixin (shared helper net.eca.mv.ItemStacks). 1.21.9 changed
// updateForTopItem/appendItemLayers to take an ItemOwner (the holder of the item) where they took the LivingEntity. Added to this
// target's eca.mixins.json by the 1.21.4 target.properties mixin_add.
// Recorded at updateForTopItem (every fill of a state starts there: ItemRenderer, GuiGraphics, VaultRenderer,
// BreakingItemParticle), not at appendItemLayers: an open bundle's model (BundleSelectedItemSpecialRenderer) appends
// the selected item's layers into the bundle's state through appendItemLayers, which would replace the bundle with
// the selected item and draw that item's extension pass over the bundle tooltip (ECA-012). 1.21.5+ updateForTopItem has
// no leftHand boolean (1.21.10 vanilla: ItemStackRenderState, ItemStack, ItemDisplayContext, Level, ItemOwner, int).
// 1.21.10 (port of 1.21.8's): GUI items are drawn into GuiRenderer's cached item atlas, and a slot is redrawn only while its
// state isAnimated() (GuiRenderer.prepareItemElements); a stack whose item extension draws shader passes (time-driven) marks
// the state animated, as a foil or an animated texture does, so the pass runs every frame in the inventory, hotbar and
// tooltips as on 1.21.1.
// 1.21.11 (ECA-016, port of 1.21.10's): checked in the 1.21.11 sources -- updateForTopItem and appendItemLayers keep the
// (state, stack, context, level, ItemOwner, seed) shape (javap of the 21.11.45 merged jar), ItemStackRenderState.setAnimated()/
// isAnimated() exist, and GuiRenderer.prepareItemElements still redraws an atlas slot every frame only while the state isAnimated().
// The animated mark stays on appendItemLayers: it only forces a redraw, so marking a bundle whose selected item has passes is harmless.
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

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForTopItem", at = @At("HEAD"))
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
