package net.eca.network;

import net.eca.client.ClientEntityUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public final class ShaderGeneratorOpenPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ShaderGeneratorOpenPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "shader_generator_open_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShaderGeneratorOpenPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> ShaderGeneratorOpenPacket.encode(msg, buf), ShaderGeneratorOpenPacket::decode);

    @Override
    public CustomPacketPayload.Type<ShaderGeneratorOpenPacket> type() {
        return TYPE;
    }


    public static void encode(ShaderGeneratorOpenPacket message, FriendlyByteBuf buffer) {
    }

    public static ShaderGeneratorOpenPacket decode(FriendlyByteBuf buffer) {
        return new ShaderGeneratorOpenPacket();
    }

    public static void handle(ShaderGeneratorOpenPacket message, IPayloadContext context) {
        context.enqueueWork(() -> { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.open(); });
    }

    // 客户端引用委托给 @OnlyIn(Dist.CLIENT) 的 ClientEntityUtil
    private static final class ClientHandlerRef {
        static void open() {
            ClientEntityUtil.openShaderGeneratorScreen();
        }
    }
}
