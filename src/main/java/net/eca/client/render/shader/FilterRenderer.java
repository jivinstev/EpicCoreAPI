package net.eca.client.render.shader;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.EcaMod;
import net.eca.client.BossShowScreenEffectState;
import net.eca.util.filter.FilterType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
@SuppressWarnings("removal")
@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public class FilterRenderer {

    // 1.21 的 RenderLevelStageEvent.Stage 在 26.x 拆成了逐阶段的事件类，这里保留阶段枚举以复用原有的分派逻辑
    private enum Stage {
        AFTER_ENTITIES,
        AFTER_CUTOUT_BLOCKS,
        AFTER_LEVEL
    }

    private static EcaShaderInstance sketchShader;
    private static EcaShaderInstance spotlightShader;
    private static EcaShaderInstance matrixShader;
    private static EcaShaderInstance rainShader;
    private static EcaShaderInstance desertShader;
    private static EcaShaderInstance snowShader;
    private static EcaShaderInstance toxicShader;
    private static EcaShaderInstance cosmosShader;
    private static EcaShaderInstance bossShowEffectShader;
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

    // 26.x 没有可直接绑定的 FBO：主缓冲区的深度/颜色复制到自有纹理，再作为采样器供滤镜着色器读取
    private static GpuTexture depthCopyTexture;
    private static GpuTexture colorCopyTexture;
    private static GpuTextureView depthCopyView;
    private static GpuTextureView colorCopyView;
    private static GpuFormat copyDepthFormat;
    private static int copyWidth;
    private static int copyHeight;

    private static GpuTexture spotlightDepthTexture;
    private static GpuTexture spotlightColorTexture;
    private static GpuTextureView spotlightDepthView;
    private static GpuTextureView spotlightColorView;
    private static GpuFormat spotlightDepthFormat;
    private static int spotlightWidth;
    private static int spotlightHeight;

    /* 宇宙滤镜专用：在实体绘制前（AfterOpaqueBlocks）快照纯地形深度，
       用于 AfterLevel 合成时区分实体像素与方块像素（阶段 AFTER_CUTOUT_BLOCKS） */
    private static GpuTexture cosmosTerrainDepthTexture;
    private static GpuTextureView cosmosTerrainDepthView;
    private static GpuFormat cosmosTerrainFormat;
    private static int cosmosTerrainWidth;
    private static int cosmosTerrainHeight;

    private static final Projection FILTER_PROJECTION = new Projection();
    private static ProjectionMatrixBuffer filterProjectionBuffer;

    public static void registerShaders(ShaderRegistration event) throws IOException {
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

    // AFTER_ENTITIES：所有实体要素（含半透明）绘制完毕
    @SubscribeEvent
    public static void onAfterEntities(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        onRenderLevelStage(Stage.AFTER_ENTITIES, event);
    }

    @SubscribeEvent
    public static void onAfterCutoutBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        onRenderLevelStage(Stage.AFTER_CUTOUT_BLOCKS, event);
    }

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        onRenderLevelStage(Stage.AFTER_LEVEL, event);
    }

    private static void onRenderLevelStage(Stage stage, RenderLevelStageEvent event) {
        if (activeFilters.isEmpty() && bossShowFilter == null) return;

        if (isRenderedFilter(FilterType.SPOTLIGHT) && spotlightShader != null) {
            if (stage == Stage.AFTER_ENTITIES) {
                captureSpotlightEntity(event);
                return;
            }
            if (stage == Stage.AFTER_LEVEL) {
                renderSpotlight();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.MATRIX) && matrixShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
                renderMatrix();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.RAIN) && rainShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
                renderRain();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.DESERT) && desertShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
                renderDesert();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.SNOW) && snowShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
                renderSnow();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.TOXIC) && toxicShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
                renderToxic();
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.COSMOS)) {
            if (stage == Stage.AFTER_CUTOUT_BLOCKS) {
                captureCosmosTerrainDepth();
                return;
            }
            if (stage == Stage.AFTER_LEVEL && cosmosShader != null) {
                renderCosmos(event);
                return;
            }
            return;
        }
        if (isRenderedFilter(FilterType.SKETCH) && sketchShader != null) {
            if (stage == Stage.AFTER_LEVEL) {
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
            BossShowScreenEffectState.applyShaderUniforms((name, value) -> {
                EcaShaderInstance.Uniform uniform = shader.getUniform(name);
                if (uniform != null) {
                    uniform.set(value);
                }
            }, partialTick);
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

    private static GpuTexture createTexture(String label, GpuFormat format, int usage, int width, int height) {
        return RenderSystem.getDevice().createTexture(() -> label, usage, format, width, height, 1, 1);
    }

    private static GpuTextureView createView(GpuTexture texture) {
        return RenderSystem.getDevice().createTextureView(texture);
    }

    private static GpuFormat depthFormatOf(RenderTarget target) {
        return target.getDepthTexture().getFormat();
    }

    private static void ensureCopyTargets(RenderTarget mainTarget) {
        int width = mainTarget.width;
        int height = mainTarget.height;
        GpuFormat depthFormat = depthFormatOf(mainTarget);
        if (depthCopyTexture != null && copyWidth == width && copyHeight == height && copyDepthFormat == depthFormat) return;
        destroyCopyTargets();

        depthCopyTexture = createTexture("ECA filter depth copy", depthFormat,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, width, height);
        depthCopyView = createView(depthCopyTexture);
        colorCopyTexture = createTexture("ECA filter color copy", GpuFormat.RGBA8_UNORM,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, width, height);
        colorCopyView = createView(colorCopyTexture);

        copyWidth = width;
        copyHeight = height;
        copyDepthFormat = depthFormat;
    }

    private static void destroyCopyTargets() {
        if (depthCopyView != null) {
            depthCopyView.close();
            depthCopyView = null;
        }
        if (colorCopyView != null) {
            colorCopyView.close();
            colorCopyView = null;
        }
        if (depthCopyTexture != null) {
            depthCopyTexture.close();
            depthCopyTexture = null;
        }
        if (colorCopyTexture != null) {
            colorCopyTexture.close();
            colorCopyTexture = null;
        }
    }

    private static void ensureSpotlightTargets(RenderTarget mainTarget) {
        int width = mainTarget.width;
        int height = mainTarget.height;
        GpuFormat depthFormat = depthFormatOf(mainTarget);
        if (spotlightColorTexture != null && spotlightWidth == width && spotlightHeight == height
                && spotlightDepthFormat == depthFormat) return;
        destroySpotlightTargets();

        spotlightDepthTexture = createTexture("ECA spotlight depth", depthFormat,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, width, height);
        spotlightDepthView = createView(spotlightDepthTexture);
        spotlightColorTexture = createTexture("ECA spotlight color", GpuFormat.RGBA8_UNORM,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, width, height);
        spotlightColorView = createView(spotlightColorTexture);

        spotlightWidth = width;
        spotlightHeight = height;
        spotlightDepthFormat = depthFormat;
    }

    private static void destroySpotlightTargets() {
        if (spotlightDepthView != null) {
            spotlightDepthView.close();
            spotlightDepthView = null;
        }
        if (spotlightColorView != null) {
            spotlightColorView.close();
            spotlightColorView = null;
        }
        if (spotlightDepthTexture != null) {
            spotlightDepthTexture.close();
            spotlightDepthTexture = null;
        }
        if (spotlightColorTexture != null) {
            spotlightColorTexture.close();
            spotlightColorTexture = null;
        }
    }

    private static void ensureCosmosTerrainTarget(RenderTarget mainTarget) {
        int width = mainTarget.width;
        int height = mainTarget.height;
        GpuFormat depthFormat = depthFormatOf(mainTarget);
        if (cosmosTerrainDepthTexture != null && cosmosTerrainWidth == width && cosmosTerrainHeight == height
                && cosmosTerrainFormat == depthFormat) return;
        destroyCosmosTerrainTarget();

        cosmosTerrainDepthTexture = createTexture("ECA cosmos terrain depth", depthFormat,
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, width, height);
        cosmosTerrainDepthView = createView(cosmosTerrainDepthTexture);

        cosmosTerrainWidth = width;
        cosmosTerrainHeight = height;
        cosmosTerrainFormat = depthFormat;
    }

    private static void destroyCosmosTerrainTarget() {
        if (cosmosTerrainDepthView != null) {
            cosmosTerrainDepthView.close();
            cosmosTerrainDepthView = null;
        }
        if (cosmosTerrainDepthTexture != null) {
            cosmosTerrainDepthTexture.close();
            cosmosTerrainDepthTexture = null;
        }
    }

    private static void captureCosmosTerrainDepth() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCosmosTerrainTarget(mainTarget);

        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                mainTarget.getDepthTexture(), cosmosTerrainDepthTexture, 0, 0, 0, 0, 0, width, height);
    }

    // 主缓冲区的深度与颜色复制到自有纹理，之后滤镜通道才能一边读一边写回主缓冲区
    private static void copyMainTarget(RenderTarget mainTarget) {
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureCopyTargets(mainTarget);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(mainTarget.getDepthTexture(), depthCopyTexture, 0, 0, 0, 0, 0, width, height);
        encoder.copyTextureToTexture(mainTarget.getColorTexture(), colorCopyTexture, 0, 0, 0, 0, 0, width, height);
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
    private static void renderSpotlight() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureSpotlightTargets(mainTarget);

        // 原先在同一通道里额外绑定 Sampler2（聚光灯实体颜色）
        renderFilterPass(spotlightShader, shader -> {
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) width, (float) height);
            }
        }, List.of(new PreparedRenderType.Texture("Sampler2", spotlightColorView, nearestSampler())));
    }

    @SuppressWarnings("deprecation")
    private static void captureSpotlightEntity(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.crosshairPickEntity;
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureSpotlightTargets(mainTarget);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(spotlightColorTexture, new Vector4f(0.0f, 0.0f, 0.0f, 0.0f),
                spotlightDepthTexture, 0.0);

        if (target == null || target.isRemoved() || mc.level == null || target.level() != mc.level) {
            return;
        }

        // 聚光灯缓冲区带着主缓冲区的深度，实体按遮挡关系只留下可见的像素
        encoder.copyTextureToTexture(mainTarget.getDepthTexture(), spotlightDepthTexture, 0, 0, 0, 0, 0, width, height);

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CameraRenderState cameraState = event.getLevelRenderState().cameraRenderState;
        Vec3 camPos = cameraState.pos;

        // 26.x 的实体渲染是“提取渲染状态 → 提交 → 统一绘制”，这里单独提取聚光灯实体并绘制到离屏缓冲区
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderState renderState = dispatcher.extractEntity(target, partialTick);
        SubmitNodeStorage storage = new SubmitNodeStorage();
        dispatcher.submit(renderState, cameraState, renderState.x - camPos.x, renderState.y - camPos.y,
                renderState.z - camPos.z, new PoseStack(), storage);

        RenderSystem.outputColorTextureOverride = spotlightColorView;
        RenderSystem.outputDepthTextureOverride = spotlightDepthView;
        try {
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage);
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
        }
    }

    private static GpuSampler nearestSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    private static RenderPipeline.Builder filterPipelineState(RenderPipeline.Builder builder) {
        return builder
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(Optional.empty())
                .withCull(false);
    }

    // 以正交投影把全屏四边形画回主缓冲区（原先靠 setShader / setShaderTexture 全局状态，现在显式组装一次绘制）
    private static void drawFullscreenQuad(EcaShaderInstance shader, int width, int height,
                                           List<PreparedRenderType.Texture> textures) {
        RenderPipeline pipeline = shader.pipeline("filter", FilterRenderer::filterPipelineState);

        if (filterProjectionBuffer == null) {
            filterProjectionBuffer = new ProjectionMatrixBuffer("ECA filter");
        }
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        FILTER_PROJECTION.setupOrtho(-1000.0f, 1000.0f, (float) width, (float) height, true);
        RenderSystem.setProjectionMatrix(filterProjectionBuffer.getBuffer(FILTER_PROJECTION), ProjectionType.ORTHOGRAPHIC);
        try (ByteBufferBuilder allocator = new ByteBufferBuilder(256)) {
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(), new Matrix4f());
            PreparedRenderType prepared = new PreparedRenderType(pipeline, OutputTarget.MAIN_TARGET, transforms,
                    new ScissorState(), textures);
            // uniform 取快照要在所有取值都设置完之后、绘制之前
            EcaShaderInstance.onPrepare(pipeline, prepared);

            BufferBuilder builder = new BufferBuilder(allocator, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.addVertex((float) (0.0f), (float) (0.0f), (float) (0.0f)).setUv(0.0f, 0.0f);
            builder.addVertex((float) width, 0.0f, 0.0f).setUv(1.0f, 0.0f);
            builder.addVertex((float) width, (float) height, 0.0f).setUv(1.0f, 1.0f);
            builder.addVertex(0.0f, (float) height, 0.0f).setUv(0.0f, 1.0f);
            try (MeshData mesh = builder.buildOrThrow();
                 GpuBuffer vertexBuffer = RenderSystem.getDevice()
                         .createBuffer(() -> "ECA filter quad", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                int indexCount = mesh.drawState().indexCount();
                prepared.drawFromBuffer(vertexBuffer, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
            }
        } finally {
            if (savedProjection != null) {
                RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
            }
        }
    }

    private static void renderFilterPass(EcaShaderInstance shader, Consumer<EcaShaderInstance> uniformApplier) {
        renderFilterPass(shader, uniformApplier, List.of());
    }

    @SuppressWarnings("deprecation")
    private static void renderFilterPass(EcaShaderInstance shader, Consumer<EcaShaderInstance> uniformApplier,
                                         List<PreparedRenderType.Texture> extraTextures) {
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        copyMainTarget(mainTarget);

        uniformApplier.accept(shader);
        if (shader.getUniform("FilterStrength") != null) {
            shader.getUniform("FilterStrength").set(bossShowFilter != null ? bossShowFilterStrength : 1.0F);
        }

        drawFullscreenQuad(shader, width, height, filterTextures(extraTextures));
    }

    private static List<PreparedRenderType.Texture> filterTextures(List<PreparedRenderType.Texture> extraTextures) {
        List<PreparedRenderType.Texture> textures = new java.util.ArrayList<>();
        textures.add(new PreparedRenderType.Texture("Sampler0", depthCopyView, nearestSampler()));
        textures.add(new PreparedRenderType.Texture("Sampler1", colorCopyView, nearestSampler()));
        textures.addAll(extraTextures);
        return textures;
    }

    @SuppressWarnings("deprecation")
    private static void renderCosmos(RenderLevelStageEvent event) {
        if (cosmosStartNanos == 0) {
            cosmosStartNanos = System.nanoTime();
        }
        float time = (System.nanoTime() - cosmosStartNanos) / 1_000_000_000.0f;
        ensureCosmosTerrainTarget(Minecraft.getInstance().gameRenderer.mainRenderTarget());
        renderWorldFilterPass(event, cosmosShader, shader -> {
            Minecraft mc = Minecraft.getInstance();
            if (shader.getUniform("ScreenSize") != null) {
                shader.getUniform("ScreenSize").set((float) mc.gameRenderer.mainRenderTarget().width, (float) mc.gameRenderer.mainRenderTarget().height);
            }
            if (shader.getUniform("Time") != null) {
                shader.getUniform("Time").set(filterTime(time));
            }
        }, List.of(new PreparedRenderType.Texture("Sampler3", cosmosTerrainDepthView, nearestSampler())));
    }

    private static float filterTime(float time) {
        return time * (bossShowFilter != null ? bossShowFilterSpeed : 1.0F);
    }

    /* 世界空间滤镜通道：在 renderFilterPass 的基础上，额外向着色器提供逐像素世界坐标
       反算所需的数据，使效果附着在世界表面而非屏幕。任何"贴世界表面"的滤镜均可复用。
       视图旋转与投影取自相机渲染状态（AfterLevel 阶段事件的 poseStack 为空栈，不可用）。
       着色器契约（除 Sampler0=深度、Sampler1=颜色外）：
         uniform mat4 InvViewProjMat  // inverse(ProjMat * ViewRotMat)，相机相对
         uniform vec3 CameraPos       // 相机世界坐标
       反算公式：worldPos = (InvViewProjMat * vec4(ndc, 1)).xyz / w + CameraPos */
    @SuppressWarnings("deprecation")
    private static void renderWorldFilterPass(RenderLevelStageEvent event, EcaShaderInstance shader,
                                              Consumer<EcaShaderInstance> uniformApplier,
                                              List<PreparedRenderType.Texture> extraTextures) {
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        int width = mainTarget.width;
        int height = mainTarget.height;

        copyMainTarget(mainTarget);

        // 相机相对反算矩阵：视图旋转即相机渲染状态里的 viewRotationMatrix，与投影组合求逆
        CameraRenderState cameraState = event.getLevelRenderState().cameraRenderState;
        Matrix4f invViewProj = new Matrix4f(cameraState.projectionMatrix)
                .mul(cameraState.viewRotationMatrix)
                .invert();
        Vec3 cam = cameraState.pos;

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

        drawFullscreenQuad(shader, width, height, filterTextures(extraTextures));
    }
}
