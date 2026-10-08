package net.eca.util.reflect;

import java.util.HashMap;
import java.util.Map;

// 混淆映射表 - 存储字段和方法的混淆名对应关系
/**
 * Obfuscation mapping registry for Minecraft field and method names.
 * This class only stores mappings, no reflection operations.
 */
public final class ObfuscationMapping {

    public static final String MINECRAFT_VERSION = "1.20.1";
    public static final String FORGE_VERSION = "47.2.0";

    private static final String CURRENT_VERSION = MINECRAFT_VERSION + "-" + FORGE_VERSION;

    // 字段映射表: 版本 -> 字段标识 -> 混淆名
    private static final Map<String, Map<String, String>> FIELD_MAPPINGS = new HashMap<>();

    // 方法映射表: 版本 -> 方法标识 -> 混淆名
    private static final Map<String, Map<String, String>> METHOD_MAPPINGS = new HashMap<>();

    static {
        initFieldMappings();
        initMethodMappings();
    }

    // 初始化字段映射
    private static void initFieldMappings() {
        Map<String, String> fields = new HashMap<>();

        // Entity
        fields.put("Entity.removalReason", "removalReason");
        fields.put("Entity.levelCallback", "levelCallback");

        // LivingEntity teleport interpolation
        fields.put("LivingEntity.lerpSteps", "lerpSteps");
        fields.put("LivingEntity.lerpX", "lerpX");
        fields.put("LivingEntity.lerpY", "lerpY");
        fields.put("LivingEntity.lerpZ", "lerpZ");
        fields.put("LivingEntity.lerpYRot", "lerpYRot");
        fields.put("LivingEntity.lerpXRot", "lerpXRot");

        // Boat teleport interpolation
        fields.put("Boat.lerpSteps", "lerpSteps");
        fields.put("Boat.lerpX", "lerpX");
        fields.put("Boat.lerpY", "lerpY");
        fields.put("Boat.lerpZ", "lerpZ");
        fields.put("Boat.lerpYRot", "lerpYRot");
        fields.put("Boat.lerpXRot", "lerpXRot");

        // AbstractMinecart teleport interpolation
        fields.put("AbstractMinecart.lSteps", "lSteps");
        fields.put("AbstractMinecart.lx", "lx");
        fields.put("AbstractMinecart.ly", "ly");
        fields.put("AbstractMinecart.lz", "lz");
        fields.put("AbstractMinecart.lyr", "lyr");
        fields.put("AbstractMinecart.lxr", "lxr");

        // ServerLevel
        fields.put("ServerLevel.players", "players");
        fields.put("ServerLevel.chunkSource", "chunkSource");
        fields.put("ServerLevel.entityTickList", "entityTickList");
        fields.put("ServerLevel.entityManager", "entityManager");
        fields.put("ServerLevel.navigatingMobs", "navigatingMobs");

        // DimensionDataStorage
        fields.put("DimensionDataStorage.cache", "cache");

        // EntityTickList
        fields.put("EntityTickList.active", "active");
        fields.put("EntityTickList.passive", "passive");
        fields.put("EntityTickList.iterated", "iterated");

        // ServerChunkCache
        fields.put("ServerChunkCache.chunkMap", "chunkMap");

        // ChunkMap
        fields.put("ChunkMap.entityMap", "entityMap");
        fields.put("ChunkMap.updatingChunkMap", "updatingChunkMap");
        fields.put("ChunkMap.pendingUnloads", "pendingUnloads");
        fields.put("ChunkMap.entitiesInLevel", "entitiesInLevel");
        fields.put("ChunkMap.TrackedEntity.seenBy", "seenBy");

        // PersistentEntitySectionManager
        fields.put("PersistentEntitySectionManager.visibleEntityStorage", "visibleEntityStorage");
        fields.put("PersistentEntitySectionManager.knownUuids", "knownUuids");
        fields.put("PersistentEntitySectionManager.sectionStorage", "sectionStorage");
        fields.put("PersistentEntitySectionManager.callbacks", "callbacks");
        fields.put("PersistentEntitySectionManager.loadingInbox", "loadingInbox");
        fields.put("PersistentEntitySectionManager.chunkVisibility", "chunkVisibility");
        fields.put("PersistentEntitySectionManager.chunksToUnload", "chunksToUnload");
        fields.put("PersistentEntitySectionManager.chunkLoadStatuses", "chunkLoadStatuses");

        // EntityLookup
        fields.put("EntityLookup.byUuid", "byUuid");
        fields.put("EntityLookup.byId", "byId");

        // EntitySectionStorage
        fields.put("EntitySectionStorage.sections", "sections");
        fields.put("EntitySectionStorage.intialSectionVisibility", "intialSectionVisibility");
        fields.put("EntitySectionStorage.sectionIds", "sectionIds");

        // EntitySection
        fields.put("EntitySection.storage", "storage");

        // ClassInstanceMultiMap
        fields.put("ClassInstanceMultiMap.byClass", "byClass");
        fields.put("ClassInstanceMultiMap.allInstances", "allInstances");

        // ClientLevel
        fields.put("ClientLevel.tickingEntities", "tickingEntities");
        fields.put("ClientLevel.entityStorage", "entityStorage");
        fields.put("ClientLevel.players", "players");

        // TransientEntitySectionManager
        fields.put("TransientEntitySectionManager.entityStorage", "entityStorage");
        fields.put("TransientEntitySectionManager.sectionStorage", "sectionStorage");

        FIELD_MAPPINGS.put(CURRENT_VERSION, fields);
    }

