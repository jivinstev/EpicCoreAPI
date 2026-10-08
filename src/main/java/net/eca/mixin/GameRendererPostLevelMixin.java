package net.eca.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eca.client.render.ShaderMaskRenderQueue;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.config.EcaConfiguration;
import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.entity_extension.GlobalSkyboxExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererPostLevelMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private Camera mainCamera;

    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void eca$extendForceLoadedDepthFar(CallbackInfoReturnable<Float> cir) {
        float forceLoadedFar = EcaConfiguration.getForceLoadingMaxRenderDistanceSafely() + 32.0f;
        if (cir.getReturnValue() < forceLoadedFar) {
            cir.setReturnValue(forceLoadedFar);
        }
    }

    // 在renderLevel()返回后注入，此时Oculus延迟渲染管线已完成合成，主帧缓冲区活跃
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void eca$renderPostLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (minecraft.level == null) {
            return;
        }

        // 1.21 不再传入 PoseStack：以相机旋转重建 1.20 中 renderLevel 所用的姿态栈
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(new Matrix4f().rotation(mainCamera.rotation().conjugate(new Quaternionf())));

        Camera camera = mainCamera;
        boolean cameraReady = camera != null && camera.getFluidInCamera() == FogType.NONE;

        // Oculus光影激活时才在此渲染shader skybox（管线合成后主帧缓冲区活跃）
        if (cameraReady && EcaShaderInstance.isOculusShadersActive()) {
            renderPostSkybox(poseStack);
        }

        // 所有扩展遮罩层共用一条延迟队列，保证矩阵与遮罩状态按 pass 成组恢复
        ShaderMaskRenderQueue.flush();
    }

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
                     shift = At.Shift.AFTER))
    private void eca$flushItemLayersAfterWorld(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!EcaShaderInstance.isOculusShadersActive()) {
            ShaderMaskRenderQueue.flush();
        }
    }

    private void renderPostSkybox(PoseStack poseStack) {
        GlobalSkyboxExtension skybox = getGlobalSkyboxExtension(minecraft.level.dimension().identifier());
        if (skybox == null || !skybox.enabled()) {
            return;
        }

        if (!skybox.enableShader() || skybox.shaderRenderType() == null) {
            return;
        }

        float size = Math.max(20.0f, skybox.size());
        float alpha = Mth.clamp(skybox.alpha(), 0.0f, 1.0f);
        if (alpha <= 0.0f) {
            return;
        }

        drawPostLevelSkybox(poseStack, skybox.shaderRenderType(), size, alpha);
    }

    private GlobalSkyboxExtension getGlobalSkyboxExtension(Identifier dimensionId) {
        return EntityExtensionClientState.getActiveSkybox(dimensionId);
    }

    private void drawPostLevelSkybox(PoseStack poseStack, RenderType renderType, float size, float alpha) {
        int alphaInt = (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        int light = 15728880;

        try (ByteBufferBuilder allocator = new ByteBufferBuilder(4096)) {
        for (int i = 0; i < 6; ++i) {
            poseStack.pushPose();
            rotateToFace(poseStack, i);
            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder bufferBuilder = new BufferBuilder(allocator, renderType.mode(), renderType.format());
            bufferBuilder.addVertex(matrix, -size, -size, -size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, -size, -size, size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, size, -size, size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, size, -size, -size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            renderType.draw(bufferBuilder.buildOrThrow());
            poseStack.popPose();
        }
        }
    }

    private static void rotateToFace(PoseStack poseStack, int face) {
        if (face == 1) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        } else if (face == 2) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
        } else if (face == 3) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
        } else if (face == 4) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
        } else if (face == 5) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0f));
        }
    }
}
