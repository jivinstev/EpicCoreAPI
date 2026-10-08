package net.eca.mixin;

import net.eca.client.music.CombatMusicManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void eca$blockVanillaMusicWhenLocked(SoundInstance soundInstance, CallbackInfo ci) {
        if (soundInstance == null) {
            return;
        }
        if (CombatMusicManager.shouldBlockMusic(soundInstance.getLocation(), soundInstance.getSource())) {
            ci.cancel();
        }
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void eca$blockDelayedVanillaMusicWhenLocked(SoundInstance soundInstance, int delay, CallbackInfo ci) {
        if (soundInstance == null) {
            return;
        }
        if (CombatMusicManager.shouldBlockMusic(soundInstance.getLocation(), soundInstance.getSource())) {
            ci.cancel();
        }
    }
}
