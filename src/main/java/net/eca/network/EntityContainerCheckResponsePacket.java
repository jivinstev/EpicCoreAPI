package net.eca.network;

import net.eca.util.EntityUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class EntityContainerCheckResponsePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EntityContainerCheckResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "entity_container_check_response_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityContainerCheckResponsePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> EntityContainerCheckResponsePacket.encode(msg, buf), EntityContainerCheckResponsePacket::decode);

    @Override
    public CustomPacketPayload.Type<EntityContainerCheckResponsePacket> type() {
        return TYPE;
    }


    private final UUID requestId;
    private final UUID entityUuid;
    private final Map<String, Boolean> result;

    public EntityContainerCheckResponsePacket(UUID requestId, UUID entityUuid, Map<String, Boolean> result) {
        this.requestId = requestId;
        this.entityUuid = entityUuid;
        this.result = result;
    }

    public static void encode(EntityContainerCheckResponsePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.requestId);
        buf.writeUUID(msg.entityUuid);
        buf.writeVarInt(msg.result.size());
        for (Map.Entry<String, Boolean> entry : msg.result.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeBoolean(entry.getValue());
        }
    }

    public static EntityContainerCheckResponsePacket decode(FriendlyByteBuf buf) {
        UUID requestId = buf.readUUID();
        UUID entityUuid = buf.readUUID();
        int size = buf.readVarInt();
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            result.put(buf.readUtf(), buf.readBoolean());
        }
        return new EntityContainerCheckResponsePacket(requestId, entityUuid, result);
    }

    public static void handle(EntityContainerCheckResponsePacket msg, IPayloadContext ctx) {
        EntityUtil.completeClientContainerCheck(msg.requestId, msg.entityUuid, msg.result);
    }
}