    // 初始化方法映射
    private static void initMethodMappings() {
        Map<String, String> methods = new HashMap<>();

        // LivingEntity
        methods.put("LivingEntity.dropAllDeathLoot", "dropAllDeathLoot");
        methods.put("LivingEntity.getRecordMaxHp", "getMaxHealth");
        methods.put("LivingEntity.getHealth", "getHealth");
        methods.put("LivingEntity.getMaxHealth", "getMaxHealth");
        methods.put("LivingEntity.actuallyHurt", "actuallyHurt");
        methods.put("LivingEntity.hurt", "hurt");
        methods.put("LivingEntity.setHealth", "setHealth");
        methods.put("LivingEntity.isDeadOrDying", "isDeadOrDying");
        methods.put("LivingEntity.isAlive", "isAlive");
        methods.put("LivingEntity.aiStep", "aiStep");
        methods.put("LivingEntity.tickDeath", "tickDeath");

        // Entity
        methods.put("Entity.getId", "getId");
        methods.put("Entity.getUUID", "getUUID");
        methods.put("Entity.level", "level");
        methods.put("Entity.setRemoved", "setRemoved");
        methods.put("Entity.tick", "tick");
        methods.put("Entity.baseTick", "baseTick");
        methods.put("Entity.onSyncedDataUpdated", "onSyncedDataUpdated");
        methods.put("Entity.positionRider", "positionRider");

        // Entity storage
        methods.put("EntityLookup.add", "add");
        methods.put("EntitySection.add", "add");
        methods.put("EntityTickList.add", "add");
        methods.put("PersistentEntitySectionManager.addEntity", "addEntity");
        methods.put("PersistentEntitySectionManager.addNewEntity", "addNewEntity");
        methods.put("TransientEntitySectionManager.addEntity", "addEntity");
        methods.put("ChunkMap.addEntity", "addEntity");
        methods.put("ServerLevel.addEntity", "addEntity");
        methods.put("ServerLevel.addFreshEntity", "addFreshEntity");
        methods.put("ServerLevel.addWithUUID", "addWithUUID");
        methods.put("ServerLevel.addDuringTeleport", "addDuringTeleport");

        // CompoundTag
        methods.put("CompoundTag.getBoolean", "getBoolean");
        methods.put("CompoundTag.getByte", "getByte");
        methods.put("CompoundTag.getShort", "getShort");
        methods.put("CompoundTag.getInt", "getInt");
        methods.put("CompoundTag.getLong", "getLong");
        methods.put("CompoundTag.getFloat", "getFloat");
        methods.put("CompoundTag.getDouble", "getDouble");
        methods.put("CompoundTag.getString", "getString");
        methods.put("CompoundTag.putBoolean", "putBoolean");
        methods.put("CompoundTag.putByte", "putByte");
        methods.put("CompoundTag.putShort", "putShort");
        methods.put("CompoundTag.putInt", "putInt");
        methods.put("CompoundTag.putLong", "putLong");
        methods.put("CompoundTag.putFloat", "putFloat");
        methods.put("CompoundTag.putDouble", "putDouble");
        methods.put("CompoundTag.putString", "putString");

        // Mth
        methods.put("Mth.clampInt", "clamp");
        methods.put("Mth.clampFloat", "clamp");
        methods.put("Mth.clampDouble", "clamp");

        METHOD_MAPPINGS.put(CURRENT_VERSION, methods);
    }

