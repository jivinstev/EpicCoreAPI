package net.eca.mixin;

import net.eca.client.music.CombatMusicManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import net.minecraft.client.sounds.SoundManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public class SoundManagerMixin {

    // 1.21.8: SoundManager.play returns SoundEngine.PlayResult (was void), so the callback is a
    // CallbackInfoReturnable. NOT_STARTED is what a blocked sound is: this injection starts no
    // replacement (CombatMusicManager plays its own track through a separate play call), and the
    // consumer, MusicManager.startPlaying, shows the "now playing" toast only on STARTED.
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void eca$blockVanillaMusicWhenLocked(SoundInstance soundInstance, CallbackInfoReturnable<PlayResult> ci) {
        if (soundInstance == null) {
            return;
        }
        if (CombatMusicManager.shouldBlockMusic(soundInstance.getLocation(), soundInstance.getSource())) {
            ci.setReturnValue(PlayResult.NOT_STARTED);
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
