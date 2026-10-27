// Fork multi-version layer: 26.1.2 (renderLevel takes the CameraRenderState, one model-view Matrix4fc and the chunk sections) copy of the
// 1.21.10 copy of the 1.21.8 overlay. 1.21.9 SUBMITS entities: there is no renderEntity; submitEntities
// loops the visible entity render states and hands each to EntityRenderDispatcher.submit, and the drawing happens later, in
// FeatureRenderDispatcher.renderAllFeatures. So the per-entity bracket (cloud occlusion, fog flag) is a @Redirect of that call,
// the frustum the bracket needs (the cullingFrustum field is gone) is taken from extractVisibleEntities' argument, and
// renderLevel gained a third Matrix4f (the culling projection).
// 1.21.8 copy of the 1.21.5 overlay: renderLevel lost its GameRenderer and LightTexture and gained a GraphicsResourceAllocator, the fog buffer, the fog colour and a render-sky flag (1.21.6 shape; shared row in shared/1.21.8/renames.tsv);
// renderClouds/renderSky became frame-graph passes, so the cloud skip moved to CloudRendererMixin and the global
// skybox to net.eca.mv.GlobalSkyboxHook (RenderLevelStageEvent AFTER_SKY, which runs where renderSky's TAIL did).
// 26.3 (#32, re-derived from the 26.3 sources and javap, not inherited from 26.2): clouds are no longer a frame pass of
// their own. They are drawn inside the main pass, by executeClassicTransparency (CustomCloudsRenderer.renderClouds, else
// CloudRenderer.render) or, with improved transparency, by executeOit (renderCloudsOit, else CloudRenderer.renderOit); each
// first computes "clouds on and cloud colour alpha > 0". The skip makes that test false (the alpha it reads becomes 0), so
// both the custom renderer and vanilla's are skipped on both paths, as 1.21.1's renderClouds HEAD cancel did. The sky pass
// lambda is lambda$addSkyPass$0 (renderSky now also takes the sky fog); the skybox gets its own pass before addMainPass,
// whose 26.3 descriptor is (FrameGraphBuilder, PreparedFrame, GpuBufferSlice, ChunkSectionsToRender, boolean).
// 26.3 (review of #32/#33, F3): on a frame with no sky pass the skybox pass also stands down where the dimension has a
// CustomSkyboxRenderer at all (1.21.1 asked that override first and returned before the TAIL when it drew). 26.3's
// LevelExtractor.extract assigns LevelRenderState.customSkyboxRenderer every frame (CustomEnvironmentEffectsRendererManager
// .getCustomSkyboxRenderer, null when none) before GameRenderer.renderLevel calls render(); LevelRenderer keeps that state
// in its package-private final levelRenderState field, which lambda$addSkyPass$0 null-checks before renderSky.
package net.eca.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.renderpearl.api.vertex.VertexFormat;
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
    // 26.3: render(...) lost the DeltaTracker and model-view arguments and gained consistentDepthRequired
    @Inject(method = "render", at = @At("HEAD"))
    private void eca$resetCloudOcclusion(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
                                         net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, boolean consistentDepth, CallbackInfo ci) {
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
    private void eca$clearForceLoadedRenderContext(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
                                                   net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
                                                   GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, boolean consistentDepth, CallbackInfo ci) {
        ForceLoadingManager.clearCurrentRenderingEntity();
    }

    // 1.21.2: clouds are a frame-graph pass; net.eca.mixin.CloudRendererMixin cancels them (fork layer).
    // 26.3 (#32): CloudRendererMixin only reaches vanilla's CloudRenderer, so a dimension with its own clouds
    // (LevelRenderState.customCloudsRenderer) was still drawn. Both execute paths decide "draw clouds" from the cloud
    // colour's alpha (ARGB.alpha, called once in each); reading 0 there while occluding skips the custom renderer and
    // vanilla's together. CloudGate.occlude is final by then: submitFeatures runs at the top of render(), before the
    // frame graph that runs the main pass is even built.
    @ModifyExpressionValue(method = "executeClassicTransparency",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;alpha(I)I"), require = 1)
    private int eca$skipOccludingCloudsClassic(int alpha) {
        return net.eca.mv.CloudGate.occlude ? 0 : alpha;
    }

    @ModifyExpressionValue(method = "executeOit",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;alpha(I)I"), require = 1)
    private int eca$skipOccludingCloudsOit(int alpha) {
        return net.eca.mv.CloudGate.occlude ? 0 : alpha;
    }

    // ==================== 全局天空盒渲染 (fork layer, 1.21.2+) ====================

    @Shadow
    @Final
    private LevelTargetBundle targets;

    /* true when this frame's sky pass was drawn by a custom CustomSkyboxRenderer.renderSky: 1.21.1's renderSky returned
       before its TAIL then, so the authors' skybox was not drawn. Cleared at render() HEAD every frame. */
    @Unique
    private boolean eca$customSkyDrawn;

    /* true when this frame's sky pass reached the custom renderSky call (eca$customSkyDrawn then holds its answer). */
    @Unique
    private boolean eca$skyPassAsked;

    /* 26.3: LevelExtractor.extract assigns customSkyboxRenderer (null when the dimension has none) before render(). */
    @Shadow
    @Final
    LevelRenderState levelRenderState;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void eca$resetCustomSky(CallbackInfo ci) {
        eca$customSkyDrawn = false;
        eca$skyPassAsked = false;
    }

    @ModifyExpressionValue(method = "lambda$addSkyPass$0",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/CustomSkyboxRenderer;renderSky(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/state/level/SkyRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)Z"),
            require = 1)
    private boolean eca$recordCustomSky(boolean handled) {
        eca$skyPassAsked = true;
        eca$customSkyDrawn = handled;
        return handled;
    }

    /* 26.3 (#32): the authors' renderSky TAIL as a frame pass of its own on the main target, added right before
       addMainPass, so it runs after the clear and the sky pass (when render() adds one) and before the terrain. AfterSky is
       posted only inside the sky pass, which render() skips when shouldRenderSky is false and addSkyPass skips for
       lava / powder snow, a sky-blocking effect and Skybox NONE; it is also posted after a custom renderSky drew. */
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Z)V"),
            require = 1)
    private void eca$addGlobalSkyboxPass(GraphicsResourceAllocator allocator, boolean renderBlockOutline,
                                         net.minecraft.client.renderer.state.level.CameraRenderState cameraState,
                                         GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, boolean consistentDepth,
                                         CallbackInfo ci, @Local FrameGraphBuilder frameGraph) {
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
            // render() hands its terrain fog to the sky pass (as skyFog) and sets it in the main pass; set the same here.
            RenderSystem.setShaderFog(fogBuffer);
            // render() has pushed cameraState.viewRotationMatrix onto RenderSystem's model-view stack (popped after the
            // graph has run), and LegacyGl / LegacyRenderType upload that stack as ModelViewMat: the PoseStack starts
            // from identity, so the view rotation is applied once. Depth: no render pass is open here (the helpers open
            // their own on the main target), and the clear pass has just set depth to 0.0, the far plane of 26.3's
            // reverse-Z (Projection swaps near and far). The helpers' default compare and LegacyRenderType's LEQUAL shard
            // are GREATER_THAN_OR_EQUAL, so every fragment inside the frustum passes against 0.0 as it passed against
            // 1.21.1's cleared 1.0; the texture skybox writes no depth (depthMask(false)), as vanilla's sky writes none.
            net.eca.mv.GlobalSkyboxHook.render(new Matrix4f(), Minecraft.getInstance().gameRenderer.mainCamera());
        });
    }

    /* 26.3 (#33): render() submits every feature (submitFeatures) and prepares the frame before it builds the frame graph,
       so a pass an extension draws at once from a level submit would land before the clear pass. net.eca.mv.SubmitPhase
       sends those passes to the after-level queue while this flag is up. */
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
    // 26.3: isSectionCompiledAndVisible(pos, chunkFadeDuration)
    private void eca$forceLoadedChunkCheck(BlockPos pos, long chunkFadeDuration, CallbackInfoReturnable<Boolean> cir) {
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
       StagedVertexBuffer is already uploaded for the frame then and takes no more draws.
       26.3: submitEntities runs inside render() again, before vanilla prepares its frame; the force-loaded frame is
       prepared right after it (eca$prepareForceLoadedWithoutFog) and drawn into the open solid pass. */
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

    /* 26.3: this frame's prepared force-loaded submits (prepared, and their staged vertices uploaded, right after vanilla
       prepares its own -- outside any render pass -- drawn in the main pass, closed at render()'s end). */
    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame eca$forceLoadedFrame;

    /* Created on first use, kept for the session: the dispatcher and its own RenderBuffers (one section pack, the
       staging buffer). */
    @Unique
    private net.minecraft.client.renderer.RenderBuffers eca$forceLoadedBuffers;

    @Unique
    private net.minecraft.client.renderer.feature.FeatureRenderDispatcher eca$forceLoadedFeatures;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void eca$resetForceLoadedNodes(CallbackInfo ci) {
        eca$closeForceLoadedFrame();
        if (eca$forceLoadedPending) {
            eca$forceLoadedNodes.getSubmitsPerOrder().clear();
            eca$forceLoadedPending = false;
        }
    }

    /* 26.3: FeatureRenderDispatcher prepares a frame (sorts the submits, uploads its staged vertex buffer) apart from
       executing it in a pass, and no upload can be made inside the main pass; so the force-loaded submits are prepared
       here, where vanilla prepares the level's own. */
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;",
                     shift = At.Shift.AFTER),
            require = 1)
    private void eca$prepareForceLoadedWithoutFog(CallbackInfo ci) {
        if (!eca$forceLoadedPending) {
            return;
        }
        eca$forceLoadedPending = false;
        eca$forceLoadedFrame = eca$forceLoadedFeatures(Minecraft.getInstance()).prepareFrame(eca$forceLoadedNodes);
    }

    /* In the main pass after the solid features (terrain and the level's opaque entities are in the depth buffer, the
       level lighting is set up): the force-loaded entities, all their phases, with the NONE fog slice. 26.3 binds the
       Fog uniform into the pass once (RenderSystem.bindDefaultUniforms), so the slice is set on the pass and the
       pass's terrainFog (RenderSystem's shader fog, set at the pass's start) is put back for the rest of it. */
    @Inject(method = "executeSolid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid(Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
                     shift = At.Shift.AFTER),
            require = 1)
    private void eca$drawForceLoadedWithoutFog(net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunkSectionsToRender,
                                               net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame featureFrame,
                                               com.mojang.renderpearl.api.commands.RenderPass renderPass, CallbackInfo ci) {
        if (eca$forceLoadedFrame == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        renderPass.setUniform("Fog", minecraft.gameRenderer.fogRenderer.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
        try {
            net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(renderPass, eca$forceLoadedFrame);
        } finally {
            GpuBufferSlice terrainFog = RenderSystem.getShaderFog();
            if (terrainFog != null) {
                renderPass.setUniform("Fog", terrainFog);
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void eca$endForceLoadedFrame(CallbackInfo ci) {
        eca$closeForceLoadedFrame();
    }

    @Unique
    private void eca$closeForceLoadedFrame() {
        if (eca$forceLoadedFrame == null) {
            return;
        }
        try {
            eca$forceLoadedFrame.close();
        } finally {
            eca$forceLoadedFrame = null;
            eca$forceLoadedNodes.getSubmitsPerOrder().clear();
            eca$forceLoadedBuffers.endFrame();
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
        return renderer.shouldRender(entity, frustum, camX, camY, camZ, 1.0f);
    }
}
