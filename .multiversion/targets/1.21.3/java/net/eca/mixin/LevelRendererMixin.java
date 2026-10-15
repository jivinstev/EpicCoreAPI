// Fork multi-version layer: 1.21.2 version of the authors' mixin. renderLevel gained a GraphicsResourceAllocator;
// renderClouds/renderSky became frame-graph passes. The cloud skip cancels the clouds pass at the point 1.21.1's
// renderClouds began (before the custom DimensionSpecialEffects.renderClouds, as HEAD did); CloudRendererMixin also
// gates CloudRenderer.render. The global skybox is drawn from a pass of its own before the main pass
// (net.eca.mv.GlobalSkyboxHook.render), because 1.21.2 adds no sky pass in a foggy or sky-less dimension where 1.21.1's
// renderSky still reached its TAIL; it is skipped where a custom DimensionSpecialEffects.renderSky drew the sky, and,
// on a frame with no sky pass, where the dimension's effects override renderSky at all (1.21.1 asked that override
// first and returned before the TAIL when it drew).
package net.eca.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

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
    private void eca$resetCloudOcclusion(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                         GameRenderer gameRenderer, LightTexture lightTexture,
                                         Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        net.eca.mv.CloudGate.occlude = false;
    }

    /* shouldRender 每次进入都会登记当前实体，isChunkCompiled 是它在同一个判断表达式里的读取方。
       但 shouldRender 返回 false 时 isChunkCompiled 不会被求值，登记值没有配对的消费点，
       只能由帧边界回收。ReceivingLevelScreen 在 tick 里也调 isChunkCompiled，
       读到跨帧残留会让“正在加载地形”在地形就绪前提前关闭。 */
    @Inject(method = "renderLevel", at = {@At("HEAD"), @At("RETURN")})
    private void eca$clearForceLoadedRenderContext(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                                   GameRenderer gameRenderer, LightTexture lightTexture,
                                                   Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        ForceLoadingManager.clearCurrentRenderingEntity();
    }

    // 1.21.2: clouds are a frame-graph pass (lambda$addCloudsPass$6). Cancel it where 1.21.1's renderClouds HEAD was:
    // before the custom DimensionSpecialEffects.renderClouds hook, so a dimension that draws its own clouds is skipped
    // too. The pass executes after the main pass, so the flag eca$markCloudOcclusion set this frame is already final.
    @Inject(method = "lambda$addCloudsPass$6",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderClouds(Lnet/minecraft/client/multiplayer/ClientLevel;IFDDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)Z"),
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

    /* Per DimensionSpecialEffects class: does it override renderSky (NeoForge's default lives on
       IDimensionSpecialEffectsExtension and returns false)? Read reflectively once per class. */
    @Unique
    private static final java.util.Map<Class<?>, Boolean> ECA_CUSTOM_SKY_CLASSES = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void eca$resetCustomSky(CallbackInfo ci) {
        eca$customSkyDrawn = false;
        eca$skyPassAsked = false;
    }

    /* No sky pass this frame (foggy or boss-fog dimension, SkyType NONE, blindness/darkness, under fluid): 1.21.3 never
       calls renderSky then, but 1.21.1 still asked it before reaching the TAIL. A dimension with its own renderSky draws
       its sky there, so the skybox stays out, as it did when that override returned true. */
    @Unique
    private static boolean eca$hasCustomSkyRenderer(net.minecraft.client.renderer.DimensionSpecialEffects effects) {
        if (effects == null) {
            return false;
        }
        return ECA_CUSTOM_SKY_CLASSES.computeIfAbsent(effects.getClass(), type -> {
            try {
                Class<?> owner = type.getMethod("renderSky", net.minecraft.client.multiplayer.ClientLevel.class, int.class,
                        float.class, Matrix4f.class, Camera.class, Matrix4f.class, Runnable.class).getDeclaringClass();
                return !owner.isInterface() && owner != net.minecraft.client.renderer.DimensionSpecialEffects.class;
            } catch (NoSuchMethodException | SecurityException e) {
                return false;
            }
        });
    }

    @ModifyExpressionValue(method = "lambda$addSkyPass$12",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;renderSky(Lnet/minecraft/client/multiplayer/ClientLevel;IFLorg/joml/Matrix4f;Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Ljava/lang/Runnable;)Z"),
            require = 1)
    private boolean eca$recordCustomSky(boolean handled) {
        eca$skyPassAsked = true;
        eca$customSkyDrawn = handled;
        return handled;
    }

    /* The authors' renderSky TAIL: a pass of its own on the main target, ordered after the sky pass (when there is
       one) and before the main pass, with the sky fog renderSky had set up. frustumMatrix is unused: see below. */
    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addMainPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/culling/Frustum;Lnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/FogParameters;ZZLnet/minecraft/client/DeltaTracker;Lnet/minecraft/util/profiling/ProfilerFiller;)V"),
            require = 1)
    private void eca$addGlobalSkyboxPass(com.mojang.blaze3d.resource.GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                         GameRenderer gameRenderer, LightTexture lightTexture,
                                         Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci,
                                         @Local FrameGraphBuilder frameGraph, @Local(ordinal = 1) FogParameters skyFog) {
        net.eca.mv.GlobalSkyboxHook.passOwned = true;
        FramePass pass = frameGraph.addPass("eca_global_skybox");
        this.targets.main = pass.readsAndWrites(this.targets.main);
        pass.executes(() -> {
            if (eca$skyPassAsked ? eca$customSkyDrawn
                    : Minecraft.getInstance().level != null && eca$hasCustomSkyRenderer(Minecraft.getInstance().level.effects())) {
                return;
            }
            RenderSystem.setShaderFog(skyFog);
            RenderStateShard.MAIN_TARGET.setupRenderState();
            // 1.21.1's renderSky ran before renderLevel pushed frustumMatrix onto the model-view stack, so the authors'
            // PoseStack carried it; inside a 1.21.2 pass the stack already has it (the vanilla sky pass draws with a
            // bare PoseStack), so the PoseStack starts from identity here -- the same net transform, applied once.
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

    @Inject(method = "renderEntity", at = @At("HEAD"))
    private void eca$beginForceLoadedEntityRender(Entity entity, double camX, double camY, double camZ,
                                                   float partialTick, PoseStack poseStack,
                                                   MultiBufferSource bufferSource, CallbackInfo ci) {
        eca$forceLoadedFogActive = false;
        if (!ForceLoadingManager.shouldForceLoad(entity)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();

        eca$markCloudOcclusion(minecraft, entity, camX, camY, camZ);

        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null || camera.getFluidInCamera() != FogType.NONE) {
            return;
        }

        flushEntityBuffers(minecraft);
        net.eca.mv.Mv.suspendFog();
        eca$forceLoadedFogActive = true;
    }

    @Inject(method = "renderEntity", at = @At("RETURN"))
    private void eca$endForceLoadedEntityRender(Entity entity, double camX, double camY, double camZ,
                                                 float partialTick, PoseStack poseStack,
                                                 MultiBufferSource bufferSource, CallbackInfo ci) {
        if (!eca$forceLoadedFogActive) {
            return;
        }
        flushEntityBuffers(Minecraft.getInstance());
        net.eca.mv.Mv.restoreFog();
        eca$forceLoadedFogActive = false;
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
        float cloudBottom = minecraft.level.effects().getCloudHeight();
        if (Float.isNaN(cloudBottom)) {
            return;
        }

        AABB box = net.eca.mv.Mv.cullingBox(entity);
        float cloudTop = cloudBottom + ECA_CLOUD_SLAB_THICKNESS;
        // 相机与实体的纵向跨度必须真的切过云层平板，否则视线不经过云
        if (Math.max(camY, box.maxY) <= cloudBottom || Math.min(camY, box.minY) >= cloudTop) {
            return;
        }

        Frustum frustum = ((LevelRenderer) (Object) this).getFrustum();
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

    @Unique
    private static void flushEntityBuffers(Minecraft minecraft) {
        minecraft.renderBuffers().bufferSource().endBatch();
        minecraft.renderBuffers().outlineBufferSource().endOutlineBatch();
    }
}
