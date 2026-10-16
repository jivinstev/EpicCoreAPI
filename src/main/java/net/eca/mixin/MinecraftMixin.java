package net.eca.mixin;

import net.eca.api.EcaAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// fork layer (26.2): Minecraft.setScreen moved to Gui.setScreen (Minecraft keeps only setScreenAndShow), so the
// authors' death-screen veto targets Gui; the player is Minecraft's, as before.
@Mixin(Gui.class)
public class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen modifyScreen(Screen screen) {
        // 如果尝试设置死亡界面且玩家无敌，替换为null
        if (screen instanceof DeathScreen && EcaAPI.isInvulnerable(Minecraft.getInstance().player)) {
            return null;
        }
        return screen;
    }
}
