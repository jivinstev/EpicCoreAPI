package net.eca.network;

import net.eca.util.entity_extension.EntityExtensionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class EntityExtensionActiveTypePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityExtensionActiveTypePacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "entity_extension_active_type_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityExtensionActiveTypePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityExtensionActiveTypePacket.encode(msg, buf), EntityExtensionActiveTypePacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityExtensionActiveTypePacket> type() {
        return TYPE;
    }


    private final Identifier dimensionId;
    private final Identifier typeId;

    public EntityExtensionActiveTypePacket(Identifier dimensionId, Identifier typeId) {
        this.dimensionId = dimensionId;
        this.typeId = typeId;
    }

    public static void encode(EntityExtensionActiveTypePacket message, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(message.dimensionId);
        buffer.writeBoolean(message.typeId != null);
        if (message.typeId != null) {
            buffer.writeResourceLocation(message.typeId);
        }
    }

    public static EntityExtensionActiveTypePacket decode(FriendlyByteBuf buffer) {
        Identifier dimensionId = buffer.readResourceLocation();
        boolean hasType = buffer.readBoolean();
        Identifier typeId = hasType ? buffer.readResourceLocation() : null;
        return new EntityExtensionActiveTypePacket(dimensionId, typeId);
    }

    public static void handle(EntityExtensionActiveTypePacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> EntityExtensionClientState.setActiveType(message.dimensionId, message.typeId));
    }
}
