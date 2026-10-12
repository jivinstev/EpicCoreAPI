// Fork multi-version layer: 1.21.2 version of the authors' mixin.
package net.eca.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.GlobalFogExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    // Fork multi-version layer, 1.21.8: setupFog returns the clear colour; the fog colour/distances that reach
    // the shaders are the arguments of FogRenderer.updateBuffer (uniform buffer upload).
    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private void eca$applyGlobalFogColor(Camera camera, int renderDistanceChunks, boolean thickFog, DeltaTracker deltaTracker,
                                         float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<Vector4f> cir) {
        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$resolveFogContext(level, camera, strengthHolder);
        if (fog == null) {
            return;
        }
        cir.setReturnValue(eca$blendColor(cir.getReturnValue(), fog, strengthHolder[0]));
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Vector4f eca$modifyBufferColor(Vector4f color) {
        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$currentFog(strengthHolder);
        return fog == null ? color : eca$blendColor(color, fog, strengthHolder[0]);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static float eca$modifyEnvStart(float value) {
        return eca$distance(value, 0);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static float eca$modifyEnvEnd(float value) {
        return eca$distance(value, 1);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static float eca$modifyRenderStart(float value) {
        return eca$distance(value, 0);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static float eca$modifyRenderEnd(float value) {
        return eca$distance(value, 1);
    }

    @ModifyVariable(method = "updateBuffer", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private static float eca$modifySkyEnd(float value) {
        return eca$distance(value, 2);
    }

    @Unique
    private static Vector4f eca$blendColor(Vector4f base, GlobalFogExtension fog, float strength) {
        return new Vector4f(
                Mth.lerp(strength, base.x, eca$clampColor(fog.fogRed())),
                Mth.lerp(strength, base.y, eca$clampColor(fog.fogGreen())),
                Mth.lerp(strength, base.z, eca$clampColor(fog.fogBlue())),
                base.w);
    }

    @Unique
    private static GlobalFogExtension eca$currentFog(float[] strengthHolder) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || camera.getFluidInCamera() != FogType.NONE) {
            return null;
        }
        return eca$resolveFogContext(minecraft.level, camera, strengthHolder);
    }

    // kind: 0 terrain start, 1 terrain end, 2 sky end
    @Unique
    private static float eca$distance(float original, int kind) {
        if (net.eca.mv.Mv.isFogSuspended()) {
            return Float.MAX_VALUE;
        }
        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$currentFog(strengthHolder);
        if (fog == null) {
            return original;
        }
        float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
        float custom = kind == 0 ? fog.terrainFogStart(renderDistance)
                : kind == 1 ? fog.terrainFogEnd(renderDistance)
                : fog.skyFogEnd(renderDistance);
        return Mth.lerp(strengthHolder[0], original, custom);
    }

    @Unique
    private static GlobalFogExtension eca$resolveFogContext(ClientLevel level, Camera camera, float[] strengthHolder) {
        if (level == null || camera == null || strengthHolder == null || strengthHolder.length == 0) {
            return null;
        }

        ResourceLocation dimensionId = level.dimension().location();
        GlobalFogExtension cached = EntityExtensionClientState.getActiveFog(dimensionId);
        if (cached != null && cached.enabled() && cached.globalMode()) {
            strengthHolder[0] = 1.0f;
            return cached;
        }

        return eca$resolveLocalFogContext(level, camera, strengthHolder);
    }

    @Unique
    private static GlobalFogExtension eca$resolveLocalFogContext(ClientLevel level, Camera camera, float[] strengthHolder) {
        Vec3 cameraPos = camera.getPosition();
        if (cameraPos == null) {
            return null;
        }

        float bestStrength = 0.0f;
        int bestPriority = Integer.MIN_VALUE;
        GlobalFogExtension bestFog = null;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                continue;
            }

            EntityExtension extension = EntityExtensionManager.getExtension(living.getType());
            if (extension == null) {
                continue;
            }

            GlobalFogExtension fog = EntityExtensionSafeAccess.globalFogExtension(extension, living);
            if (fog == null || !fog.enabled() || fog.globalMode()) {
                continue;
            }

            if (!EntityExtensionSafeAccess.shouldEnableFog(extension, living)) {
                continue;
            }

            float radius = fog.radius();
            if (!(radius > 0.0f)) {
                continue;
            }

            float distance = (float)Math.sqrt(living.distanceToSqr(cameraPos));
            if (distance > radius) {
                continue;
            }

            float strength = 1.0f - (distance / radius);
            if (strength > bestStrength || (Math.abs(strength - bestStrength) < 0.0001f && extension.getPriority() > bestPriority)) {
                bestStrength = strength;
                bestPriority = extension.getPriority();
                bestFog = fog;
            }
        }

        if (bestFog == null || bestStrength <= 0.0f) {
            return null;
        }

        strengthHolder[0] = Mth.clamp(bestStrength, 0.0f, 1.0f);
        return bestFog;
    }

    @Unique
    private static float eca$clampColor(float color) {
        return Math.max(0.0f, Math.min(1.0f, color));
    }

}
