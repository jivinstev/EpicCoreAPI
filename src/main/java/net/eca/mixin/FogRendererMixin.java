// Fork multi-version layer: 1.21.2 version of the authors' mixin.
package net.eca.mixin;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.entity_extension.EntityExtension;
import net.eca.util.entity_extension.EntityExtensionManager;
import net.eca.util.entity_extension.EntityExtensionSafeAccess;
import net.eca.util.entity_extension.GlobalFogExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
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
import net.minecraft.client.renderer.FogParameters;
import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    // Fork multi-version layer, 1.21.2: fog colour is the Vector4f computeFogColor returns (LevelRenderer
    // clears with it and passes it to setupFog), and fog distance is the FogParameters setupFog returns,
    // instead of FogRenderer's static colour fields and RenderSystem's fog state.
    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private static void eca$applyGlobalFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistanceChunks,
                                               float darkenWorldAmount, CallbackInfoReturnable<Vector4f> cir) {
        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$resolveFogContext(level, camera, strengthHolder);
        if (fog == null) {
            return;
        }
        float strength = strengthHolder[0];
        Vector4f base = cir.getReturnValue();
        cir.setReturnValue(new Vector4f(
                Mth.lerp(strength, base.x, eca$clampColor(fog.fogRed())),
                Mth.lerp(strength, base.y, eca$clampColor(fog.fogGreen())),
                Mth.lerp(strength, base.z, eca$clampColor(fog.fogBlue())),
                base.w));
    }

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void eca$applyGlobalFogDistance(Camera camera, FogRenderer.FogMode fogMode, Vector4f fogColor, float renderDistance,
                                                  boolean thickFog, float partialTick, CallbackInfoReturnable<FogParameters> cir) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }

        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$resolveFogContext(minecraft.level, camera, strengthHolder);
        if (fog == null) {
            return;
        }
        float strength = strengthHolder[0];

        float customStart;
        float customEnd;
        if (fogMode == FogRenderer.FogMode.FOG_SKY) {
            customStart = fog.skyFogStart(renderDistance);
            customEnd = fog.skyFogEnd(renderDistance);
        } else {
            customStart = fog.terrainFogStart(renderDistance);
            customEnd = fog.terrainFogEnd(renderDistance);
        }

        FogParameters base = cir.getReturnValue();
        float start = Mth.lerp(strength, base.start(), customStart);
        float end = Mth.lerp(strength, base.end(), customEnd);

        if (end <= start) {
            end = start + 1.0f;
        }

        FogShape shape = strength >= 0.999f ? fog.fogShape() : null;
        cir.setReturnValue(new FogParameters(start, end, shape == null ? FogShape.CYLINDER : shape,
                base.red(), base.green(), base.blue(), base.alpha()));
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
