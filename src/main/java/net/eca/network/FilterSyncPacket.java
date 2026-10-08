package net.eca.network;

import net.eca.client.render.shader.FilterRenderer;
import net.eca.util.filter.FilterType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;


public class FilterSyncPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FilterSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "filter_sync_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> FilterSyncPacket.encode(msg, buf), FilterSyncPacket::decode);

    @Override
    public CustomPacketPayload.Type<FilterSyncPacket> type() {
        return TYPE;
    }


    private final int filterOrdinal;
    private final boolean enable;

    public FilterSyncPacket(FilterType filter, boolean enable) {
        this.filterOrdinal = filter.ordinal();
        this.enable = enable;
    }

    private FilterSyncPacket(int filterOrdinal, boolean enable) {
        this.filterOrdinal = filterOrdinal;
        this.enable = enable;
    }

    public static void encode(FilterSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.filterOrdinal);
        buf.writeBoolean(msg.enable);
    }

    public static FilterSyncPacket decode(FriendlyByteBuf buf) {
        return new FilterSyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(FilterSyncPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow() != PacketFlow.CLIENTBOUND) {
                return;
            }
            if (FMLEnvironment.dist == Dist.CLIENT) ClientHandlerRef.onSync(msg);
        });
    }

    private static final class ClientHandlerRef {
        static void onSync(FilterSyncPacket msg) {
            FilterType[] types = FilterType.values();
            if (msg.filterOrdinal < 0 || msg.filterOrdinal >= types.length) {
                return;
            }
            FilterType filter = types[msg.filterOrdinal];
            if (msg.enable) {
                FilterRenderer.enable(filter);
            } else {
                FilterRenderer.disable(filter);
            }
        }
    }
}
