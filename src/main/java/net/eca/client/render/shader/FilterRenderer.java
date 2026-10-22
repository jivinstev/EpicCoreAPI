// Fork multi-version layer: 26.2 overlay of the authors' FilterRenderer (26.2 port: stage listeners, ProjectionMatrixBuffer,
// reverse-Z depth flips, extract -> submit -> feature-dispatch entity drawing, sized depth copies). Review fix (#33, F1),
// derived with javap/sources of minecraft-patched-26.2.0.90:
//  - LevelRenderer.render prepares the level's FeatureRenderDispatcher.PreparedFrame before it builds the frame graph and
//    closes it only after the graph ran; AfterTranslucentFeatures is posted inside the main pass, while that frame (the
//    dispatcher's ONE PreparedFrame, shared with GameRenderer) is in use. renderAllFeatures(storage) there calls
//    prepareFrame -> begin, which refuses a frame in use (IllegalStateException), and would re-upload the shared staged
//    vertex buffer under the level's later phases. Even drawn, PreparedRenderType.drawFromBuffer opens its RenderPass on
//    its OutputTarget's render target (the main one) unless RenderSystem.outputColorTextureOverride /
//    outputDepthTextureOverride are set, so a raw spotlight FBO bind was ignored.
//  - So, as 26.3 does: the scene depth is copied into the spotlight target at AfterTranslucentFeatures (raw GL blit,
//    the authors' moment), and the crosshair entity is drawn into it at AfterLevel (GameRenderer.renderLevel posts it
//    after render returned, so the level's frame is closed and every clear of the main target is behind us), with both
//    output overrides naming the spotlight's views for the whole submit/prepare/execute, and the view rotation pushed
//    back on the model-view stack (RenderType.prepare reads it; render popped it). The spotlight is a TextureTarget
//    whose depth follows the main target's useStencil, i.e. the main target's own depth format.
// Under Fabulous graphics the item-entity/translucent/outline targets are not redirected, as on 1.21.1.
// An overlay passes down the `inherits` chain WITHOUT rename rows; 26.3 carries its own FilterRenderer overlay.
package net.eca.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.EcaMod;
import net.eca.client.BossShowScreenEffectState;
import net.eca.util.filter.FilterType;
import net.minecraft.client.Minecraft;

