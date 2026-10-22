// Fork multi-version layer: 26.1.2 version (one edit of setupFog's FogData) of the 1.21.2 version of the authors' mixin.
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
import net.minecraft.resources.Identifier;
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
import org.joml.Vector4f;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    // Fork multi-version layer, 26.1.2: setupFog returns the frame's FogData (colour + distances), and that one object is
    // both the clear colour (FogData.color is LevelRenderer.renderLevel's fogColor) and what updateBuffer(FogData) uploads to
    // the fog uniform block. So the 1.21.8-1.21.11 pair -- the clear colour at setupFog's RETURN plus @ModifyVariable on
    // the private updateBuffer's arguments (now an overload, so the name-only target matched nothing) -- becomes one edit
    // of the returned FogData: the colour blended once (not twice) and the distances per the extension's shape (ECA-026).
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void eca$applyGlobalFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker,
                                    float darkenWorldAmount, ClientLevel level,
                                    CallbackInfoReturnable<net.minecraft.client.renderer.fog.FogData> cir) {
        net.minecraft.client.renderer.fog.FogData data = cir.getReturnValue();
        if (data == null) {
            return;
        }
        float[] strengthHolder = new float[]{0.0f};
        // 26.2: the fog-suspended flag (net.eca.mv.Mv.suspendFog) pushed every distance out, as 1.21.1's MAX_VALUE did
        boolean suspended = net.eca.mv.Mv.isFogSuspended();
        if (suspended) {
            data.environmentalStart = Float.MAX_VALUE;
            data.environmentalEnd = Float.MAX_VALUE;
            data.renderDistanceStart = Float.MAX_VALUE;
            data.renderDistanceEnd = Float.MAX_VALUE;
            data.skyEnd = Float.MAX_VALUE;
        }
        GlobalFogExtension fog = eca$resolveFogContext(level, camera, strengthHolder);
        if (fog == null) {
            return;
        }
        float strength = strengthHolder[0];
        // the colour is blended in any medium, as 1.21.1's setupColor TAIL did; the distances only in air (setupFog TAIL)
        data.color = eca$blendColor(data.color, fog, strength);
        if (!suspended && camera.getFluidInCamera() == FogType.NONE) {
            // 26.1.2 (ECA-026, as 1.21.8-1.21.11's WrapOperation on updateBuffer's arguments, here on the FogData fields
            // updateBuffer(FogData) uploads): fog.glsl takes the max of a spherical (environmental) and a cylindrical
            // (render-distance) fog, so the extension's shape is which pair of fields gets the custom start/end; the other
            // pair is lerped (by strength, from vanilla's) to a finite distance where it is never the nearer fog, so s=0 is
            // vanilla and s=1 1.21.1's shape (1.21.1 forced FogShape.CYLINDER unless the extension asked otherwise at full
            // strength).
            float renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f;
            float customStart = fog.terrainFogStart(renderDistance);
            float customEnd = fog.terrainFogEnd(renderDistance);
            GlobalFogExtension.Shape shape = strength >= 0.999f ? fog.fogShape() : null;
            if (shape == GlobalFogExtension.Shape.SPHERE) {
                float start = Mth.lerp(strength, data.environmentalStart, customStart);
                float end = Mth.lerp(strength, data.environmentalEnd, customEnd);
                data.environmentalStart = start;
                data.environmentalEnd = end <= start ? start + 1.0f : end;
                // the cylindrical distance is at most the spherical one, so a render-distance fog that starts at the
                // custom end can never be the nearer of the two (fog.glsl total_fog_value takes the max)
                data.renderDistanceStart = Mth.lerp(strength, data.renderDistanceStart, data.environmentalEnd);
                data.renderDistanceEnd = Mth.lerp(strength, data.renderDistanceEnd, data.environmentalEnd + 1.0f);
            } else {
                float start = Mth.lerp(strength, data.renderDistanceStart, customStart);
                float end = Mth.lerp(strength, data.renderDistanceEnd, customEnd);
                data.renderDistanceStart = start;
                data.renderDistanceEnd = end <= start ? start + 1.0f : end;
                // the spherical distance is at most sqrt(2) times the cylindrical one (length(pos) against
                // max(length(pos.xz), abs(pos.y))), so an environmental fog that starts at sqrt(2) x the custom end can
                // never be the nearer of the two; lerped from vanilla's, so the fog fades in with strength as on 1.21.1
                float pushed = data.renderDistanceEnd * Mth.SQRT_OF_TWO;
                data.environmentalStart = Mth.lerp(strength, data.environmentalStart, pushed);
                data.environmentalEnd = Mth.lerp(strength, data.environmentalEnd, pushed + 1.0f);
            }
            // the sky shader fogs from 0 to FogSkyEnd (1.21.1's FOG_SKY start has no field)
            data.skyEnd = Mth.lerp(strength, data.skyEnd, fog.skyFogEnd(renderDistance));
        }
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
    private static GlobalFogExtension eca$resolveFogContext(ClientLevel level, Camera camera, float[] strengthHolder) {
        if (level == null || camera == null || strengthHolder == null || strengthHolder.length == 0) {
            return null;
        }

        Identifier dimensionId = level.dimension().identifier();
        GlobalFogExtension cached = EntityExtensionClientState.getActiveFog(dimensionId);
        if (cached != null && cached.enabled() && cached.globalMode()) {
            strengthHolder[0] = 1.0f;
            return cached;
        }

        return eca$resolveLocalFogContext(level, camera, strengthHolder);
    }

    @Unique
    private static GlobalFogExtension eca$resolveLocalFogContext(ClientLevel level, Camera camera, float[] strengthHolder) {
        Vec3 cameraPos = camera.position();
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
