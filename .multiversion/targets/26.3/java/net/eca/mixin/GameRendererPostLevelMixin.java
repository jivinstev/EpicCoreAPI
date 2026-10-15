// Fork multi-version layer: 26.1.2 version (LevelRenderer.renderLevel takes the CameraRenderState and the chunk sections;
// GameRenderer.getDepthFar became Camera.depthFar -- see CameraDepthFarMixin).
package net.eca.mixin;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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

    // 26.1: GameRenderer.getDepthFar is gone; the far plane is Camera.depthFar, set in Camera.update. The force-loaded
    // extension of it moved to CameraDepthFarMixin (added for this target by target.properties mixin_add).

    // 在renderLevel()返回后注入，此时Oculus延迟渲染管线已完成合成，主帧缓冲区活跃
    @Inject(method = "renderLevel", at = @At("RETURN"))
    // 26.3: renderLevel() takes no DeltaTracker
    private void eca$renderPostLevel(CallbackInfo ci) {
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

    // 26.2: LevelRenderer.renderLevel(..., ChunkSectionsToRender) is render(...) without the sections argument;
    // 26.3: render(allocator, outline, cameraState, terrainFog, fogColor, renderSky, consistentDepth) (no DeltaTracker / model-view)
    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
                     shift = At.Shift.AFTER))
    private void eca$flushItemLayersAfterWorld(CallbackInfo ci) {
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

        // 26.2: Tesselator is gone; a local ByteBufferBuilder backs each face's BufferBuilder
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4)) {
        net.eca.mv.LegacyGl.setupRenderState(renderType);

        for (int i = 0; i < 6; ++i) {
            poseStack.pushPose();
            rotateToFace(poseStack, i);
            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);
            bufferBuilder.addVertex(matrix, -size, -size, -size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, -size, -size, size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, size, -size, size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            bufferBuilder.addVertex(matrix, size, -size, -size).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 1.0f, 0.0f);
            net.eca.mv.LegacyGl.drawWithShader(bufferBuilder.buildOrThrow());
            poseStack.popPose();
        }

        net.eca.mv.LegacyGl.clearRenderState(renderType);
        }
    }

    private static void rotateToFace(PoseStack poseStack, int face) {
        if (face == 1) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(90.0)));
        } else if (face == 2) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-90.0)));
        } else if (face == 3) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(180.0)));
        } else if (face == 4) {
            poseStack.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(90.0)));
        } else if (face == 5) {
            poseStack.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-90.0)));
        }
    }
}
