package net.eca.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.renderpearl.api.vertex.VertexFormat;
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

    private static int spotlightFbo = -1;
    private static int spotlightDepthTexture = -1;
    private static int spotlightColorTexture = -1;
    private static int spotlightWidth;
    private static int spotlightHeight;

    /* 宇宙滤镜专用：在实体绘制前（AFTER_CUTOUT_BLOCKS_LAYER）快照纯地形深度，
       用于 AFTER_LEVEL 合成时区分实体像素与方块像素（阶段 AFTER_CUTOUT_BLOCKS） */
    private static int cosmosTerrainFbo = -1;
    private static int cosmosTerrainDepthTexture = -1;
    private static int cosmosTerrainWidth;
    private static int cosmosTerrainHeight;

    // 26.3 (#33): the depth format each copy was allocated in (the main target's, see allocateDepthCopy)
    private static com.mojang.renderpearl.api.GpuFormat copyDepthFormat;
    private static com.mojang.renderpearl.api.GpuFormat cosmosTerrainDepthFormat;

    /* 26.3 (#33): the spotlight's colour + depth pair is a TextureTarget, not two raw GL textures. 26.3's
       createRenderPass refuses attachments without USAGE_RENDER_ATTACHMENT (the RawGlTexture wrapper has none), and the
       entity is drawn into it in a render pass. spotlightFbo / spotlightDepthTexture / spotlightColorTexture are that
       target's GL framebuffer and texture ids, for the authors' raw GL blits. */
    private static com.mojang.blaze3d.pipeline.TextureTarget spotlightTarget;
    /* 26.3 (#33): AfterTranslucentFeatures is posted inside the main render pass, where no other pass can be opened, so
       only the depth is copied there; the entity is drawn into the spotlight target at AfterLevel. */
    private static boolean spotlightDepthCaptured;

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

    private static com.mojang.renderpearl.api.buffers.GpuBufferSlice orthoSlice(Matrix4f ortho) {
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
            // 26.3 (#33): the depth after the translucent features is taken inside the main pass (raw GL only); the
            // entity is drawn at AfterLevel, where render passes can be opened. With improved transparency 26.3 posts
            // no AfterTranslucentFeatures, so the depth is then taken at AfterLevel.
            if (event instanceof RenderLevelStageEvent.AfterTranslucentFeatures) {
                captureSpotlightDepth();
                return;
            }
            if (afterLevel) {
                if (!spotlightDepthCaptured) {
                    captureSpotlightDepth();
                }
                spotlightDepthCaptured = false;
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

    /* 26.3 (#33): the main target's depth is a SIZED texture -- D32_FLOAT (GL_DEPTH_COMPONENT32F) or, with a stencil,
       a depth-stencil format -- where 1.21.1's was the unsized GL_DEPTH_COMPONENT these copies were made with.
       glBlitFramebuffer copies depth only between identical depth/stencil formats and otherwise raises
       GL_INVALID_OPERATION, which drops the whole blit (the colour copy in the same call too). So every depth copy is
       allocated in the main target's own format. */
    private static com.mojang.renderpearl.api.GpuFormat mainDepthFormat() {
        com.mojang.renderpearl.api.textures.GpuTexture depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture();
        return depth != null ? depth.getFormat() : com.mojang.renderpearl.api.GpuFormat.D32_FLOAT;
    }

    private static void allocateDepthCopy(int width, int height) {
        com.mojang.renderpearl.api.GpuFormat format = mainDepthFormat();
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, com.mojang.renderpearl.backend.opengl.GlConst.toGlInternalId(format), width, height, 0,
                com.mojang.renderpearl.backend.opengl.GlConst.toGlExternalId(format), com.mojang.renderpearl.backend.opengl.GlConst.toGlType(format), null);
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
        if (spotlightTarget != null && spotlightWidth == width && spotlightHeight == height
                && spotlightTarget.getDepthTexture() != null && spotlightTarget.getDepthTexture().getFormat() == mainDepthFormat()) return;
        destroySpotlightTargets();

        // 26.3 (#33): RGBA8 colour as before; the depth in the main target's own format, so the depth blit applies
        spotlightTarget = new com.mojang.blaze3d.pipeline.TextureTarget("eca spotlight", width, height,
                com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, mainDepthFormat());
        spotlightFbo = net.eca.mv.LegacyTargets.fbo(spotlightTarget);
        spotlightColorTexture = ((com.mojang.renderpearl.backend.opengl.GlTexture) spotlightTarget.getColorTexture()).glId();
        spotlightDepthTexture = ((com.mojang.renderpearl.backend.opengl.GlTexture) spotlightTarget.getDepthTexture()).glId();

        spotlightWidth = width;
        spotlightHeight = height;
    }

    private static void destroySpotlightTargets() {
        // the target owns its textures and the device's framebuffer cache owns the FBO: only the target is released
        if (spotlightTarget != null) {
            spotlightTarget.destroyBuffers();
            spotlightTarget = null;
        }
        spotlightDepthTexture = -1;
        spotlightColorTexture = -1;
        spotlightFbo = -1;
        spotlightDepthCaptured = false;
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

    // 26.3 (#37): runs at AfterOpaqueBlocks, inside the main render pass (raw GL only; see inPassBlit)
    private static void captureCosmosTerrainDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ensureCosmosTerrainTarget(width, height);
        GlStateManager._bindTexture(boundTexture);   // an allocation above binds the new texture; see inPassBlit

        inPassBlit(mainTarget, cosmosTerrainFbo, width, height, GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * 26.3 (#37): a depth copy of the main target taken from a stage that is inside the main render pass, with the GL
     * state that pass relies on put back (read from the 26.3.0.76 OpenGL backend):
     * <ul>
     * <li><b>Framebuffer.</b> GlCommandEncoder.createRenderPass binds the pass's FBO once (GlStateManager._glBindFramebuffer,
     * which caches the read and draw ids) and draws into whatever is bound. FrameBufferCache keys an FBO by each
     * attachment's GL id and mip level (a GlTextureView gives its texture's id and base level 0, a GlTexture id and 0),
     * so LegacyTargets.fbo(mainTarget) is the very FBO the main pass was opened on, and bindWrite rebinds it for read and
     * draw through the same cache.</li>
     * <li><b>Scissor.</b> createRenderPass enables the scissor test at the pass's render area, and glBlitFramebuffer is
     * clipped by it: it is switched off around the blit and back on after (raw GL, so GlStateManager's cached flag stays
     * true to the GL state).</li>
     * <li><b>Viewport, program, vertex array, active texture unit:</b> a blit touches none of them.</li>
     * <li><b>Texture bindings.</b> The backend rebinds a sampler only when the pass marks it dirty, so a texture an
     * allocation left bound on the active unit would be sampled by the pass's next draw that keeps its sampler: the
     * callers put the previous GL_TEXTURE_BINDING_2D back (through GlStateManager, which caches it).</li>
     * </ul>
     */
    private static void inPassBlit(RenderTarget mainTarget, int destFbo, int width, int height, int mask) {
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destFbo);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, mask, GL11.GL_NEAREST);
        } finally {
            net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
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

        com.mojang.renderpearl.api.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
        Matrix4f ortho = new Matrix4f().setOrtho(0.0f, (float) width, (float) height, 0.0f, -1000.0f, 1000.0f);
        RenderSystem.setProjectionMatrix(orthoSlice(ortho), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().identity();

            net.eca.mv.LegacyGl.setShader(() -> spotlightShader);
            net.eca.mv.LegacyGl.setShaderTexture(0, depthCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(1, colorCopyTexture);
            net.eca.mv.LegacyGl.setShaderTexture(2, spotlightTarget.getColorTextureView());   // 26.3: a bindable view
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

    /** 26.3 (#33): the authors' depth copy from captureSpotlightEntity, alone: raw GL only, so it may run inside the
     *  main render pass (whose framebuffer is the main target's, bound back afterwards). */
    private static void captureSpotlightDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;
        // 26.3 (#37): from AfterTranslucentFeatures this is inside the main pass; a (re)allocation here goes through
        // GlDevice.createTexture, which leaves the new texture bound on the active unit (see inPassBlit)
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ensureSpotlightTargets(width, height);
        GlStateManager._bindTexture(boundTexture);

        inPassBlit(mainTarget, spotlightFbo, width, height, GL11.GL_DEPTH_BUFFER_BIT);
        spotlightDepthCaptured = true;
    }

    /** The authors' captureSpotlightEntity after its depth copy (26.3: at AfterLevel, see captureSpotlightDepth): clear the
     *  spotlight colour and draw the crosshair entity into the spotlight target. */
    @SuppressWarnings("deprecation")
    private static void captureSpotlightEntity(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.crosshairPickEntity;
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        ensureSpotlightTargets(mainTarget.width, mainTarget.height);

        // the colour is cleared to transparent black in either case (the depth keeps the scene's, copied above)
        com.mojang.renderpearl.api.commands.CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorTexture(spotlightTarget.getColorTexture(), new org.joml.Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        if (target == null || target.isRemoved() || mc.level == null || target.level() != mc.level) {
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        double x = Mth.lerp(partialTick, target.xOld, target.getX()) - camPos.x;
        double y = Mth.lerp(partialTick, target.yOld, target.getY()) - camPos.y;
        double z = Mth.lerp(partialTick, target.zOld, target.getZ()) - camPos.z;

        PoseStack poseStack = event.getPoseStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();

        // 1.21.10: entity rendering is extract -> submit -> feature dispatch; flush it right away into the spotlight target.
        // 26.2: this one entity goes through a storage of its own (as vanilla's PictureInPictureRenderer does).
        // 26.3: at AfterLevel render() has popped the view rotation off the model-view stack; the frame is prepared and
        // drawn with it pushed again, as it was while the level's features were drawn.
        RenderSystem.getModelViewStack().pushMatrix();
        try {
            RenderSystem.getModelViewStack().mul(event.getModelViewMatrix());
            net.minecraft.client.renderer.SubmitNodeStorage storage = new net.minecraft.client.renderer.SubmitNodeStorage();
            dispatcher.submit(dispatcher.extractEntity(target, partialTick), event.getLevelRenderState().cameraRenderState,
                    x, y, z, poseStack, storage);
            try (
                net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame frame =
                    mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage);
                com.mojang.renderpearl.api.commands.RenderPass renderPass = encoder.createRenderPass(() -> "ECA spotlight",
                    spotlightTarget.getColorTextureView(), java.util.Optional.empty(),
                    spotlightTarget.getDepthTextureView(), java.util.OptionalDouble.empty())
            ) {
                RenderSystem.bindDefaultUniforms(renderPass);
                net.minecraft.client.renderer.feature.FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
            }
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
        }
        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
    }

    private static void drawFullscreenQuad(int width, int height) {
        BufferBuilder builder = net.eca.mv.Tesselator.getInstance().begin(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
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

        com.mojang.renderpearl.api.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
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

        com.mojang.renderpearl.api.buffers.GpuBufferSlice savedProj = RenderSystem.getProjectionMatrixBuffer();
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
