package net.eca.mixin;

import net.eca.coremod.AllReturnToggle;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// NeoForge 1.21.1: every payload is written and read through the StreamCodec that CustomPacketPayload.codec(...)
// builds (writeCap / decode), which takes the place of Forge's IndexedMessageCodec.tryEncode / tryDecode.
@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class NetworkCodecMixin {

    // 编码回调包含同步辅助调用，完整执行才能与接收端的数据布局一致。
    @SuppressWarnings("unchecked")
    @Redirect(method = "writeCap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;encode(Ljava/lang/Object;Ljava/lang/Object;)V"))
    private <B, T> void eca$encode(StreamCodec<B, T> codec, Object buffer, Object payload) {
        AllReturnToggle.withNetworkBypass(() -> {
            codec.encode((B) buffer, (T) payload);
            return null;
        });
    }

    // 解码完整执行；后续消息处理器不在豁免范围内。
    @SuppressWarnings("unchecked")
    @Redirect(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
    private <B, T> Object eca$decode(StreamCodec<B, T> codec, Object buffer) {
        return AllReturnToggle.withNetworkBypass(() -> codec.decode((B) buffer));
    }
}
