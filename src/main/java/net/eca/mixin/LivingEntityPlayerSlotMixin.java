// OVERLAY-ONLY (1.21.5): on 1.21.4 PlayerMixin injected Player.setItemSlot. In 1.21.5 Player has no such override;
// setItemSlot is only declared on LivingEntity (and Mob), so the guard is injected there and restricted to players.
package net.eca.mixin;

import net.eca.api.EcaAPI;
import net.eca.util.EntityUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityPlayerSlotMixin {

    //防止外部代码替换无敌玩家的装备槽 (players only, as on 1.21.4)
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void eca$onSetItemSlot(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof Player player && EcaAPI.isInvulnerable(player) && EntityUtil.hasExternalCaller(5)) {
            ci.cancel();
        }
    }
}
