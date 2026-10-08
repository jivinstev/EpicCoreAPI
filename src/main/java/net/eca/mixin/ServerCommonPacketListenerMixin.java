package net.eca.mixin;

import net.eca.api.EcaAPI;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1.21 起 disconnect/send 声明在 ServerCommonPacketListenerImpl，ServerGamePacketListenerImpl 只是继承
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerMixin {

    //拦截踢人操作，防止无敌玩家被踢出服务器
    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void eca$onDisconnect(Component reason, CallbackInfo ci) {
        if (this.eca$isInvulnerableGamePlayer()) {
            ci.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
            at = @At("HEAD"), cancellable = true)
    private void eca$onDisconnectDetails(DisconnectionDetails details, CallbackInfo ci) {
        if (this.eca$isInvulnerableGamePlayer()) {
            ci.cancel();
        }
    }

    //拦截危险数据包（断线包、死亡包），防止无敌玩家被恶意包影响
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void eca$onSendPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (!this.eca$isInvulnerableGamePlayer()) {
            return;
        }

        if (packet instanceof ClientboundDisconnectPacket || packet instanceof ClientboundPlayerCombatKillPacket) {
            ci.cancel();
        }
    }

    private boolean eca$isInvulnerableGamePlayer() {
        return (Object) this instanceof ServerGamePacketListenerImpl game && EcaAPI.isInvulnerable(game.player);
    }
}
