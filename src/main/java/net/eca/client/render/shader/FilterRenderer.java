// Fork multi-version layer: 26.1.2 overlay of the authors' FilterRenderer. Beyond the 26.1.2 port (stage listeners,
// ProjectionMatrixBuffer, the Cosmos projection, extract -> submit -> feature-dispatch entity drawing, Identifier,
// Camera.position(), AfterTranslucentFeatures), two review fixes (#33), shaped as 1.21.11's overlay and re-derived for
// 26.1.2 with javap of minecraft-patched-26.1.2.116-merged.jar:
//  - the spotlight mask: net.minecraft.client.renderer.rendertype.RenderType.draw (reached from the feature renderers
//    through the shared MultiBufferSource) opens its RenderPass (CommandEncoder.createRenderPass) on its OutputTarget's
//    GpuTextureViews (the main target) unless RenderSystem.outputColorTextureOverride / outputDepthTextureOverride are
//    set (both public static on 26.1.2, read before createRenderPass), so binding a raw spotlight FBO was ignored: the
//    mask stayed empty and the entity was drawn a second time into the main target. The spotlight is now a
//    TextureTarget, and the whole entity draw (submit, renderAllFeatures, endBatch) runs with both overrides naming its
//    views, restored in a finally; net.eca.mv.LegacyGl (shared/26.1.2) honours the same overrides.
//  - depth copies in the main target's own format: RenderTarget.createBuffers allocates DEPTH32, or NeoForge's
//    ClientHooks.getStencilFormat() when useStencil, and an unsized GL_DEPTH_COMPONENT copy makes the COLOR|DEPTH blit
//    fail as a whole. allocateDepthCopy/depthAttachment (GlConst.toGlInternalId/toGlExternalId/toGlType), reallocated
//    when the format changes; the spotlight TextureTarget takes the main target's useStencil, so it gets the main's format.
// Under Fabulous graphics the item-entity/translucent/outline targets are not redirected, as on 1.21.1.
// An overlay passes down the `inherits` chain WITHOUT rename rows; 26.2 carries its own FilterRenderer overlay, so no
// later target compiles this file.
package net.eca.client.render.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.EcaMod;
import net.eca.client.BossShowScreenEffectState;
import net.eca.util.filter.FilterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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

    // 26.1.2 (#33): the spotlight mask is a TextureTarget, so RenderType draws can be pointed at it (see captureSpotlightEntity)
    private static com.mojang.blaze3d.pipeline.TextureTarget spotlightTarget;

    /* 宇宙滤镜专用：在实体绘制前（AFTER_CUTOUT_BLOCKS_LAYER）快照纯地形深度，
       用于 AFTER_LEVEL 合成时区分实体像素与方块像素（阶段 AFTER_CUTOUT_BLOCKS） */
    private static int cosmosTerrainFbo = -1;
    private static int cosmosTerrainDepthTexture = -1;
    private static int cosmosTerrainWidth;
    private static int cosmosTerrainHeight;

    // 26.1.2 (#33): the depth format each copy was allocated in (the main target's, see allocateDepthCopy)
    private static com.mojang.blaze3d.textures.TextureFormat copyDepthFormat;
    private static com.mojang.blaze3d.textures.TextureFormat cosmosTerrainDepthFormat;

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
            if (event instanceof RenderLevelStageEvent.AfterTranslucentFeatures) {
                captureSpotlightEntity(event);
                return;
            }
            if (afterLevel) {
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width,
                    (float) mc.getMainRenderTarget().height);
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

    // 26.1.2 (#33): the main target's depth format (MainTarget allocates DEPTH32, or NeoForge's stencil format)
    private static com.mojang.blaze3d.textures.TextureFormat mainDepthFormat() {
        com.mojang.blaze3d.textures.GpuTexture depth = Minecraft.getInstance().getMainRenderTarget().getDepthTexture();
        return depth != null ? depth.getFormat() : com.mojang.blaze3d.textures.TextureFormat.DEPTH32;
    }

    // allocates the bound texture as a depth copy in the main target's format, so a depth blit between them is legal
    private static void allocateDepthCopy(int width, int height) {
        com.mojang.blaze3d.textures.TextureFormat format = mainDepthFormat();
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, com.mojang.blaze3d.opengl.GlConst.toGlInternalId(format),
                width, height, 0, com.mojang.blaze3d.opengl.GlConst.toGlExternalId(format),
                com.mojang.blaze3d.opengl.GlConst.toGlType(format), null);
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
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        if (spotlightTarget != null && spotlightTarget.width == width && spotlightTarget.height == height
                && spotlightTarget.useStencil == mainTarget.useStencil
                && spotlightTarget.getDepthTexture().getFormat() == mainDepthFormat()) return;
        destroySpotlightTargets();
        // 26.1.2: colour RGBA8 and depth in the main target's format (createBuffers picks it from useStencil, as
        // MainTarget does), NEAREST filtering -- what the authors' raw textures were, as GpuTextures a RenderPass can use
        spotlightTarget = new com.mojang.blaze3d.pipeline.TextureTarget("eca_spotlight", width, height, true, mainTarget.useStencil);
    }

    private static void destroySpotlightTargets() {
        if (spotlightTarget != null) {
            spotlightTarget.destroyBuffers();
            spotlightTarget = null;
        }
    }

    private static void ensureCosmosTerrainTarget(int width, int height) {
        if (cosmosTerrainFbo != -1 && cosmosTerrainWidth == width && cosmosTerrainHeight == height
                && cosmosTerrainDepthFormat == mainDepthFormat()) return;
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
        RenderTarget mainTarget = mc.getMainRenderTarget();
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void renderSpotlight(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.getMainRenderTarget();
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
            net.eca.mv.LegacyGl.setShaderTexture(2, spotlightTarget != null ? spotlightTarget.getColorTextureView() : null);
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

    @SuppressWarnings("deprecation")
    private static void captureSpotlightEntity(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.crosshairPickEntity;
        RenderTarget mainTarget = mc.getMainRenderTarget();

        if (target == null || target.isRemoved() || mc.level == null || target.level() != mc.level) {
            int width = mainTarget.width;
            int height = mainTarget.height;
            ensureSpotlightTargets(width, height);
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    spotlightTarget.getColorTexture(), 0, spotlightTarget.getDepthTexture(), 1.0);
            net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
            return;
        }

        int width = mainTarget.width;
        int height = mainTarget.height;
        ensureSpotlightTargets(width, height);

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                spotlightTarget.getColorTexture(), 0, spotlightTarget.getDepthTexture(), 1.0);

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(mainTarget));
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, net.eca.mv.LegacyTargets.fbo(spotlightTarget));
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        double x = Mth.lerp(partialTick, target.xOld, target.getX()) - camPos.x;
        double y = Mth.lerp(partialTick, target.yOld, target.getY()) - camPos.y;
        double z = Mth.lerp(partialTick, target.zOld, target.getZ()) - camPos.z;
        float yRot = Mth.lerp(partialTick, target.yRotO, target.getYRot());

        PoseStack poseStack = event.getPoseStack();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        net.eca.mv.LegacyGl.enableDepthTest();
        net.eca.mv.LegacyGl.depthMask(true);
        // 1.21.10: entity rendering is extract -> submit -> feature dispatch. The level's own submits were rendered and the
        // storage cleared before AFTER_ENTITIES, so renderAllFeatures draws only this entity, into the shared bufferSource;
        // (#33) its RenderType draws open their pass on MAIN_TARGET's views unless an output override is set, so the whole
        // draw runs with the overrides naming the spotlight's (any override already in place is put back afterwards)
        com.mojang.blaze3d.textures.GpuTextureView savedColor = RenderSystem.outputColorTextureOverride;
        com.mojang.blaze3d.textures.GpuTextureView savedDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = spotlightTarget.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = spotlightTarget.getDepthTextureView();
        try {
            dispatcher.submit(dispatcher.extractEntity(target, partialTick), event.getLevelRenderState().cameraRenderState,
                    x, y, z, poseStack, mc.gameRenderer.getSubmitNodeStorage());
            mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
            buffer.endBatch();
        } finally {
            RenderSystem.outputColorTextureOverride = savedColor;
            RenderSystem.outputDepthTextureOverride = savedDepth;
        }

        net.eca.mv.LegacyTargets.bindWrite(mainTarget, false);
    }

    private static void drawFullscreenQuad(int width, int height) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
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
        RenderTarget mainTarget = mc.getMainRenderTarget();
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
                shader.getUniform("ScreenSize").set((float) mc.getMainRenderTarget().width, (float) mc.getMainRenderTarget().height);
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
        RenderTarget mainTarget = mc.getMainRenderTarget();
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
        Matrix3f viewRot = new Matrix3f().rotation(mc.gameRenderer.getMainCamera().rotation()).invert();
        Matrix4f view = new Matrix4f().set(viewRot);
        // 1.21.11 (as 1.21.8/1.21.10): AfterLevel carries no projection; 1.21.1's event.getProjectionMatrix() was GameRenderer's
        // level projection (FOV modifiers, bobHurt/bobView, getDepthFar), which renderLevel still receives -- captured there
        // (26.1.2: captured at GameRenderer.renderLevel's upload of it, see captureLevelProjection)
        Matrix4f invViewProj = new Matrix4f(LEVEL_PROJECTION)
                .mul(view)
                .invert();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();

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
