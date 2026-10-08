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

import java.util.UUID;

/**
 * Client-side container repair packet.
 * Sent from server to client when a tracked entity still exists on the client but has
 * been dropped from part of the client containers, telling the client to re-register
 * that same instance instead of waiting for a spawn packet it will never receive.
 */
public class ClientReviveContainersPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientReviveContainersPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "client_revive_containers_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientReviveContainersPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> ClientReviveContainersPacket.encode(msg, buf), ClientReviveContainersPacket::decode);

    @Override
    public CustomPacketPayload.Type<ClientReviveContainersPacket> type() {
        return TYPE;
    }


    private final UUID entityUuid;

    public ClientReviveContainersPacket(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    /**
     * Encode the packet to buffer.
     * @param msg the packet to encode
     * @param buf the buffer to write to
     */
    public static void encode(ClientReviveContainersPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.entityUuid);
    }

    /**
     * Decode the packet from buffer.
     * @param buf the buffer to read from
     * @return the decoded packet
     */
    public static ClientReviveContainersPacket decode(FriendlyByteBuf buf) {
        return new ClientReviveContainersPacket(buf.readUUID());
    }

    /**
     * Handle the packet on client side.
     * @param msg the packet to handle
     * @param ctx the network context
     */
    public static void handle(ClientReviveContainersPacket msg, IPayloadContext context) {
        context.enqueueWork(() ->
                { if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.apply(msg); });
    }

    // 客户端引用隔离在独立内部类中，实际逻辑委托给 @OnlyIn(Dist.CLIENT) 的 ClientEntityUtil
    private static final class ClientHandlerRef {
        static void apply(ClientReviveContainersPacket msg) {
            ClientEntityUtil.handleReviveContainers(msg.entityUuid);
        }
    }
}
