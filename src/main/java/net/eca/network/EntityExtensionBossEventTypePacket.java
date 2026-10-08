package net.eca.network;

import net.eca.util.entity_extension.EntityExtensionClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class EntityExtensionBossEventTypePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityExtensionBossEventTypePacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "entity_extension_boss_event_type_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityExtensionBossEventTypePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityExtensionBossEventTypePacket.encode(msg, buf), EntityExtensionBossEventTypePacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityExtensionBossEventTypePacket> type() {
        return TYPE;
    }


    private final UUID bossEventId;
    private final Identifier typeId;
    private final UUID entityUuid;

    public EntityExtensionBossEventTypePacket(UUID bossEventId, Identifier typeId, UUID entityUuid) {
        this.bossEventId = bossEventId;
        this.typeId = typeId;
        this.entityUuid = entityUuid;
    }

    public static void encode(EntityExtensionBossEventTypePacket message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.bossEventId);
        buffer.writeBoolean(message.typeId != null);
        if (message.typeId != null) {
            buffer.writeIdentifier(message.typeId);
        }
        buffer.writeBoolean(message.entityUuid != null);
        if (message.entityUuid != null) {
            buffer.writeUUID(message.entityUuid);
        }
    }

    public static EntityExtensionBossEventTypePacket decode(FriendlyByteBuf buffer) {
        UUID bossEventId = buffer.readUUID();
        boolean hasType = buffer.readBoolean();
        Identifier typeId = hasType ? buffer.readIdentifier() : null;
        boolean hasEntityUuid = buffer.readBoolean();
        UUID entityUuid = hasEntityUuid ? buffer.readUUID() : null;
        return new EntityExtensionBossEventTypePacket(bossEventId, typeId, entityUuid);
    }

    public static void handle(EntityExtensionBossEventTypePacket message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            EntityExtensionClientState.setBossEventType(message.bossEventId, message.typeId, message.entityUuid);
        });
    }
}
