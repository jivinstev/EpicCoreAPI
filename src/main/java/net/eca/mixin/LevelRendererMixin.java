// Fork multi-version layer: 26.1.2 (renderLevel takes the CameraRenderState, one model-view Matrix4fc and the chunk sections) copy of the
// 1.21.10 copy of the 1.21.8 overlay. 1.21.9 SUBMITS entities: there is no renderEntity; submitEntities
// loops the visible entity render states and hands each to EntityRenderDispatcher.submit, and the drawing happens later, in
// FeatureRenderDispatcher.renderAllFeatures. So the per-entity bracket (cloud occlusion, fog flag) is a @Redirect of that call,
// the frustum the bracket needs (the cullingFrustum field is gone) is taken from extractVisibleEntities' argument, and
// renderLevel gained a third Matrix4f (the culling projection).
// 1.21.8 copy of the 1.21.5 overlay: renderLevel lost its GameRenderer and LightTexture and gained a GraphicsResourceAllocator, the fog buffer, the fog colour and a render-sky flag (1.21.6 shape; shared row in shared/1.21.8/renames.tsv);
// renderClouds/renderSky became frame-graph passes, so the cloud skip moved to CloudRendererMixin and the global
// skybox to net.eca.mv.GlobalSkyboxHook (RenderLevelStageEvent AFTER_SKY, which runs where renderSky's TAIL did).
// 26.2 (#32): the 26.1.2 shape with 26.2's names. renderLevel is render(...) without the chunk sections; entities are
// submitted by submitFeatures at the start of render(), before the frame graph is even built, so the cloud-occlusion flag
// is final before any pass executes. The clouds lambda is lambda$addCloudsPass$0 and null-checks
// LevelRenderState.customCloudsRenderer before CloudRenderer.render, so the skip cancels it at HEAD. The sky lambda is
// lambda$addSkyPass$0 (posting AfterSky after a custom CustomSkyboxRenderer.renderSky too); render() adds the sky pass only
// when shouldRenderSky, and addSkyPass declines lava/powder snow, doesMobEffectBlockSky and Skybox NONE, so the skybox gets
// its own pass on targets.main before addMainPass (now taking the FeatureRenderDispatcher.PreparedFrame and the
// model-view Matrix4fc), with render()'s terrainFog slice as the sky and main passes use.
// 26.2 (review of #32/#33, F3): the skybox pass also stands down, on a frame with no sky pass, where the dimension has a
// CustomSkyboxRenderer at all (1.21.1 asked that override first and returned before the TAIL when it drew). 26.2's
// LevelExtractor.extract fills LevelRenderState.customSkyboxRenderer (reset to null first) before GameRenderer.renderLevel
// calls render(); LevelRenderer keeps that state in its package-private final levelRenderState field.
package net.eca.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

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
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
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

    // This frame's culling frustum lives in net.eca.mv.CullingFrustum (26.2: taken from LevelExtractor.extractVisibleEntities).

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
    // 26.2: LevelRenderer.renderLevel is render(...), without the ChunkSectionsToRender argument
    @Inject(method = "render", at = @At("HEAD"))
    private void eca$resetCloudOcclusion(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                         net.minecraft.client.renderer.state.level.CameraRenderState cameraState, org.joml.Matrix4fc modelView,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        net.eca.mv.CloudGate.occlude = false;
        // FilterRenderer's world-space pass inverts this frame's level projection (1.21.1: event.getProjectionMatrix()).
        // 26.1.2: renderLevel no longer receives it (cameraState.projectionMatrix lacks bobHurt/bobView); it is captured
        // where GameRenderer.renderLevel uploads it, in GameRendererPostLevelMixin.eca$captureLevelProjection
    }

    /* shouldRender 每次进入都会登记当前实体，isChunkCompiled 是它在同一个判断表达式里的读取方。
       但 shouldRender 返回 false 时 isChunkCompiled 不会被求值，登记值没有配对的消费点，
       只能由帧边界回收。ReceivingLevelScreen 在 tick 里也调 isChunkCompiled，
       读到跨帧残留会让“正在加载地形”在地形就绪前提前关闭。 */
    @Inject(method = "render", at = {@At("HEAD"), @At("RETURN")})
    private void eca$clearForceLoadedRenderContext(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                                   net.minecraft.client.renderer.state.level.CameraRenderState cameraState, org.joml.Matrix4fc modelView,
                                                   GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        ForceLoadingManager.clearCurrentRenderingEntity();
    }

    // 1.21.2: clouds are a frame-graph pass; net.eca.mixin.CloudRendererMixin cancels vanilla's CloudRenderer.render (fork
    // layer, inherited from 26.1.2: 26.2's render(int, CloudStatus, float, int, Vec3, long, float) is unchanged).
    // 26.2: that alone missed a dimension with its own clouds. The pass lambda (lambda$addCloudsPass$0) first asks
    // LevelRenderState.customCloudsRenderer (null when the dimension has none) and only falls back to CloudRenderer.render,
    // so cancelling the lambda at HEAD is 1.21.1's renderClouds HEAD: before both the custom hook and vanilla's clouds.
    @Inject(method = "lambda$addCloudsPass$0", at = @At("HEAD"), cancellable = true, require = 1)
    private void eca$skipOccludingClouds(CallbackInfo ci) {
        if (net.eca.mv.CloudGate.occlude) {
            ci.cancel();
        }
    }

    // ==================== 全局天空盒渲染 (fork layer, 1.21.2+) ====================

    @Shadow
    @Final
    private LevelTargetBundle targets;

    /* true when this frame's sky pass was drawn by a custom CustomSkyboxRenderer.renderSky: 1.21.1's renderSky returned
       before its TAIL then, so the authors' skybox was not drawn. Reset every frame at render() HEAD. */
    @Unique
    private boolean eca$customSkyDrawn;

    /* true when this frame's sky pass reached the custom renderSky call (eca$customSkyDrawn then holds its answer). */
    @Unique
    private boolean eca$skyPassAsked;

    /* 26.2: LevelExtractor.extract fills customSkyboxRenderer (null when the dimension has none) before render(). */
    @Shadow
    @Final
    LevelRenderState levelRenderState;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void eca$resetCustomSky(CallbackInfo ci) {
        eca$customSkyDrawn = false;
        eca$skyPassAsked = false;
    }

    @ModifyExpressionValue(method = "lambda$addSkyPass$0",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/CustomSkyboxRenderer;renderSky(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lorg/joml/Matrix4fc;Ljava/lang/Runnable;)Z"),
            require = 1)
    private boolean eca$recordCustomSky(boolean handled) {
        eca$skyPassAsked = true;
        eca$customSkyDrawn = handled;
        return handled;
    }

    /* The authors' renderSky TAIL, as a pass of its own on the main target: added after the sky pass (when render() adds one)
       and before addMainPass, so it executes between them as 1.21.1's TAIL did. */
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/util/profiling/ProfilerFiller;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lorg/joml/Matrix4fc;)V"),
            require = 1)
    private void eca$addGlobalSkyboxPass(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline,
                                         net.minecraft.client.renderer.state.level.CameraRenderState cameraState, org.joml.Matrix4fc modelView,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo ci,
                                         @Local FrameGraphBuilder frameGraph) {
        net.eca.mv.GlobalSkyboxHook.passOwned = true;
        FramePass pass = frameGraph.addPass("eca_global_skybox");
        this.targets.main = pass.readsAndWrites(this.targets.main);
        pass.executes(() -> {
            // a sky pass asked the custom renderSky: its answer decides (false still drew the skybox on 1.21.1). No sky
            // pass this frame (shouldRenderSky false under boss world fog, or addSkyPass declined: Skybox NONE, lava/powder
            // snow, a sky-blocking mob effect), so renderSky was never asked: stand down where the dimension has one.
            if (eca$skyPassAsked ? eca$customSkyDrawn : this.levelRenderState.customSkyboxRenderer != null) {
                return;
            }
            // render()'s terrainFog is the one world fog slice the sky pass and the main pass both set; do the same.
            RenderSystem.setShaderFog(fogBuffer);
            // render() has already done modelViewStack.mul(modelViewMatrix) (popped after the graph executes), and the
            // shared LegacyGl / LegacyRenderType upload RenderSystem.getModelViewMatrixCopy() as ModelViewMat, so the view
            // rotation is applied once by the stack: the authors' PoseStack starts from identity here. Depth: this pass
            // runs on a depth buffer just cleared to 0.0 (26.2 is reverse-Z, 0 is the far plane). LegacyGl's default
            // compare and LegacyRenderType's LEQUAL shard are GREATER_THAN_OR_EQUAL, so every in-frustum fragment passes
            // against 0.0 as it did against 1.21.1's cleared 1.0; the texture skybox writes no depth (depthMask(false)),
            // as vanilla's sky pipelines write none; a shader skybox writes depth exactly when its render type says so.
            net.eca.mv.GlobalSkyboxHook.render(new Matrix4f(), Minecraft.getInstance().gameRenderer.mainCamera());
        });
    }

    /* 26.2 (#33): render() submits every feature before it builds the frame graph (26.1.2 did it inside the main pass),
       so an extension pass that draws itself from a submit would land before the clear pass. net.eca.mv.SubmitPhase
       sends such passes to the after-level queue while this flag is up. */
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"),
            require = 1)
    private void eca$enterLevelSubmit(CallbackInfo ci) {
        net.eca.mv.SubmitPhase.levelSubmit = true;
    }

    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
                     shift = At.Shift.AFTER),
            require = 1)
    private void eca$leaveLevelSubmit(CallbackInfo ci) {
        net.eca.mv.SubmitPhase.levelSubmit = false;
    }

    // 1.21.11: isSectionCompiled became isSectionCompiledAndVisible (compiled AND faded in); forcing true keeps the same effect.
    @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
    private void eca$forceLoadedChunkCheck(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = ForceLoadingManager.getCurrentRenderingEntity();
        if (entity != null && ForceLoadingManager.shouldForceLoad(entity)) {
            cir.setReturnValue(true);
        }
    }

    // 26.2: extractVisibleEntities moved to LevelExtractor; LevelExtractorMixin keeps its frustum in net.eca.mv.CullingFrustum

    /* 1.21.9 之前这一对是 renderEntity 的 HEAD / RETURN；现在 submitEntities 逐个把渲染状态交给
       EntityRenderDispatcher.submit，这里把同样的前后处理包在那次调用外面。
       注意：绘制发生在之后的 renderAllFeatures，所以“前后”只覆盖提交，不再覆盖绘制——
       云层遮挡标记（本帧的状态，云层之后才读）不受影响。
       26.2 (ECA-026, as 1.21.10-26.1.2's bracket): a force-loaded entity seen from outside fluid is drawn without world fog.
       26.2 submits the level's entities before the frame graph and its clear pass run, so the entity cannot be drawn at
       submit as 26.1.2 did; it is submitted into a SubmitNodeStorage of its own instead and drawn inside the main pass,
       right after the solid features (eca$drawForceLoadedWithoutFog), by a FeatureRenderDispatcher of its own with
       FogRenderer's FogMode.NONE slice. That dispatcher has RenderBuffers of its own: the main dispatcher's
       StagedVertexBuffer is already uploaded for the frame then and takes no more draws. */
    @Redirect(method = "submitEntities",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
    private void eca$submitEntityWithForceLoadBracket(EntityRenderDispatcher dispatcher, EntityRenderState state,
                                                      CameraRenderState cameraState, double x, double y, double z,
                                                      PoseStack poseStack, SubmitNodeCollector collector) {
        eca$forceLoadedFogActive = false;
        Entity entity = net.eca.mv.EntityOf.get(state);
        if (entity != null && ForceLoadingManager.shouldForceLoad(entity)) {
            Minecraft minecraft = Minecraft.getInstance();
            Vec3 cameraPos = cameraState.pos;
            eca$markCloudOcclusion(minecraft, entity, cameraPos.x, cameraPos.y, cameraPos.z);
            Camera camera = minecraft.gameRenderer.mainCamera();
            if (camera != null && camera.getFluidInCamera() == FogType.NONE) {
                eca$forceLoadedFogActive = true;
            }
        }
        if (!eca$forceLoadedFogActive) {
            dispatcher.submit(state, cameraState, x, y, z, poseStack, collector);
            return;
        }
        try {
            dispatcher.submit(state, cameraState, x, y, z, poseStack, eca$forceLoadedNodes);
            eca$forceLoadedPending = true;
        } finally {
            eca$forceLoadedFogActive = false;
        }
    }

    /* This frame's force-loaded no-fog submits (see eca$submitEntityWithForceLoadBracket); dropped at render() HEAD if a
       frame never reached the main pass. */
    @Unique
    private final net.minecraft.client.renderer.SubmitNodeStorage eca$forceLoadedNodes = new net.minecraft.client.renderer.SubmitNodeStorage();

    @Unique
    private boolean eca$forceLoadedPending;

    /* Created on first use, kept for the session: the dispatcher and its own RenderBuffers (one section pack, the
       staging buffer). */
    @Unique
    private net.minecraft.client.renderer.RenderBuffers eca$forceLoadedBuffers;

    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher eca$forceLoadedFeatures;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void eca$resetForceLoadedNodes(CallbackInfo ci) {
        if (eca$forceLoadedPending) {
            eca$forceLoadedNodes.getSubmitsPerOrder().clear();
            eca$forceLoadedPending = false;
        }
    }

    /* In the main pass after the solid features (terrain and the level's opaque entities are in the depth buffer, the
       level lighting is set up): the force-loaded entities, all their phases, with the NONE fog slice; the pass's
       terrainFog is put back for the rest of it. */
    @Inject(method = "lambda$addMainPass$0",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V",
                     shift = At.Shift.AFTER),
            require = 1)
    private void eca$drawForceLoadedWithoutFog(CallbackInfo ci) {
        if (!eca$forceLoadedPending) {
            return;
        }
        eca$forceLoadedPending = false;
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.renderer.feature.FeatureRenderDispatcher features = eca$forceLoadedFeatures(minecraft);
        GpuBufferSlice savedFog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(minecraft.gameRenderer.fogRenderer.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
        try {
            features.renderAllFeatures(eca$forceLoadedNodes);
        } finally {
            eca$forceLoadedNodes.getSubmitsPerOrder().clear();
            eca$forceLoadedBuffers.endFrame();
            RenderSystem.setShaderFog(savedFog);
        }
    }

    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher eca$forceLoadedFeatures(Minecraft minecraft) {
        if (eca$forceLoadedFeatures == null) {
            eca$forceLoadedBuffers = new net.minecraft.client.renderer.RenderBuffers(1);
            eca$forceLoadedFeatures = new net.minecraft.client.renderer.feature.FeatureRenderDispatcher(eca$forceLoadedBuffers,
                    minecraft.getModelManager(), minecraft.getAtlasManager(), minecraft.font, minecraft.gameRenderer.gameRenderState());
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

        Frustum frustum = net.eca.mv.CullingFrustum.current;
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
