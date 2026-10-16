// Fork multi-version layer: 1.21.10 copy of the 1.21.8 overlay. 1.21.9 SUBMITS entities: there is no renderEntity; submitEntities
// loops the visible entity render states and hands each to EntityRenderDispatcher.submit, and the drawing happens later, in
// FeatureRenderDispatcher.renderAllFeatures. So the per-entity bracket (cloud occlusion, fog flag) is a @Redirect of that call,
// the frustum the bracket needs (the cullingFrustum field is gone) is taken from extractVisibleEntities' argument, and
// renderLevel gained a third Matrix4f (the culling projection).
// renderClouds/renderSky are frame-graph passes, as on 1.21.8, but 1.21.9 hands them the extracted LevelRenderState. The cloud
// skip cancels the clouds pass (1.21.10: lambda$addCloudsPass$3) at its DimensionSpecialEffects.renderClouds call, where
// 1.21.1's renderClouds HEAD was; CloudRendererMixin (from 1.21.3) also gates CloudRenderer.render. The global skybox is drawn
// from a pass of its own before the main pass (net.eca.mv.GlobalSkyboxHook.render), because 1.21.10 adds no sky pass when
// renderLevel's render-sky flag is false (GameRenderer passes !(isFoggyAt || boss-overlay world fog)) or addSkyPass declines
// (SkyType NONE, lava/powder snow, blindness/darkness), where 1.21.1's renderSky still reached its TAIL; it is skipped where a
// custom DimensionSpecialEffects.renderSky drew the sky (1.21.10's sky lambda is lambda$addSkyPass$8), and, on a frame with
// no sky pass, where the dimension's effects override renderSky at all (1.21.1 asked that override first and returned
// before the TAIL when it drew).
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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.renderer.LevelTargetBundle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    /* 1.21.10: force-loaded entities are submitted into this dispatcher's own storage and drawn at once, with the
       no-fog slice (see eca$submitEntityWithForceLoadBracket); created on first use, kept for the session. */
    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher eca$forceLoadedFeatures;

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
        // FilterRenderer's world-space pass inverts this frame's level projection (1.21.1: event.getProjectionMatrix())
        net.eca.client.render.shader.FilterRenderer.captureLevelProjection(projectionMatrix);
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

    // 1.21.10: clouds are a frame-graph pass (lambda$addCloudsPass$3). Cancel it where 1.21.1's renderClouds HEAD was:
    // before the custom DimensionSpecialEffects.renderClouds hook, so a dimension that draws its own clouds is skipped
    // too. renderLevel adds the clouds pass after addMainPass and addParticlesPass, and entities are submitted (and
    // eca$markCloudOcclusion sets the flag) in the main pass, so when the clouds pass executes the flag is final.
    @Inject(method = "lambda$addCloudsPass$3",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderClouds(Lnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/CloudStatus;IFLorg/joml/Matrix4f;)Z"),
            cancellable = true, require = 1)
    private void eca$skipOccludingClouds(CallbackInfo ci) {
        if (net.eca.mv.CloudGate.occlude) {
            ci.cancel();
        }
    }

    // ==================== 全局天空盒渲染 (fork layer, 1.21.2+) ====================

    @Shadow
    @Final
    private LevelTargetBundle targets;

    /* true when this frame's sky pass was drawn by a custom DimensionSpecialEffects.renderSky: 1.21.1's renderSky
       returned before its TAIL then, so the authors' skybox was not drawn. */
    @Unique
    private boolean eca$customSkyDrawn;

    /* true when this frame's sky pass ran and so asked the custom renderSky (eca$customSkyDrawn is its answer). */
    @Unique
    private boolean eca$skyPassAsked;

    /* Per DimensionSpecialEffects class: does it override renderSky (1.21.10's NeoForge default lives on
       IDimensionSpecialEffectsExtension and returns false)? Read reflectively once per class. */
    @Unique
    private static final java.util.Map<Class<?>, Boolean> ECA_CUSTOM_SKY_CLASSES = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 1)
    private void eca$resetCustomSky(CallbackInfo ci) {
        eca$customSkyDrawn = false;
        eca$skyPassAsked = false;
    }

    /* No sky pass this frame (render-sky flag false: foggy or boss-fog dimension; SkyType NONE, blindness/darkness,
       under lava/powder snow): 1.21.10 never calls renderSky then, but 1.21.1 still asked it before reaching the TAIL. A
       dimension with its own renderSky draws its sky there, so the skybox stays out, as it did when that override
       returned true. 1.21.10's signature: renderSky(LevelRenderState, SkyRenderState, Matrix4f, Runnable). */
    @Unique
    private static boolean eca$hasCustomSkyRenderer(net.minecraft.client.renderer.DimensionSpecialEffects effects) {
        if (effects == null) {
            return false;
        }
        return ECA_CUSTOM_SKY_CLASSES.computeIfAbsent(effects.getClass(), type -> {
            try {
                Class<?> owner = type.getMethod("renderSky", LevelRenderState.class,
                        net.minecraft.client.renderer.state.SkyRenderState.class, Matrix4f.class, Runnable.class)
                        .getDeclaringClass();
                return !owner.isInterface() && owner != net.minecraft.client.renderer.DimensionSpecialEffects.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        });
    }

    @ModifyExpressionValue(method = "lambda$addSkyPass$8",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSky(Lnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/renderer/state/SkyRenderState;Lorg/joml/Matrix4f;Ljava/lang/Runnable;)Z"),
            require = 1)
    private boolean eca$recordCustomSky(boolean handled) {
        eca$skyPassAsked = true;
        eca$customSkyDrawn = handled;
        return handled;
    }

    /* The authors' renderSky TAIL: a pass of its own on the main target, ordered after the sky pass (when there is
       one) and before the main pass. 1.21.10 still has one world fog buffer (GameRenderer passes
       FogRenderer.getBuffer(FogMode.WORLD) as renderLevel's GpuBufferSlice), and vanilla's sky pass sets exactly that
       with RenderSystem.setShaderFog before drawing, so this pass does the same. frustumMatrix is unused: see below. */
    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;ZLnet/minecraft/client/renderer/state/LevelRenderState;Lnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V"),
            require = 1)
    private void eca$addGlobalSkyboxPass(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                         Matrix4f frustumMatrix, Matrix4f projectionMatrix, Matrix4f cullProjection,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci,
                                         @Local FrameGraphBuilder frameGraph) {
        net.eca.mv.GlobalSkyboxHook.passOwned = true;
        FramePass pass = frameGraph.addPass("eca_global_skybox");
        this.targets.main = pass.readsAndWrites(this.targets.main);
        pass.executes(() -> {
            // the sky pass ran: its renderSky answer decides; no sky pass: skip where the dimension has its own renderSky
            if (eca$skyPassAsked ? eca$customSkyDrawn
                    : Minecraft.getInstance().level != null && eca$hasCustomSkyRenderer(Minecraft.getInstance().level.effects())) {
                return;
            }
            RenderSystem.setShaderFog(fogBuffer);
            // 1.21.1's renderSky ran before renderLevel pushed frustumMatrix onto the model-view stack, so the authors'
            // PoseStack carried it. 1.21.10 renderLevel still does matrix4fstack.mul(frustumMatrix) before building the
            // frame graph (popped after it executes), and both LegacyGl and RenderType.draw write
            // RenderSystem.getModelViewMatrix() (that stack) into DynamicTransforms as ModelViewMat, so the PoseStack
            // starts from identity here (the vanilla sky pass also draws its sun and moon from a bare PoseStack).
            net.eca.mv.GlobalSkyboxHook.render(new Matrix4f(), camera);
        });
    }

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
       原先在前后各做的 flushEntityBuffers 只是为了让“绘制”落在雾标志之内，提交阶段没有绘制，不再需要。
       [mv] 1.21.10 (port of 1.21.8's fix): a draw binds RenderSystem.getShaderFog() when it is flushed, and the level's
       submits are drawn later by renderAllFeatures with the world fog slice, so a force-loaded entity (no fluid in the
       camera) is submitted into eca$forceLoadedFeatures' own storage instead and drawn right here -- this is inside the
       main pass (lambda$addMainPass$1, after its setShaderFog) -- with FogRenderer's FogMode.NONE slice, then the slice is
       restored, as 1.21.1's setShaderFogStart/End(MAX_VALUE) around renderEntity did (1.21.8: flush, NONE, flush at
       RETURN). Its outline (glowing) still goes to the outline buffer source vanilla flushes later. */
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
                eca$forceLoadedFogActive = true;
            }
        }
        if (!eca$forceLoadedFogActive) {
            dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        net.minecraft.client.renderer.feature.FeatureRenderDispatcher features = eca$forceLoadedFeatures(minecraft);
        buffers.endBatch();
        GpuBufferSlice savedFog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(minecraft.gameRenderer.fogRenderer.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
        try {
            dispatcher.submit(state, cameraState, x, y, z, poseStack, features.getSubmitNodeStorage());
            features.renderAllFeatures();
            buffers.endBatch();
        } finally {
            features.getSubmitNodeStorage().clear();
            RenderSystem.setShaderFog(savedFog);
            eca$forceLoadedFogActive = false;
        }
    }

    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher eca$forceLoadedFeatures(Minecraft minecraft) {
        if (eca$forceLoadedFeatures == null) {
            eca$forceLoadedFeatures = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(
                    new net.minecraft.client.renderer.SubmitNodeStorage(),
                    minecraft.getBlockRenderer(),
                    minecraft.renderBuffers().bufferSource(),
                    minecraft.getAtlasManager(),
                    minecraft.renderBuffers().outlineBufferSource(),
                    minecraft.renderBuffers().crumblingBufferSource(),
                    minecraft.font);
        }
        return eca$forceLoadedFeatures;
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
        float cloudBottom = minecraft.level.dimensionType().cloudHeight().map(Integer::floatValue).orElse(Float.NaN);
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