import net.eca.mv.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.eca.mv.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
@SuppressWarnings("removal")
@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public class FilterRenderer {

    // 1.21.11 (as 1.21.8/1.21.10): the projectionMatrix LevelRenderer.renderLevel was given this frame (GameRenderer.renderLevel's
    // level projection, the matrix the depth buffer is written with); set by LevelRendererMixin at renderLevel HEAD.
    // 26.1.2: set by GameRendererPostLevelMixin where GameRenderer.renderLevel uploads that projection
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();

    public static void captureLevelProjection(org.joml.Matrix4fc projection) {
        LEVEL_PROJECTION.set(projection);
    }

    // 26.2: the main camera's depthFar (max(RD x 64, cloud range x 16), raised by CameraDepthFarMixin to the force-load
    // distance + 32), the far plane the level projection and its depth buffer use; set by CameraDepthFarMixin
    private static float levelDepthFar;

    public static void captureDepthFar(float depthFar) {
        levelDepthFar = depthFar;
    }

    /* 26.2: the captured level projection is REVERSE-Z (Projection.getMatrix: setPerspective(fov, aspect, zFar, zNear, ...)),
       while filters/cosmos.fsh flips the sampled depth back to forward-Z and rebuilds NDC (depth * 2 - 1) against a forward
       JOML perspective (near 0.05, far the camera's depthFar, see captureDepthFar). So the
       captured matrix keeps its x / y / w rows -- FOV modifiers, aspect, bobHurt/bobView and the portal warp, which are
       affine view-space transforms B on its right (P = Persp * B) -- and gets the forward perspective's z row:
       row2(Persp_fwd * B) = a * row2(B) + b * row3(B) = -a * row3(P) + b * (0, 0, 0, 1), since row3(Persp) = (0, 0, -1, 0)
       and row3(B) = (0, 0, 0, 1). Before the first capture it is the old forward perspective. */
    private static Matrix4f eca$forwardLevelProjection(int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        float near = 0.05f;
        // 26.2: the camera's real far plane (CameraDepthFarMixin), so the forward depth matches what the depth buffer holds;
        // the old estimate only before the first camera update
        float far = levelDepthFar > near ? levelDepthFar
                : Math.max(128.0f, mc.options.getEffectiveRenderDistance() * 16.0f * 4.0f);
        org.joml.Vector4f w = LEVEL_PROJECTION.getRow(3, new org.joml.Vector4f());
        if (Math.abs(w.z) < 1.0e-6f) {
            float fov = (float) Math.toRadians(mc.options.fov().get());
            return new Matrix4f().perspective(fov, (float) width / (float) height, near, far);
        }
        float a = -(far + near) / (far - near);
        float b = -2.0f * far * near / (far - near);
        return new Matrix4f(LEVEL_PROJECTION).setRow(2, new org.joml.Vector4f(-a * w.x, -a * w.y, -a * w.z, -a * w.w + b));
    }

    private static ShaderInstance sketchShader;
    private static ShaderInstance spotlightShader;
    private static ShaderInstance matrixShader;
    private static ShaderInstance rainShader;
    private static ShaderInstance desertShader;
    private static ShaderInstance snowShader;
    private static ShaderInstance toxicShader;
    private static ShaderInstance cosmosShader;
    private static ShaderInstance bossShowEffectShader;
    private static long matrixStartNanos;
    private static long rainStartNanos;
    private static long desertStartNanos;
    private static long snowStartNanos;
    private static long toxicStartNanos;
    private static long cosmosStartNanos;
    private static final Set<FilterType> activeFilters = EnumSet.noneOf(FilterType.class);
    private static FilterType bossShowFilter;
    private static float bossShowFilterStrength = 1.0F;
    private static float bossShowFilterSpeed = 1.0F;

    private static int copyFbo = -1;
    private static int depthCopyTexture = -1;
    private static int colorCopyTexture = -1;
    private static int copyWidth;
    private static int copyHeight;

    // 26.2 (#33): the spotlight mask is a TextureTarget, so the entity's RenderType draws can be pointed at it
    private static com.mojang.blaze3d.pipeline.TextureTarget spotlightTarget;
    // 26.2 (#33): set when AfterTranslucentFeatures copied the scene depth this frame (see onRenderLevelStage)
    private static boolean spotlightDepthTaken;

    /* 宇宙滤镜专用：在实体绘制前（AFTER_CUTOUT_BLOCKS_LAYER）快照纯地形深度，
       用于 AFTER_LEVEL 合成时区分实体像素与方块像素（阶段 AFTER_CUTOUT_BLOCKS） */
    private static int cosmosTerrainFbo = -1;
    private static int cosmosTerrainDepthTexture = -1;
    private static int cosmosTerrainWidth;
    private static int cosmosTerrainHeight;

    // 26.2 (#33): the depth format each copy was allocated in (the main target's, see allocateDepthCopy)
    private static com.mojang.blaze3d.GpuFormat copyDepthFormat;
    private static com.mojang.blaze3d.GpuFormat cosmosTerrainDepthFormat;

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/sketch"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> sketchShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/spotlight"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> spotlightShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/matrix"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> matrixShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/rain"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> rainShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/desert"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> desertShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/snow"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> snowShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/toxic"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> toxicShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/cosmos"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> cosmosShader = instance
        );
        event.registerShader(
                EcaShaderInstance.create(
                        event.getResourceProvider(),
                        Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "filters/boss_show_effect"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                instance -> bossShowEffectShader = instance
        );
    }

    public static void enable(FilterType filter) {
        activeFilters.add(filter);
    }

    public static void disable(FilterType filter) {
        activeFilters.remove(filter);
    }

    public static boolean isActive(FilterType filter) {
        return activeFilters.contains(filter);
    }

    public static Set<FilterType> getActiveFilters() {
        return EnumSet.copyOf(activeFilters);
    }

    public static void setBossShowFilter(FilterType filter, float strength, float speed) {
        bossShowFilter = filter;
        bossShowFilterStrength = Mth.clamp(strength, 0.0F, 1.0F);
        bossShowFilterSpeed = Math.max(0.0F, speed);
    }

    public static void clearBossShowFilter() {
        bossShowFilter = null;
        bossShowFilterStrength = 1.0F;
        bossShowFilterSpeed = 1.0F;
    }

    public static void clearAll() {
        activeFilters.clear();
        clearBossShowFilter();
        destroyCopyTargets();
        destroySpotlightTargets();
        destroyCosmosTerrainTarget();
        matrixStartNanos = 0;
        rainStartNanos = 0;
        desertStartNanos = 0;
        snowStartNanos = 0;
        toxicStartNanos = 0;
        cosmosStartNanos = 0;
    }

    private static net.minecraft.client.renderer.ProjectionMatrixBuffer orthoBuffer;

    private static com.mojang.blaze3d.buffers.GpuBufferSlice orthoSlice(Matrix4f ortho) {
        if (orthoBuffer == null) {
            orthoBuffer = new net.minecraft.client.renderer.ProjectionMatrixBuffer("eca filter ortho");
        }
        return orthoBuffer.getBuffer(ortho);
    }

    // RenderLevelStageEvent is abstract on 1.21.8 (the bus refuses a listener for it): one listener per stage this
    // filter reads, all sharing the authors' single handler body below (CATALOG X33)
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        onRenderLevelStage(event);
    }

    @SubscribeEvent
    public static void onAfterEntities(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        onRenderLevelStage(event);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        onRenderLevelStage(event);
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (activeFilters.isEmpty() && bossShowFilter == null) return;

        boolean afterLevel = event instanceof RenderLevelStageEvent.AfterLevel;

        if (isRenderedFilter(FilterType.SPOTLIGHT) && spotlightShader != null) {
            // 26.2 (#33): depth at the authors' moment (inside the main pass, raw GL only); the entity itself once the
            // level's PreparedFrame is closed (AfterLevel), then the composite
            if (event instanceof RenderLevelStageEvent.AfterTranslucentFeatures) {
                copySpotlightDepth();
                return;
            }
            if (afterLevel) {
                if (!spotlightDepthTaken) {
                    copySpotlightDepth();
                }
                spotlightDepthTaken = false;
                captureSpotlightEntity(event);
                renderSpotlight(event);
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.MATRIX) && matrixShader != null) {
            if (afterLevel) {
                renderMatrix();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.RAIN) && rainShader != null) {
            if (afterLevel) {
                renderRain();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.DESERT) && desertShader != null) {
            if (afterLevel) {
                renderDesert();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.SNOW) && snowShader != null) {
            if (afterLevel) {
                renderSnow();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.TOXIC) && toxicShader != null) {
            if (afterLevel) {
                renderToxic();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.COSMOS)) {
            if (event instanceof RenderLevelStageEvent.AfterOpaqueBlocks) {
                captureCosmosTerrainDepth();
                return;
            }
            if (afterLevel && cosmosShader != null) {
                renderCosmos(event);
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.SKETCH) && sketchShader != null) {
            if (afterLevel) {
                renderSketch();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossShowEffectRender(RenderLevelStageEvent.AfterLevel event) {
        if (!BossShowScreenEffectState.hasShaderEffects()) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderFilterPass(bossShowEffectShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width,
                    (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set((System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000.0F);
            }
            BossShowScreenEffectState.applyShaderUniforms(shader, partialTick);
        });
    }

    private static boolean isRenderedFilter(FilterType filter) {
        return bossShowFilter != null ? bossShowFilter == filter && bossShowFilterStrength > 0.0F
            : activeFilters.contains(filter);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BossShowScreenEffectState.clear();
        clearAll();
    }

    /* 26.2 (#33): the main target's depth is a SIZED texture -- D32_FLOAT (GL_DEPTH_COMPONENT32F), or the depth-stencil
       format NeoForge picks when the target has a stencil -- where 1.21.1's was the unsized GL_DEPTH_COMPONENT these copies
       were made with. glBlitFramebuffer copies depth only between identical depth/stencil formats and otherwise fails
       with GL_INVALID_OPERATION, which drops the whole blit (the colour copy too), and an unsized GL_DEPTH_COMPONENT never
       resolves to a float format. So every depth copy is allocated in the main target's own format. */
    private static com.mojang.blaze3d.GpuFormat mainDepthFormat() {
        com.mojang.blaze3d.textures.GpuTexture depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture();
        return depth != null ? depth.getFormat() : com.mojang.blaze3d.GpuFormat.D32_FLOAT;
    }

    private static void allocateDepthCopy(int width, int height) {
        com.mojang.blaze3d.GpuFormat format = mainDepthFormat();
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, com.mojang.blaze3d.opengl.GlConst.toGlInternalId(format), width, height, 0,
                com.mojang.blaze3d.opengl.GlConst.toGlExternalId(format), com.mojang.blaze3d.opengl.GlConst.toGlType(format), null);
    }

    private static int depthAttachment() {
        return mainDepthFormat().hasStencilAspect() ? GL30.GL_DEPTH_STENCIL_ATTACHMENT : GL30.GL_DEPTH_ATTACHMENT;
    }

    private static void ensureCopyTargets(int width, int height) {
        if (copyFbo != -1 && copyWidth == width && copyHeight == height && copyDepthFormat == mainDepthFormat()) return;
        destroyCopyTargets();

        depthCopyTexture = GlStateManager._genTexture();
        GlStateManager._bindTexture(depthCopyTexture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        allocateDepthCopy(width, height);

        colorCopyTexture = GlStateManager._genTexture();
        GlStateManager._bindTexture(colorCopyTexture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, width, height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, null);

        copyFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, copyFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthAttachment(),
                GL11.GL_TEXTURE_2D, depthCopyTexture, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorCopyTexture, 0);

        copyWidth = width;
        copyHeight = height;
        copyDepthFormat = mainDepthFormat();
    }

    private static void destroyCopyTargets() {
        if (depthCopyTexture != -1) {
            GlStateManager._deleteTexture(depthCopyTexture);
            depthCopyTexture = -1;
        }
        if (colorCopyTexture != -1) {
            GlStateManager._deleteTexture(colorCopyTexture);
            colorCopyTexture = -1;
        }
        if (copyFbo != -1) {
            GL30.glDeleteFramebuffers(copyFbo);
            copyFbo = -1;
        }
    }

    private static void ensureSpotlightTargets(int width, int height) {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        boolean reusable = spotlightTarget != null
                && spotlightTarget.width == width && spotlightTarget.height == height
                && spotlightTarget.useStencil == main.useStencil
                && spotlightTarget.getDepthTexture() != null
                && spotlightTarget.getDepthTexture().getFormat() == mainDepthFormat();
        if (reusable) return;
        destroySpotlightTargets();
        // RGBA8 colour as the authors' texture was; createBuffers picks the depth format from useStencil exactly as the
        // main target does, so the depth blit from the main target is between identical formats
        spotlightTarget = new com.mojang.blaze3d.pipeline.TextureTarget("eca_spotlight", width, height, true,
                main.useStencil, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
    }

    private static void destroySpotlightTargets() {
        if (spotlightTarget != null) {
            spotlightTarget.destroyBuffers();
            spotlightTarget = null;
        }
        spotlightDepthTaken = false;
    }

    private static void ensureCosmosTerrainTarget(int width, int height) {
        if (cosmosTerrainFbo != -1 && cosmosTerrainWidth == width && cosmosTerrainHeight == height && cosmosTerrainDepthFormat == mainDepthFormat()) return;
        destroyCosmosTerrainTarget();

        cosmosTerrainDepthTexture = GlStateManager._genTexture();
        GlStateManager._bindTexture(cosmosTerrainDepthTexture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        allocateDepthCopy(width, height);

        cosmosTerrainFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, cosmosTerrainFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, depthAttachment(),
                GL11.GL_TEXTURE_2D, cosmosTerrainDepthTexture, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        cosmosTerrainWidth = width;
        cosmosTerrainHeight = height;
        cosmosTerrainDepthFormat = mainDepthFormat();
    }

    private static void destroyCosmosTerrainTarget() {
        if (cosmosTerrainDepthTexture != -1) {
            GlStateManager._deleteTexture(cosmosTerrainDepthTexture);
            cosmosTerrainDepthTexture = -1;
        }
        if (cosmosTerrainFbo != -1) {
            GL30.glDeleteFramebuffers(cosmosTerrainFbo);
            cosmosTerrainFbo = -1;
        }
    }

    private static void captureCosmosTerrainDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCosmosTerrainTarget(width, height);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, cosmosTerrainFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
    }

    @SuppressWarnings("deprecation")
    private static void renderSketch() {
        renderFilterPass(sketchShader, shader -> {
            if (shader.getUniform("ScreenSize") != null) {
                Minecraft mc = Minecraft.getInstance();
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderMatrix() {
        if (matrixStartNanos == 0) {
            matrixStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - matrixStartNanos) / 1_000_000_000.0f;
        renderFilterPass(matrixShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderRain() {
        if (rainStartNanos == 0) {
            rainStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - rainStartNanos) / 1_000_000_000.0f;
        renderFilterPass(rainShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderDesert() {
        if (desertStartNanos == 0) {
            desertStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - desertStartNanos) / 1_000_000_000.0f;
        renderFilterPass(desertShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderSnow() {
        if (snowStartNanos == 0) {
            snowStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - snowStartNanos) / 1_000_000_000.0f;
        renderFilterPass(snowShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderToxic() {
        if (toxicStartNanos == 0) {
            toxicStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - toxicStartNanos) / 1_000_000_000.0f;
        renderFilterPass(toxicShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderSpotlight(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCopyTargets(width, height);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);

        net.eca.mv.LegacyGl.disableDepthTest();
        net.eca.mv.LegacyGl.depthMask(false);
        net.eca.mv.LegacyGl.disableCull();
        net.eca.mv.LegacyGl.disableBlend();

        com.mojang.blaze3d.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, (float) width, (float) height, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(orthoSlice(ortho), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().identity();

            net.eca.mv.LegacyGl.setShader(() -> spotlightShader);
            net.eca.mv.LegacyGl.setShaderTexture(0, depthCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(1, colorCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(2, spotlightTarget.getColorTextureView());
            if (spotlightShader.getUniform("ScreenSize") != null) {
                spotlightShader.getUniform("ScreenSize").set((float) width, (float) height);
            }
            if (spotlightShader.getUniform("FilterStrength") != null) {
                spotlightShader.getUniform("FilterStrength").set(
                    bossShowFilter != null ? bossShowFilterStrength : 1.0F);
            }

            drawFullscreenQuad(width, height);
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(savedProj, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
            net.eca.mv.LegacyGl.enableCull();
            net.eca.mv.LegacyGl.depthMask(true);
            net.eca.mv.LegacyGl.enableDepthTest();
            net.eca.mv.LegacyGl.enableBlend();
        }
    }

    /** 26.2 (#33): the scene depth into the spotlight target, as the authors' capture began; raw GL, so it is legal inside
     *  the main frame pass. */
    private static void copySpotlightDepth() {
        RenderTarget mainTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int w = mainTarget.width;
        int h = mainTarget.height;
        ensureSpotlightTargets(w, h);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(spotlightTarget));
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
        spotlightDepthTaken = true;
    }

    /** The rest of the authors' capture, at AfterLevel on 26.2: a transparent mask colour, then the crosshair entity drawn
     *  over the copied depth into the spotlight target. */
    @SuppressWarnings("deprecation")
    private static void captureSpotlightEntity(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.crosshairPickEntity;
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        ensureSpotlightTargets(mainTarget.width, mainTarget.height);
        RenderSystem.getDevice().createCommandEncoder()
                .clearColorTexture(spotlightTarget.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        boolean drawable = target != null && !target.isRemoved() && mc.level != null && target.level() == mc.level;
        if (!drawable) {
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double x = Mth.lerp(partialTick, target.xOld, target.getX()) - camPos.x;
        double y = Mth.lerp(partialTick, target.yOld, target.getY()) - camPos.y;
        double z = Mth.lerp(partialTick, target.zOld, target.getZ()) - camPos.z;

        PoseStack poseStack = event.getPoseStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        // extract -> submit -> feature dispatch, through a storage of this entity alone (as vanilla's
        // PictureInPictureRenderer does). renderAllFeatures prepares (RenderType.prepare snapshots the model-view, which
        // render popped: push the view rotation again) and executes at once; each PreparedRenderType draw opens its pass
        // on the overrides when set, so the whole call runs with them naming the spotlight's views. Earlier overrides
        // and the model-view stack are put back in the finally.
        com.mojang.blaze3d.textures.GpuTextureView priorColor = RenderSystem.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView priorDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().mul(event.getModelViewMatrix());
            RenderSystem.outputColorTextureOverride = spotlightTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = spotlightTarget.getDepthTextureView();
            net.minecraft.client.renderer.SubmitNodeStorage storage = new net.minecraft.client.renderer.SubmitNodeStorage();
            dispatcher.submit(dispatcher.extractEntity(target, partialTick), event.getLevelRenderState().cameraRenderState,
                    x, y, z, poseStack, storage);
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage);
        } finally {
            RenderSystem.outputColorTextureOverride = priorColor;
            RenderSystem.outputDepthTextureOverride = priorDepth;
            RenderSystem.getModelViewStack().popMatrix();
        }

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
    }

    private static void drawFullscreenQuad(int width, int height) {
        BufferBuilder builder = net.eca.mv.Tesselator.getInstance().begin(com.mojang.blaze3d.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex((float) (0.0f), (float) (0.0f), (float) (0.0f)).setUv(0.0f, 0.0f);
        builder.addVertex((float) width, 0.0f, 0.0f).setUv(1.0f, 0.0f);
        builder.addVertex((float) width, (float) height, 0.0f).setUv(1.0f, 1.0f);
        builder.addVertex(0.0f, (float) height, 0.0f).setUv(0.0f, 1.0f);
        net.eca.mv.LegacyGl.drawWithShader(builder.buildOrThrow());
    }

    @SuppressWarnings("deprecation")
    private static void renderFilterPass(ShaderInstance shader, Consumer<ShaderInstance> uniformApplier) {
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCopyTargets(width, height);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);

        net.eca.mv.LegacyGl.disableDepthTest();
        net.eca.mv.LegacyGl.depthMask(false);
        net.eca.mv.LegacyGl.disableCull();
        net.eca.mv.LegacyGl.disableBlend();

        com.mojang.blaze3d.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, (float) width, (float) height, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(orthoSlice(ortho), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().identity();

            net.eca.mv.LegacyGl.setShader(() -> shader);
            net.eca.mv.LegacyGl.setShaderTexture(0, depthCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(1, colorCopyTexture);
            uniformApplier.accept(shader);
            if (shader.getUniform("FilterStrength") != null) {
                shader.getUniform("FilterStrength").set(bossShowFilter != null ? bossShowFilterStrength : 1.0F);
            }

            drawFullscreenQuad(width, height);
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(savedProj, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
            net.eca.mv.LegacyGl.enableCull();
            net.eca.mv.LegacyGl.depthMask(true);
            net.eca.mv.LegacyGl.enableDepthTest();
            net.eca.mv.LegacyGl.enableBlend();
        }
    }

    @SuppressWarnings("deprecation")
    private static void renderCosmos(RenderLevelStageEvent event) {
        if (cosmosStartNanos == 0) {
            cosmosStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - cosmosStartNanos) / 1_000_000_000.0f;
        renderWorldFilterPass(event, cosmosShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            net.eca.mv.LegacyGl.setShaderTexture(3, cosmosTerrainDepthTexture);
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    private static float filterTime(float time) {
        return time * (bossShowFilter != null ? bossShowFilterSpeed : 1.0F);
    }

    /* 世界空间滤镜通道：在 renderFilterPass 的基础上，额外向着色器提供逐像素世界坐标
       反算所需的数据，使效果附着在世界表面而非屏幕。任何"贴世界表面"的滤镜均可复用。
       视图旋转取自 RenderSystem 的逆视图旋转矩阵（AFTER_LEVEL 阶段 event 的 poseStack
       是投影栈，不可用）。着色器契约（除 Sampler0=深度、Sampler1=颜色外）：
         uniform mat4 InvViewProjMat  // inverse(ProjMat * ViewRotMat)，相机相对
         uniform vec3 CameraPos       // 相机世界坐标
       反算公式：worldPos = (InvViewProjMat * vec4(ndc, 1)).xyz / w + CameraPos */
    @SuppressWarnings("deprecation")
    private static void renderWorldFilterPass(RenderLevelStageEvent event, ShaderInstance shader, Consumer<ShaderInstance> uniformApplier) {
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCopyTargets(width, height);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);

        // 相机相对反算矩阵：AFTER_LEVEL 阶段 event.getPoseStack() 实为投影栈，不能用；
        // 视图旋转改从 RenderSystem 的逆视图旋转矩阵还原，再与投影组合求逆
        Matrix3f viewRot = new Matrix3f().rotation(mc.gameRenderer.mainCamera().rotation()).invert();
        Matrix4f view = new Matrix4f().set(viewRot);
        // 1.21.11 (as 1.21.8/1.21.10): AfterLevel carries no projection; 1.21.1's event.getProjectionMatrix() was GameRenderer's
        // level projection (FOV modifiers, bobHurt/bobView, getDepthFar), which renderLevel still receives -- captured there
        // (26.1.2: captured at GameRenderer.renderLevel's upload of it, see captureLevelProjection; 26.2: reverse-Z, given a
        // forward-Z depth row for cosmos.fsh, see eca$forwardLevelProjection)
        Matrix4f invViewProj = eca$forwardLevelProjection(width, height)
                .mul(view)
                .invert();
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        net.eca.mv.LegacyGl.disableDepthTest();
        net.eca.mv.LegacyGl.depthMask(false);
        net.eca.mv.LegacyGl.disableCull();
        net.eca.mv.LegacyGl.disableBlend();

        com.mojang.blaze3d.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, (float) width, (float) height, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(orthoSlice(ortho), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().identity();

            net.eca.mv.LegacyGl.setShader(() -> shader);
            net.eca.mv.LegacyGl.setShaderTexture(0, depthCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(1, colorCopyTexture);
            if (shader.getUniform("InvViewProjMat") != null) {
                shader.getUniform("InvViewProjMat").set(invViewProj);
            }
            if (shader.getUniform("CameraPos") != null) {
                shader.getUniform("CameraPos").set((float) cam.x, (float) cam.y, (float) cam.z);
            }
            uniformApplier.accept(shader);
            if (shader.getUniform("FilterStrength") != null) {
                shader.getUniform("FilterStrength").set(bossShowFilter != null ? bossShowFilterStrength : 1.0F);
            }

            drawFullscreenQuad(width, height);
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.setProjectionMatrix(savedProj, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
            net.eca.mv.LegacyGl.enableCull();
            net.eca.mv.LegacyGl.depthMask(true);
            net.eca.mv.LegacyGl.enableDepthTest();
            net.eca.mv.LegacyGl.enableBlend();
        }
    }
}
