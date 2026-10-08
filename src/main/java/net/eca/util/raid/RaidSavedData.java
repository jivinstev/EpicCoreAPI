package net.eca.util.raid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/*
 * 袭击持久化 — 每个维度存自己的活跃袭击。
 *
 * 与 FactionSavedData 的区别：袭击是维度局部的，不跨维度共享，
 * 因此直接使用所在 ServerLevel 的 DataStorage 而非统一存储。
 */
public class RaidSavedData extends SavedData {

    private static final String DATA_NAME = "eca_raids";
    private static final String NBT_RAIDS = "raids";
    private static final String NBT_NEXT_ID = "nextId";

    private static final SavedDataType<RaidSavedData> TYPE = new SavedDataType<>(
            Identifier.withDefaultNamespace(DATA_NAME),
            RaidSavedData::new,
            CompoundTag.CODEC.xmap(RaidSavedData::load, d -> d.save(new CompoundTag()))
    );

    private final List<CompoundTag> raidTags = new ArrayList<>();
    private int nextId = 1;

    public RaidSavedData() {}

    public static RaidSavedData load(CompoundTag tag) {
        RaidSavedData data = new RaidSavedData();
        data.nextId = Math.max(1, tag.getIntOr(NBT_NEXT_ID, 0));

        ListTag list = tag.getListOrEmpty(NBT_RAIDS);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raidTag = list.getCompoundOrEmpty(i);
            if (!raidTag.isEmpty()) {
                data.raidTags.add(raidTag);
            }
        }
        return data;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt(NBT_NEXT_ID, nextId);

        ListTag list = new ListTag();
        for (CompoundTag raidTag : raidTags) {
            list.add(raidTag.copy());
        }
        if (!list.isEmpty()) {
            tag.put(NBT_RAIDS, list);
        }
        return tag;
    }

    public static RaidSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    // 分配下一个袭击 ID
    int nextRaidId() {
        int id = nextId++;
        setDirty();
        return id;
    }

    // 反序列化全部袭击实例
    List<RaidInstance> loadRaids() {
        List<RaidInstance> result = new ArrayList<>();
        for (CompoundTag raidTag : raidTags) {
            RaidInstance raid = RaidInstance.load(raidTag);
            if (raid != null) {
                result.add(raid);
            }
        }
        return result;
    }

    // 全量写入当前活跃袭击
    void storeRaids(Collection<RaidInstance> raids) {
        raidTags.clear();
        for (RaidInstance raid : raids) {
            raidTags.add(raid.save(new CompoundTag()));
        }
        setDirty();
    }
}
