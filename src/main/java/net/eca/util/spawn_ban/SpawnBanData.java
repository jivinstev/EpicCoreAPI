package net.eca.util.spawn_ban;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// 禁生成数据存储
public class SpawnBanData extends SavedData {

    private static final String DATA_NAME = "eca_spawn_bans";
    private static final String NBT_BANS = "bans";

    // Adapter over CompoundTag.CODEC: hand-written load/save keep the on-disk format and defensive behaviour
    private static final Codec<SpawnBanData> CODEC = CompoundTag.CODEC.xmap(
        SpawnBanData::load,
        data -> data.save(new CompoundTag())
    );
    private static final SavedDataType<SpawnBanData> TYPE = new SavedDataType<>(
        Identifier.withDefaultNamespace(DATA_NAME),
        SpawnBanData::new,
        CODEC
    );

    private final Map<Identifier, Integer> bans = new HashMap<>();
    private final Set<EntityType<?>> bannedTypes = Collections.newSetFromMap(new IdentityHashMap<>());

    public SpawnBanData() {
        // Default constructor
    }

    public static SpawnBanData load(CompoundTag tag) {
        SpawnBanData data = new SpawnBanData();

        if (tag.contains(NBT_BANS)) {
            CompoundTag bansTag = tag.getCompoundOrEmpty(NBT_BANS);
            for (String key : bansTag.keySet()) {
                Identifier typeId = Identifier.tryParse(key);
                if (typeId != null) {
                    int seconds = bansTag.getIntOr(key, 0);
                    if (seconds > 0) {
                        data.bans.put(typeId, seconds);
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(typeId);
                        if (type != null) {
                            data.bannedTypes.add(type);
                        }
                    }
                }
            }
        }

        return data;
    }

    public CompoundTag save(CompoundTag tag) {
        CompoundTag bansTag = new CompoundTag();
        for (Map.Entry<Identifier, Integer> entry : bans.entrySet()) {
            bansTag.putInt(entry.getKey().toString(), entry.getValue());
        }
        tag.put(NBT_BANS, bansTag);
        return tag;
    }

    public static SpawnBanData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void addBan(Identifier typeId, EntityType<?> type, int seconds) {
        if (typeId == null || type == null || seconds <= 0) return;
        bans.put(typeId, seconds);
        bannedTypes.add(type);
        setDirty();
    }

    public boolean removeBan(Identifier typeId, EntityType<?> type) {
        if (typeId == null || type == null) return false;
        boolean removed = bans.remove(typeId) != null;
        if (removed) {
            bannedTypes.remove(type);
            setDirty();
        }
        return removed;
    }

    public boolean hasAnyBans() {
        return !bans.isEmpty();
    }

    public boolean hasBan(EntityType<?> type) {
        return type != null && bannedTypes.contains(type);
    }

    public boolean hasBan(Identifier typeId) {
        if (typeId == null) return false;
        Integer time = bans.get(typeId);
        return time != null && time > 0;
    }

    public int getTime(Identifier typeId) {
        if (typeId == null) return 0;
        return bans.getOrDefault(typeId, 0);
    }

    public Map<Identifier, Integer> getAllBans() {
        return Collections.unmodifiableMap(new HashMap<>(bans));
    }

    public void tick() {
        if (bans.isEmpty()) return;

        boolean modified = false;
        Iterator<Map.Entry<Identifier, Integer>> iterator = bans.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Identifier, Integer> entry = iterator.next();
            int newTime = entry.getValue() - 1;

            if (newTime <= 0) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(entry.getKey());
                if (type != null) {
                    bannedTypes.remove(type);
                }
                iterator.remove();
                modified = true;
            } else {
                entry.setValue(newTime);
                modified = true;
            }
        }

        if (modified) {
            setDirty();
        }
    }

    public void clearAll() {
        if (!bans.isEmpty()) {
            bans.clear();
            bannedTypes.clear();
            setDirty();
        }
    }
}
