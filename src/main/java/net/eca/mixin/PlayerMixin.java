// 1.21.5 overlay: Player no longer overrides setItemSlot (equipment moved into LivingEntity's EntityEquipment), so the
// setItemSlot guard moved to LivingEntityPlayerSlotMixin (target-only, added via target.properties mixin_add).
package net.eca.mixin;

import net.eca.api.EcaAPI;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttack(Entity target, CallbackInfo ci) {
        if (EcaAPI.isInvulnerable(target)) {
            ci.cancel();
        }
    }
}
