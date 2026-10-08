package net.eca.network;


import net.eca.client.gui.BossShowEditorHomeScreen;
import net.eca.util.bossshow.BossShowDefinition;
import net.eca.util.bossshow.BossShowDefinition.Frame;
import net.eca.util.bossshow.BossShowDefinition.EventCue;
import net.eca.util.bossshow.BossShowDefinition.SubtitleCue;
import net.eca.util.bossshow.BossShowNetCodec;
import net.eca.util.bossshow.BossShowEffectCue;
import net.eca.util.bossshow.Trigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

//S→C：打开 BossShow 编辑器 Home 界面，携带服务端当前所有定义的完整数据
public class BossShowOpenEditorHomePacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowOpenEditorHomePacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "boss_show_open_editor_home_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowOpenEditorHomePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowOpenEditorHomePacket.encode(msg, buf), BossShowOpenEditorHomePacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowOpenEditorHomePacket> type() {
        return TYPE;
    }


    private final List<BossShowDefinition> definitions;

    public BossShowOpenEditorHomePacket(Collection<BossShowDefinition> defs) {
        this.definitions = new ArrayList<>(defs);
    }

    public static void encode(BossShowOpenEditorHomePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.definitions.size());
        for (BossShowDefinition def : msg.definitions) {
            buf.writeResourceLocation(def.id());
            Identifier typeId = def.targetType() != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(def.targetType())
                : null;
            BossShowNetCodec.writeNullableRL(buf, typeId);
            BossShowNetCodec.writeTrigger(buf, def.trigger());
            buf.writeBoolean(def.cinematic());
            buf.writeBoolean(def.allowRepeat());
            BossShowNetCodec.writeFrames(buf, def.frames());
            buf.writeFloat(def.anchorYawDeg());
            BossShowNetCodec.writeEventCues(buf, def.eventCues());
            BossShowNetCodec.writeSubtitleCues(buf, def.subtitleCues());
            BossShowNetCodec.writeEffectCues(buf, def.effectCues());
        }
    }

    public static BossShowOpenEditorHomePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<BossShowDefinition> defs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Identifier id = buf.readResourceLocation();
            Identifier typeId = BossShowNetCodec.readNullableRL(buf);
            Trigger trig = BossShowNetCodec.readTrigger(buf);
            boolean cine = buf.readBoolean();
            boolean allowRepeat = buf.readBoolean();
            List<Frame> frames = BossShowNetCodec.readFrames(buf);
            float yaw = buf.readFloat();
            List<EventCue> eventCues = BossShowNetCodec.readEventCues(buf);
            List<SubtitleCue> subtitleCues = BossShowNetCodec.readSubtitleCues(buf);
            List<BossShowEffectCue> effectCues = BossShowNetCodec.readEffectCues(buf);
            EntityType<?> type = (typeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(typeId))
                ? BuiltInRegistries.ENTITY_TYPE.getValue(typeId)
                : null;
            defs.add(new BossShowDefinition(id, type, trig, cine, allowRepeat, frames,
                BossShowDefinition.Source.CONFIG, yaw, eventCues, subtitleCues, effectCues));
        }
        return new BossShowOpenEditorHomePacket(defs);
    }

    public static void handle(BossShowOpenEditorHomePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (FMLEnvironment.getDist() == Dist.CLIENT) ClientHandlerRef.onOpen(msg); });
    }

    public List<BossShowDefinition> definitions() {
        return Collections.unmodifiableList(definitions);
    }

    private static final class ClientHandlerRef {
        static void onOpen(BossShowOpenEditorHomePacket msg) {
            BossShowEditorHomeScreen.openFromPacket(msg);
        }
    }
}
