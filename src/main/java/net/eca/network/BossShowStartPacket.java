package net.eca.network;


import net.eca.util.bossshow.BossShowClientState;
import net.eca.util.bossshow.BossShowDefinition;
import net.eca.util.bossshow.BossShowDefinition.Frame;
import net.eca.util.bossshow.BossShowDefinition.EventCue;
import net.eca.util.bossshow.BossShowDefinition.SubtitleCue;
import net.eca.util.bossshow.BossShowEffectCue;
import net.eca.util.bossshow.BossShowNetCodec;
import net.eca.util.bossshow.Trigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

//S→C：开始播放一个 BossShow 演出
public class BossShowStartPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowStartPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("eca", "boss_show_start_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowStartPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowStartPacket.encode(msg, buf), BossShowStartPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowStartPacket> type() {
        return TYPE;
    }


    private final Identifier cutsceneId;
    private final Identifier targetTypeId;
    private final UUID targetUuid;
    private final double anchorX, anchorY, anchorZ;
    private final float anchorYaw;
    private final String triggerType;
    private final double triggerRadius;
    private final boolean cinematic;
    private final List<Frame> frames;
    private final List<EventCue> eventCues;
    private final List<SubtitleCue> subtitleCues;
    private final List<BossShowEffectCue> effectCues;

    public BossShowStartPacket(BossShowDefinition def, UUID targetUuid, double anchorX, double anchorY, double anchorZ, float anchorYaw) {
        this.cutsceneId = def.id();
        this.targetTypeId = def.targetType() != null
            ? BuiltInRegistries.ENTITY_TYPE.getKey(def.targetType())
            : null;
        this.targetUuid = targetUuid;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.anchorYaw = anchorYaw;
        this.triggerType = def.trigger().type();
        this.triggerRadius = def.trigger() instanceof Trigger.Range r ? r.effectRadius() : 0.0;
        this.cinematic = def.cinematic();
        this.frames = def.frames();
        this.eventCues = def.eventCues();
        this.subtitleCues = def.subtitleCues();
        this.effectCues = def.effectCues();
    }

    private BossShowStartPacket(Identifier cutsceneId, Identifier targetTypeId, UUID targetUuid,
                                double anchorX, double anchorY, double anchorZ, float anchorYaw,
                                String triggerType, double triggerRadius, boolean cinematic,
                                List<Frame> frames, List<EventCue> eventCues, List<SubtitleCue> subtitleCues,
                                List<BossShowEffectCue> effectCues) {
        this.cutsceneId = cutsceneId;
        this.targetTypeId = targetTypeId;
        this.targetUuid = targetUuid;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.anchorYaw = anchorYaw;
        this.triggerType = triggerType;
        this.triggerRadius = triggerRadius;
        this.cinematic = cinematic;
        this.frames = frames;
        this.eventCues = eventCues;
        this.subtitleCues = subtitleCues;
        this.effectCues = effectCues;
    }

    public static void encode(BossShowStartPacket msg, FriendlyByteBuf buf) {
        buf.writeIdentifier(msg.cutsceneId);
        BossShowNetCodec.writeNullableRL(buf, msg.targetTypeId);
        buf.writeUUID(msg.targetUuid);
        buf.writeDouble(msg.anchorX);
        buf.writeDouble(msg.anchorY);
        buf.writeDouble(msg.anchorZ);
        buf.writeFloat(msg.anchorYaw);
        buf.writeUtf(msg.triggerType);
        buf.writeDouble(msg.triggerRadius);
        buf.writeBoolean(msg.cinematic);
        BossShowNetCodec.writeFrames(buf, msg.frames);
        BossShowNetCodec.writeEventCues(buf, msg.eventCues);
        BossShowNetCodec.writeSubtitleCues(buf, msg.subtitleCues);
        BossShowNetCodec.writeEffectCues(buf, msg.effectCues);
    }

    public static BossShowStartPacket decode(FriendlyByteBuf buf) {
        Identifier cutsceneId = buf.readIdentifier();
        Identifier typeId = BossShowNetCodec.readNullableRL(buf);
        UUID uuid = buf.readUUID();
        double ax = buf.readDouble();
        double ay = buf.readDouble();
        double az = buf.readDouble();
        float yaw = buf.readFloat();
        String trigType = buf.readUtf(64);
        double trigRadius = buf.readDouble();
        boolean cine = buf.readBoolean();
        List<Frame> frames = BossShowNetCodec.readFrames(buf);
        List<EventCue> eventCues = BossShowNetCodec.readEventCues(buf);
        List<SubtitleCue> subtitleCues = BossShowNetCodec.readSubtitleCues(buf);
        List<BossShowEffectCue> effectCues = BossShowNetCodec.readEffectCues(buf);
        return new BossShowStartPacket(cutsceneId, typeId, uuid, ax, ay, az, yaw, trigType, trigRadius,
            cine, frames, eventCues, subtitleCues, effectCues);
    }

    public static void handle(BossShowStartPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { if (FMLEnvironment.getDist() == Dist.CLIENT) ClientHandlerRef.onStart(msg); });
    }

    public Identifier cutsceneId() { return cutsceneId; }
    public Identifier targetTypeId() { return targetTypeId; }
    public UUID targetUuid() { return targetUuid; }
    public double anchorX() { return anchorX; }
    public double anchorY() { return anchorY; }
    public double anchorZ() { return anchorZ; }
    public float anchorYaw() { return anchorYaw; }
    public String triggerType() { return triggerType; }
    public double triggerRadius() { return triggerRadius; }
    public boolean cinematic() { return cinematic; }
    public List<Frame> frames() { return Collections.unmodifiableList(frames); }
    public List<EventCue> eventCues() { return Collections.unmodifiableList(eventCues); }
    public List<SubtitleCue> subtitleCues() { return Collections.unmodifiableList(subtitleCues); }
    public List<BossShowEffectCue> effectCues() { return Collections.unmodifiableList(effectCues); }

    private static final class ClientHandlerRef {
        static void onStart(BossShowStartPacket msg) {
            BossShowClientState.onServerStart(msg);
        }
    }
}
