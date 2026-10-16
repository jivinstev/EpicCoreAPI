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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
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

    // 1.21.8: the colour and distances that reach the shaders are the arguments of setupFog's one updateBuffer call (the
    // world fog buffer; the constructor's call fills the no-fog buffer and is left alone). As 1.21.1 did: the colour is
    // tinted with no fluid check (setupColor TAIL -> levelFogColor); the distances are changed only with no fluid in the
    // camera (setupFog TAIL). 1.21.1 forced FogShape.CYLINDER unless the extension asked otherwise at full strength;
    // fog.glsl takes the max of a spherical (environmental) and a cylindrical (render-distance) fog, with 1.21.1's
    // cylindrical distance, so the shape is which pair of fields gets the custom start/end: the other pair is lerped (by
    // strength, from vanilla's) to a finite distance where it is never the nearer fog, so s=0 is vanilla, s=1 1.21.1's shape.
    @WrapOperation(method = "setupFog", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"))
    private void eca$applyGlobalFogBuffer(FogRenderer self, ByteBuffer buffer, int offset, Vector4f color,
                                          float envStart, float envEnd, float renderStart, float renderEnd,
                                          float skyEnd, float cloudEnd, Operation<Void> original) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        float[] strengthHolder = new float[]{0.0f};
        GlobalFogExtension fog = eca$resolveFogContext(minecraft.level, camera, strengthHolder);
        if (fog == null) {
            original.call(self, buffer, offset, color, envStart, envEnd, renderStart, renderEnd, skyEnd, cloudEnd);
            return;
        }
        float strength = strengthHolder[0];
        color = eca$blendColor(color, fog, strength);
        if (camera.getFluidInCamera() == FogType.NONE) {
            float renderDistance = minecraft.options.getEffectiveRenderDistance() * 16.0f;
            float customStart = fog.terrainFogStart(renderDistance);
            float customEnd = fog.terrainFogEnd(renderDistance);
            GlobalFogExtension.Shape shape = strength >= 0.999f ? fog.fogShape() : null;
            if (shape == GlobalFogExtension.Shape.SPHERE) {
                float start = Mth.lerp(strength, envStart, customStart);
                float end = Mth.lerp(strength, envEnd, customEnd);
                envStart = start;
                envEnd = end <= start ? start + 1.0f : end;
                // the cylindrical distance is at most the spherical one, so a render-distance fog that starts at the
                // custom end can never be the nearer of the two (fog.glsl total_fog_value takes the max)
                renderStart = Mth.lerp(strength, renderStart, envEnd);
                renderEnd = Mth.lerp(strength, renderEnd, envEnd + 1.0f);
            } else {
                float start = Mth.lerp(strength, renderStart, customStart);
                float end = Mth.lerp(strength, renderEnd, customEnd);
                renderStart = start;
                renderEnd = end <= start ? start + 1.0f : end;
                // the spherical distance is at most sqrt(2) times the cylindrical one (length(pos) against
                // max(length(pos.xz), abs(pos.y))), so an environmental fog that starts at sqrt(2) x the custom end can
                // never be the nearer of the two; lerped from vanilla's, so the fog fades in with strength as on 1.21.1
                float pushed = renderEnd * Mth.SQRT_OF_TWO;
                envStart = Mth.lerp(strength, envStart, pushed);
                envEnd = Mth.lerp(strength, envEnd, pushed + 1.0f);
            }
            // the sky shader fogs from 0 to FogSkyEnd (1.21.1's FOG_SKY start has no field)
            skyEnd = Mth.lerp(strength, skyEnd, fog.skyFogEnd(renderDistance));
        }
        original.call(self, buffer, offset, color, envStart, envEnd, renderStart, renderEnd, skyEnd, cloudEnd);
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
