package net.eca.mixin;

import net.eca.util.EntityUtil;
import net.eca.util.health.health_lock.HealthLockPresence;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Presence hints remain conservative while synchronized values are changing. */
@Mixin(SynchedEntityData.class)
public abstract class SynchedHealthLockPresenceMixin implements HealthLockPresence {
    @Shadow @Final private SyncedDataHolder entity;
    @Shadow public abstract <T> T get(EntityDataAccessor<T> accessor);

    @Unique private volatile boolean eca$healthMayBePresent = true;
    @Unique private volatile boolean eca$maxHealthMayBePresent = true;
    @Unique private volatile int eca$lockUpdateDepth;

    @Override
    public boolean eca$mayHaveHealthLock() {
        return eca$lockUpdateDepth != 0 || eca$healthMayBePresent;
    }

    @Override
    public boolean eca$mayHaveMaxHealthLock() {
        return eca$lockUpdateDepth != 0 || eca$maxHealthMayBePresent;
    }

    @Unique
    private boolean eca$isLockValue(EntityDataAccessor<?> accessor) {
        return accessor.equals(EntityUtil.HEALTH_LOCK_VALUE)
                || accessor.equals(EntityUtil.MAX_HEALTH_LOCK_VALUE);
    }

    @Unique
    private boolean eca$mayContainValue(EntityDataAccessor<String> accessor) {
        if (accessor == null || !eca$hasItem(accessor)) return true;
        String value = get(accessor);
        return value != null && !value.isEmpty();
    }

    // 1.21.1 has no hasItem: items are fixed at construction, indexed by accessor id
    @Unique
    private boolean eca$hasItem(EntityDataAccessor<?> accessor) {
        SynchedEntityData.DataItem<?>[] items = ((SynchedEntityData) (Object) this).itemsById;
        int id = accessor.id();
        return id >= 0 && id < items.length && items[id] != null && items[id].getAccessor() == accessor;
    }

    @Unique
    private void eca$refreshLockPresence() {
        if (!(entity instanceof LivingEntity)) return;
        eca$healthMayBePresent = eca$mayContainValue(EntityUtil.HEALTH_LOCK_VALUE);
        eca$maxHealthMayBePresent = eca$mayContainValue(EntityUtil.MAX_HEALTH_LOCK_VALUE);
    }

    // 1.21.1: define moved to SynchedEntityData.Builder, so every value (the lock fields included) is in place when
    // the constructor returns
    @Inject(method = "<init>", at = @At("RETURN"))
    private void eca$afterInit(SyncedDataHolder holder, SynchedEntityData.DataItem<?>[] items, CallbackInfo ci) {
        eca$refreshLockPresence();
    }

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"))
    private <T> void eca$beforeSet(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (eca$isLockValue(accessor)) eca$lockUpdateDepth++;
    }

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("RETURN"))
    private <T> void eca$afterSet(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        if (!eca$isLockValue(accessor)) return;
        eca$refreshLockPresence();
        eca$lockUpdateDepth--;
    }

    @Inject(method = "assignValues", at = @At("HEAD"))
    private void eca$beforeAssign(List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {
        // 更新回调可能重入血量读取，批量写入完成前必须继续读取实际同步字段。
        eca$lockUpdateDepth++;
    }

    @Inject(method = "assignValues", at = @At("RETURN"))
    private void eca$afterAssign(List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {
        eca$refreshLockPresence();
        eca$lockUpdateDepth--;
    }
}
