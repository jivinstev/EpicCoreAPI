// Fork multi-version layer: 26.1.2 version of the authors' file (Camera.setup became update(DeltaTracker)).
package net.eca.mixin;

import net.eca.util.bossshow.BossShowClientState;
import net.eca.util.bossshow.BossShowEditorState;
import net.eca.util.bossshow.BossShowPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//当 BossShow 活跃时，在 Camera.setup TAIL 覆写相机位置和朝向
@Mixin(Camera.class)
public class BossShowCameraMixin {

    // 26.1: Camera.setup(level, entity, thirdPerson, reverse, partialTick) is gone; update(DeltaTracker) positions the camera
    // through alignWithEntity and then builds the cull frustum and projection from that pose. Overriding right after
    // alignWithEntity keeps 1.21.1's effect (setup TAIL ran before the frame's frustum was taken from the camera).
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void eca$overrideCameraForBossShow(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        float partialTicks = ((Camera) (Object) this).getCameraEntityPartialTicks(deltaTracker);
        //播放优先；播放未激活时才考虑编辑器预览（二者互斥）
        BossShowPose pose;
        if (BossShowClientState.isActive()) {
            pose = BossShowClientState.computePoseForRender(partialTicks);
        } else if (BossShowEditorState.isPreviewActive()) {
            pose = BossShowEditorState.computePreviewPose();
        } else {
            return;
        }
        Camera self = (Camera) (Object) this;
        self.setPosition(pose.x, pose.y, pose.z);
        self.setRotation(pose.yaw, pose.pitch);
    }
}
