package net.eca.network;

import net.minecraft.core.registries.Registries;

import net.eca.util.bossshow.BossShowDefinition;
import net.eca.util.bossshow.BossShowDefinition.Frame;
import net.eca.util.bossshow.BossShowDefinition.EventCue;
import net.eca.util.bossshow.BossShowDefinition.SubtitleCue;
import net.eca.util.bossshow.BossShowManager;
import net.eca.util.bossshow.BossShowNetCodec;
import net.eca.util.bossshow.BossShowEffectCue;
import net.eca.util.bossshow.Trigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.ArrayList;

//C→S：客户端把当前编辑中的 BossShow 定义提交保存
public class BossShowSaveEditorPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BossShowSaveEditorPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("eca", "boss_show_save_editor_packet"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossShowSaveEditorPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> BossShowSaveEditorPacket.encode(msg, buf), BossShowSaveEditorPacket::decode);

    @Override
    public CustomPacketPayload.Type<BossShowSaveEditorPacket> type() {
        return TYPE;
    }


    private final ResourceLocation cutsceneId;
    private final ResourceLocation targetTypeId;
    private final Trigger trigger;
    private final boolean cinematic;
    private final boolean allowRepeat;
    private final List<Frame> frames;
    private final List<EventCue> eventCues;
    private final List<SubtitleCue> subtitleCues;
    private final List<BossShowEffectCue> effectCues;
    private final float anchorYawDeg;

    public BossShowSaveEditorPacket(ResourceLocation cutsceneId, ResourceLocation targetTypeId,
                                    Trigger trigger, boolean cinematic, boolean allowRepeat,
                                    List<Frame> frames, float anchorYawDeg) {
        this(cutsceneId, targetTypeId, trigger, cinematic, allowRepeat, frames, anchorYawDeg,
            deriveEventCues(frames), deriveSubtitleCues(frames), List.of());
    }

    public BossShowSaveEditorPacket(ResourceLocation cutsceneId, ResourceLocation targetTypeId,
                                    Trigger trigger, boolean cinematic, boolean allowRepeat,
                                    List<Frame> frames, float anchorYawDeg,
                                    List<EventCue> eventCues, List<SubtitleCue> subtitleCues,
                                    List<BossShowEffectCue> effectCues) {
        this.cutsceneId = cutsceneId;
        this.targetTypeId = targetTypeId;
        this.trigger = trigger;
        this.cinematic = cinematic;
        this.allowRepeat = allowRepeat;
        this.frames = frames;
        this.anchorYawDeg = anchorYawDeg;
        this.eventCues = eventCues;
        this.subtitleCues = subtitleCues;
        this.effectCues = effectCues;
    }

    public static void encode(BossShowSaveEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.cutsceneId);
        BossShowNetCodec.writeNullableRL(buf, msg.targetTypeId);
        BossShowNetCodec.writeTrigger(buf, msg.trigger);
        buf.writeBoolean(msg.cinematic);
        buf.writeBoolean(msg.allowRepeat);
        BossShowNetCodec.writeFrames(buf, msg.frames);
        buf.writeFloat(msg.anchorYawDeg);
        BossShowNetCodec.writeEventCues(buf, msg.eventCues);
        BossShowNetCodec.writeSubtitleCues(buf, msg.subtitleCues);
        BossShowNetCodec.writeEffectCues(buf, msg.effectCues);
    }

    public static BossShowSaveEditorPacket decode(FriendlyByteBuf buf) {
        ResourceLocation cutsceneId = buf.readResourceLocation();
        ResourceLocation typeId = BossShowNetCodec.readNullableRL(buf);
        Trigger trigger = BossShowNetCodec.readTrigger(buf);
        boolean cine = buf.readBoolean();
        boolean allowRepeat = buf.readBoolean();
        List<Frame> frames = BossShowNetCodec.readFrames(buf);
        float yaw = buf.readFloat();
        List<EventCue> eventCues = BossShowNetCodec.readEventCues(buf);
        List<SubtitleCue> subtitleCues = BossShowNetCodec.readSubtitleCues(buf);
        List<BossShowEffectCue> effectCues = BossShowNetCodec.readEffectCues(buf);
        return new BossShowSaveEditorPacket(cutsceneId, typeId, trigger, cine, allowRepeat, frames, yaw,
            eventCues, subtitleCues, effectCues);
    }

    public static void handle(BossShowSaveEditorPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ((ServerPlayer) ctx.player());
            if (player == null) return;
            EntityType<?> type = (msg.targetTypeId != null && BuiltInRegistries.ENTITY_TYPE.containsKey(msg.targetTypeId))
                ? BuiltInRegistries.ENTITY_TYPE.get(msg.targetTypeId)
                : null;
            BossShowDefinition def = new BossShowDefinition(
                msg.cutsceneId, type, msg.trigger, msg.cinematic, msg.allowRepeat,
                msg.frames, BossShowDefinition.Source.CONFIG, msg.anchorYawDeg,
                msg.eventCues, msg.subtitleCues, msg.effectCues);
            boolean ok = BossShowManager.save(def);
            if (ok) {
                player.sendSystemMessage(Component.literal("§aSaved BossShow " + msg.cutsceneId));
            } else {
                player.sendSystemMessage(Component.literal("§cFailed to save BossShow " + msg.cutsceneId + " (see server log)"));
            }
        });
    }

    private static List<EventCue> deriveEventCues(List<Frame> frames) {
        List<EventCue> result = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).keyframe() != null && frames.get(i).keyframe().eventId() != null) {
                result.add(new EventCue(i, frames.get(i).keyframe().eventId()));
            }
        }
        return result;
    }

    private static List<SubtitleCue> deriveSubtitleCues(List<Frame> frames) {
        List<SubtitleCue> result = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).keyframe() != null && frames.get(i).keyframe().subtitleText() != null) {
                result.add(new SubtitleCue(i, frames.get(i).keyframe().subtitleText()));
            }
        }
        return result;
    }
}