    // 获取字段的混淆名
    /**
     * Get the obfuscated field name for the given key.
     * @param fieldKey the field mapping key like "Entity.entityData"
     * @return the obfuscated field name, or null if not found
     */
    public static String getFieldMapping(String fieldKey) {
        Map<String, String> mappings = FIELD_MAPPINGS.get(CURRENT_VERSION);
        return mappings != null ? mappings.get(fieldKey) : null;
    }

    // 获取方法的混淆名
    /**
     * Get the obfuscated method name for the given key.
     * @param methodKey the method mapping key like "LivingEntity.actuallyHurt"
     * @return the obfuscated method name, or null if not found
     */
    public static String getMethodMapping(String methodKey) {
        Map<String, String> mappings = METHOD_MAPPINGS.get(CURRENT_VERSION);
        return mappings != null ? mappings.get(methodKey) : null;
    }

    // 将运行期方法名还原为映射键中的源码名
    /**
     * Resolve an obfuscated runtime method name to its mapped source name.
     * @param runtimeName the runtime method name
     * @return the mapped source name, or null if no mapping exists
     */
    public static String getDeobfuscatedMethodName(String runtimeName) {
        if (runtimeName == null) return null;
        Map<String, String> mappings = METHOD_MAPPINGS.get(CURRENT_VERSION);
        if (mappings == null) return null;
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (!runtimeName.equals(entry.getValue())) continue;
            String key = entry.getKey();
            int separator = key.lastIndexOf('.');
            return separator < 0 ? key : key.substring(separator + 1);
        }
        return null;
    }

    // 检查字段映射是否存在
    /**
     * Check if a field mapping exists for the given key.
     * @param fieldKey the field mapping key
     * @return true if mapping exists
     */
    public static boolean hasFieldMapping(String fieldKey) {
        return getFieldMapping(fieldKey) != null;
    }

    // 检查方法映射是否存在
    /**
     * Check if a method mapping exists for the given key.
     * @param methodKey the method mapping key
     * @return true if mapping exists
     */
    public static boolean hasMethodMapping(String methodKey) {
        return getMethodMapping(methodKey) != null;
    }

    // 获取当前版本标识
    /**
     * Get the current Minecraft-Forge version string.
     * @return version string like "1.20.1-47.2.0"
     */
    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    // 注册自定义字段映射
    /**
     * Register a custom field mapping for the current version.
     * @param fieldKey the field mapping key
     * @param obfuscatedName the obfuscated field name
     */
    public static void registerFieldMapping(String fieldKey, String obfuscatedName) {
        FIELD_MAPPINGS.computeIfAbsent(CURRENT_VERSION, k -> new HashMap<>())
                .put(fieldKey, obfuscatedName);
    }

    // 注册自定义方法映射
    /**
     * Register a custom method mapping for the current version.
     * @param methodKey the method mapping key
     * @param obfuscatedName the obfuscated method name
     */
    public static void registerMethodMapping(String methodKey, String obfuscatedName) {
        METHOD_MAPPINGS.computeIfAbsent(CURRENT_VERSION, k -> new HashMap<>())
                .put(methodKey, obfuscatedName);
    }

    private ObfuscationMapping() {}
}
