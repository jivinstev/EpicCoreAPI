// Fork multi-version layer: 1.21.10 copy of the 1.21.8 overlay. 1.21.9 SUBMITS entities: there is no renderEntity; submitEntities
// loops the visible entity render states and hands each to EntityRenderDispatcher.submit, and the drawing happens later, in
// FeatureRenderDispatcher.renderAllFeatures. So the per-entity bracket (cloud occlusion, fog flag) is a @Redirect of that call,
// the frustum the bracket needs (the cullingFrustum field is gone) is taken from extractVisibleEntities' argument, and
// renderLevel gained a third Matrix4f (the culling projection).
// 1.21.8 copy of the 1.21.5 overlay: renderLevel lost its GameRenderer and LightTexture and gained a GraphicsResourceAllocator, the fog buffer, the fog colour and a render-sky flag (1.21.6 shape; shared row in shared/1.21.8/renames.tsv);
// renderClouds/renderSky became frame-graph passes, so the cloud skip moved to CloudRendererMixin and the global
// skybox to net.eca.mv.GlobalSkyboxHook (RenderLevelStageEvent AFTER_SKY, which runs where renderSky's TAIL did).
package net.eca.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.config.EcaConfiguration;

import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.entity_extension.ForceLoadingManager;
import net.eca.util.entity_extension.GlobalSkyboxExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    /** This frame's culling frustum: renderLevel's local, which 1.21.9 no longer keeps in a field. Taken from extractVisibleEntities. */
    @Unique
    private Frustum eca$cullingFrustum;

    @Unique
    private boolean eca$forceLoadedFogActive;

    @Unique
    private float eca$savedFogStart;

    @Unique
    private float eca$savedFogEnd;

    /* 本帧存在横跨云层平面的可见强加载实体。云层绘制在实体之后并吃雾色，
       远处的云挡住巨型模型时看起来不像云，而像模型被挖掉一块。 */

    // 云层平板厚度：花式云的四边形在 y 方向占 4 格，快速云是平面，取上界统一处理
    @Unique
    private static final float ECA_CLOUD_SLAB_THICKNESS = 4.0f;

    // ==================== 强加载实体渲染 ====================

    /* 云层关闭时 renderClouds 根本不会被调用，用后复位会让标志跨帧残留，只能在帧首清零 */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void eca$resetCloudOcclusion(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                         Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f cullProjection,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        net.eca.mv.CloudGate.occlude = false;
    }

    /* shouldRender 每次进入都会登记当前实体，isChunkCompiled 是它在同一个判断表达式里的读取方。
       但 shouldRender 返回 false 时 isChunkCompiled 不会被求值，登记值没有配对的消费点，
       只能由帧边界回收。ReceivingLevelScreen 在 tick 里也调 isChunkCompiled，
       读到跨帧残留会让“正在加载地形”在地形就绪前提前关闭。 */
    @Inject(method = "renderLevel", at = {@At("HEAD"), @At("RETURN")})
    private void eca$clearForceLoadedRenderContext(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                                   Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f cullProjection,
                                                   GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        ForceLoadingManager.clearCurrentRenderingEntity();
    }

    // 1.21.2: clouds are a frame-graph pass; net.eca.mixin.CloudRendererMixin cancels them (fork layer).

    @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
    private void eca$forceLoadedChunkCheck(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = ForceLoadingManager.getCurrentRenderingEntity();
        if (entity != null && ForceLoadingManager.shouldForceLoad(entity)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void eca$captureCullingFrustum(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                           LevelRenderState levelRenderState, CallbackInfo ci) {
        this.eca$cullingFrustum = frustum;
    }

    /* 1.21.9 之前这一对是 renderEntity 的 HEAD / RETURN；现在 submitEntities 逐个把渲染状态交给
       EntityRenderDispatcher.submit，这里把同样的前后处理包在那次调用外面。
       注意：绘制发生在之后的 renderAllFeatures，所以“前后”只覆盖提交，不再覆盖绘制——
       云层遮挡标记（本帧的状态，云层之后才读）不受影响；雾标志在 1.21.6 之后本来就只是个标志
       （雾是整帧一份的 uniform，见 net.eca.mv.Mv.suspendFog），所以这里也没有损失。
       原先在前后各做的 flushEntityBuffers 只是为了让“绘制”落在雾标志之内，提交阶段没有绘制，不再需要。 */
    @Redirect(method = "submitEntities",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void eca$submitEntityWithForceLoadBracket(EntityRenderDispatcher dispatcher, EntityRenderState state,
                                                      CameraRenderState cameraState, double x, double y, double z,
                                                      PoseStack poseStack, SubmitNodeCollector collector) {
        eca$forceLoadedFogActive = false;
        Entity entity = net.eca.mv.EntityOf.get(state);
        if (entity != null && ForceLoadingManager.shouldForceLoad(entity)) {
            Minecraft minecraft = Minecraft.getInstance();
            Vec3 cameraPos = cameraState.pos;
            eca$markCloudOcclusion(minecraft, entity, cameraPos.x, cameraPos.y, cameraPos.z);
            Camera camera = minecraft.gameRenderer.getMainCamera();
            if (camera != null && camera.getFluidInCamera() == FogType.NONE) {
                net.eca.mv.Mv.suspendFog();
                eca$forceLoadedFogActive = true;
            }
        }
        try {
            dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
        } finally {
            if (eca$forceLoadedFogActive) {
                net.eca.mv.Mv.restoreFog();
                eca$forceLoadedFogActive = false;
            }
        }
    }

    /* 强加载实体的 EntityRenderDispatcher.shouldRender 被改成了纯距离判断（绕过视锥），
       背后的实体照样会走到 renderEntity，所以这里要补一次视锥判断，否则看不见的实体也会把云关掉。
       判据取渲染器自身的 shouldRender：巨型模型的包围盒常小于模型，只有渲染器知道实际范围。 */
    @Unique
    private void eca$markCloudOcclusion(Minecraft minecraft, Entity entity,
                                        double camX, double camY, double camZ) {
        if (net.eca.mv.CloudGate.occlude || minecraft.level == null) {
            return;
        }
        if (!EcaConfiguration.getForceLoadingHideOccludingCloudsSafely()) {
            return;
        }
        float cloudBottom = minecraft.level.environmentAttributes().getValue(
                net.minecraft.world.attribute.EnvironmentAttributes.CLOUD_HEIGHT, new net.minecraft.world.phys.Vec3(camX, camY, camZ));
        if (Float.isNaN(cloudBottom)) {
            return;
        }

        AABB box = net.eca.mv.Mv.cullingBox(entity);
        float cloudTop = cloudBottom + ECA_CLOUD_SLAB_THICKNESS;
        // 相机与实体的纵向跨度必须真的切过云层平板，否则视线不经过云
        if (Math.max(camY, box.maxY) <= cloudBottom || Math.min(camY, box.minY) >= cloudTop) {
            return;
        }

        Frustum frustum = this.eca$cullingFrustum;
        if (frustum != null && !eca$isVisibleToRenderer(minecraft, entity, frustum, camX, camY, camZ)) {
            return;
        }
        net.eca.mv.CloudGate.occlude = true;
    }

    @Unique
    private static boolean eca$isVisibleToRenderer(Minecraft minecraft, Entity entity, Frustum frustum,
                                                   double camX, double camY, double camZ) {
        EntityRenderer<? super Entity, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) {
            return true;
        }
        return renderer.shouldRender(entity, frustum, camX, camY, camZ);
    }
}
