package net.eca.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.util.EcaLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * ECA 自定义 Boss 血条绘制器 —— 实体扩展与袭击系统共用。
 *
 * 绘制分两层：外框满宽渲染作为底层，填充按 progress 横向裁剪覆盖其上。
 * 同时提供贴图与 RenderType 时，着色器通过 alpha 通道遮罩叠加在贴图的非透明像素上。
 */
@OnlyIn(Dist.CLIENT)
public final class EcaBossBarRenderer {

    // 原版血条尺寸，未提供任何尺寸信息时作为回退
    public static final int VANILLA_BAR_WIDTH = 182;
    public static final int VANILLA_BAR_HEIGHT = 5;

    private EcaBossBarRenderer() {}

    /*
     * 一条血条的完整外观参数。
     *
     * 尺寸为 0 时按贴图实际尺寸解析；仅提供 RenderType 而不提供贴图时必须显式给出尺寸，
     * 否则无法确定绘制范围。
     */
    public static final class BarAppearance {
        public ResourceLocation frameTexture;
        public ResourceLocation fillTexture;
        public RenderType frameRenderType;
        public RenderType fillRenderType;
        public int frameWidth;
        public int frameHeight;
        public int fillWidth;
        public int fillHeight;
        public int frameOffsetX;
        public int frameOffsetY;
        public int fillOffsetX;
        public int fillOffsetY;
        public float frameAlpha = 1.0f;
        public float fillAlpha = 1.0f;
        public Component valueText;

        // 是否未设置任何可绘制内容
        public boolean isEmpty() {
            return frameTexture == null && fillTexture == null
                    && frameRenderType == null && fillRenderType == null;
        }
    }

    // 绘制一条自定义血条
    /**
     * Draw a custom boss bar, resolving layout from the supplied appearance.
     * <p>
     * The bar is centered horizontally and scaled down when it would exceed the screen width.
     *
     * @param graphics   the GUI graphics context
     * @param y          the vertical position handed over by the vanilla bar layout
     * @param progress   fill ratio in range [0, 1]
     * @param appearance the resolved appearance
     * @param debugName  identifier used in warnings about missing sizes
     * @return true if the bar was drawn; false when required sizes are missing and the caller
     *         should fall back to the vanilla bar
     */
    public static boolean draw(GuiGraphics graphics, int y, float progress,
                               BarAppearance appearance, String debugName) {
        if (appearance == null || appearance.isEmpty()) {
            return false;
        }

        int barWidth = VANILLA_BAR_WIDTH;
        int barHeight = VANILLA_BAR_HEIGHT;
        int fillTextureWidth;
        int fillTextureHeight;

        if (appearance.frameTexture != null) {
            TextureSizeCache.Size frameSize = TextureSizeCache.get(appearance.frameTexture);
            barWidth = frameSize.width();
            barHeight = frameSize.height();
        } else if (appearance.frameRenderType != null) {
            barWidth = appearance.frameWidth;
            barHeight = appearance.frameHeight;
        }

        if (appearance.fillTexture != null) {
            TextureSizeCache.Size fillSize = TextureSizeCache.get(appearance.fillTexture);
            fillTextureWidth = fillSize.width();
            fillTextureHeight = fillSize.height();
        } else if (appearance.fillRenderType != null) {
            fillTextureWidth = appearance.fillWidth;
            fillTextureHeight = appearance.fillHeight;
        } else {
            fillTextureWidth = barWidth;
            fillTextureHeight = barHeight;
        }

        if (appearance.frameRenderType != null && (barWidth <= 0 || barHeight <= 0)) {
            EcaLogger.warn("Custom boss bar frame size must be set for {}", debugName);
            return false;
        }
        if (appearance.fillRenderType != null && (fillTextureWidth <= 0 || fillTextureHeight <= 0)) {
            EcaLogger.warn("Custom boss bar fill size must be set for {}", debugName);
            return false;
        }

        float clamped = progress < 0.0f ? 0.0f : (progress > 1.0f ? 1.0f : progress);
        int fillWidth = (int) (clamped * (float) fillTextureWidth);

        int layoutWidth = barWidth;
        if (appearance.frameTexture == null && appearance.frameRenderType == null && fillTextureWidth > 0) {
            layoutWidth = fillTextureWidth;
        }

        float scale = 1.0f;
        int guiWidth = graphics.guiWidth();
        if (layoutWidth > 0) {
            float availableWidth = Math.max(1.0f, (float) guiWidth - 20.0f);
            scale = Math.min(1.0f, availableWidth / (float) layoutWidth);
        }

        float scaledWidth = layoutWidth * scale;
        float renderX = (guiWidth - scaledWidth) * 0.5f;

        graphics.pose().pushPose();
        graphics.pose().translate(renderX, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);

        int baseFillOffsetX = Math.max(0, (barWidth - fillTextureWidth) / 2);
        int baseFillOffsetY = Math.max(0, (barHeight - fillTextureHeight) / 2);
        int fillDrawX = baseFillOffsetX + appearance.fillOffsetX;
        int fillDrawY = baseFillOffsetY + appearance.fillOffsetY;

        // 外框：满宽渲染（先渲染作为底层）
        renderLayer(graphics, appearance.frameTexture, appearance.frameRenderType,
                appearance.frameOffsetX, appearance.frameOffsetY,
                barWidth, barHeight, barWidth, barHeight, appearance.frameAlpha);

        // 填充：按 progress 裁剪渲染（后渲染覆盖在外框上方）
        if (fillWidth > 0) {
            renderLayer(graphics, appearance.fillTexture, appearance.fillRenderType,
                    fillDrawX, fillDrawY,
                    fillWidth, fillTextureHeight, fillTextureWidth, fillTextureHeight, appearance.fillAlpha);
        }

        if (appearance.valueText != null) {
            graphics.flush();
            int textY = (barHeight - Minecraft.getInstance().font.lineHeight) / 2;
            graphics.drawCenteredString(Minecraft.getInstance().font, appearance.valueText,
                    layoutWidth / 2, textY, 0xFFFFFFFF);
        }

        graphics.pose().popPose();
        return true;
    }

    public static Component formatValueText(Number currentValue, Number maxValue) {
        if (currentValue == null || maxValue == null) {
            return null;
        }

        double current = currentValue.doubleValue();
        double maximum = maxValue.doubleValue();
        if (!Double.isFinite(current) || !Double.isFinite(maximum)) {
            return null;
        }

        return Component.literal(formatNumber(current) + "/" + formatNumber(maximum));
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static void renderLayer(GuiGraphics graphics, ResourceLocation texture, RenderType renderType,
                                    int x, int y, int drawWidth, int drawHeight, int fullWidth, int fullHeight,
                                    float alpha) {
        if (texture == null && renderType == null) {
            return;
        }
        EcaShaderInstance.setOpacity(alpha);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        try {
            if (texture != null && renderType != null) {
                drawTextureWithShaderMask(graphics, texture, renderType,
                        x, y, drawWidth, drawHeight, fullWidth, fullHeight);
            } else if (texture != null) {
                graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, 0, 0, drawWidth, drawHeight, fullWidth, fullHeight);
            } else {
                drawRenderType(graphics, renderType, x, y, drawWidth, drawHeight, fullWidth);
            }
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            EcaShaderInstance.clearOpacity();
        }
    }

    private static void drawTextureWithShaderMask(GuiGraphics graphics, ResourceLocation texture, RenderType renderType,
                                                  int x, int y, int drawWidth, int drawHeight,
                                                  int fullWidth, int fullHeight) {
        graphics.flush();
        Matrix4f matrix = graphics.pose().last().pose();

        // 清除渲染区域的 alpha 通道为 0
        net.eca.mv.LegacyGl.colorMask(false, false, false, true);
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ZERO,
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ZERO
        );
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_COLOR);
        BufferBuilder clearBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        clearBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(0, 0, 0, 255);
        clearBuilder.addVertex(matrix, x, y, 0).setColor(0, 0, 0, 255);
        net.eca.mv.LegacyGl.drawWithShader(clearBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.colorMask(true, true, true, true);

        // 渲染贴图，alpha 通道直接写入帧缓冲区
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_SRC_ALPHA,
                com.mojang.blaze3d.platform.SourceFactor.ONE, com.mojang.blaze3d.platform.DestFactor.ZERO
        );
        net.eca.mv.LegacyGl.setShaderTexture(0, texture);
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_TEX);
        float texU1 = fullWidth <= 0 ? 0.0f : (float) drawWidth / (float) fullWidth;
        float texV1 = fullHeight <= 0 ? 0.0f : (float) drawHeight / (float) fullHeight;
        BufferBuilder texBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        texBuilder.addVertex(matrix, x, y + drawHeight, 0).setUv(0.0f, texV1);
        texBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setUv(texU1, texV1);
        texBuilder.addVertex(matrix, x + drawWidth, y, 0).setUv(texU1, 0.0f);
        texBuilder.addVertex(matrix, x, y, 0).setUv(0.0f, 0.0f);
        net.eca.mv.LegacyGl.drawWithShader(texBuilder.buildOrThrow());

        // 将 alpha 缩放到 0.5，使着色器半透明叠加在贴图上
        net.eca.mv.LegacyGl.colorMask(false, false, false, true);
        net.eca.mv.LegacyGl.enableBlend();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE,
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.SRC_ALPHA
        );
        net.eca.mv.LegacyGl.setShader(net.eca.mv.LegacyGl.Vanilla.POSITION_COLOR);
        BufferBuilder scaleBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        scaleBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(0, 0, 0, 127);
        scaleBuilder.addVertex(matrix, x, y, 0).setColor(0, 0, 0, 127);
        net.eca.mv.LegacyGl.drawWithShader(scaleBuilder.buildOrThrow());
        net.eca.mv.LegacyGl.colorMask(true, true, true, true);

        // 渲染着色器，使用 DST_ALPHA 混合（只在贴图非透明区域显示）
        renderType.setupRenderState();
        net.eca.mv.LegacyGl.blendFuncSeparate(
                com.mojang.blaze3d.platform.SourceFactor.DST_ALPHA, com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_DST_ALPHA,
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE
        );
        float shaderU1 = fullWidth <= 0 ? 0.0f : (float) drawWidth / (float) fullWidth;
        int light = LightTexture.FULL_BRIGHT;
        // 1.21.5: a vanilla type (neither this mod's LegacyRenderType nor a dependent's copy) set no shader on set-up;
        // its quad is built in the type's own format and drawn with its pipeline under the mask blend (VanillaMask)
        boolean vanilla = !(renderType instanceof net.eca.mv.LegacyRenderType) && !CopyGl.isCopy(renderType);
        BufferBuilder shaderBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                vanilla ? renderType.format() : DefaultVertexFormat.BLOCK);
        shaderBuilder.addVertex(matrix, x, y + drawHeight, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x + drawWidth, y + drawHeight, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(shaderU1, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x + drawWidth, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(shaderU1, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        shaderBuilder.addVertex(matrix, x, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        com.mojang.blaze3d.vertex.MeshData shaderMesh = shaderBuilder.buildOrThrow();
        // 1.21.5 (ECA-035): a dependent's LegacyRenderType copy loaded its shader into ITS OWN LegacyGl on set-up, so
        // this mod's LegacyGl would draw the quad with the POSITION_TEX shader set before; draw it through the copy's LegacyGl
        if (vanilla) {
            VanillaMask.draw(renderType, shaderMesh);
        } else if (renderType instanceof net.eca.mv.LegacyRenderType || !CopyGl.drawMasked(renderType, shaderMesh)) {
            net.eca.mv.LegacyGl.drawWithShader(shaderMesh);
        }
        renderType.clearRenderState();

        net.eca.mv.LegacyGl.defaultBlendFunc();
    }

    /**
     * The shader quad of a dependent's LegacyRenderType copy: the copy's setupRenderState loaded the copy's own LegacyGl
     * (its relocated sibling class), so the DST_ALPHA mask blend and the draw go to that LegacyGl too; when it has none,
     * the copy draws the mesh itself (its own blend). False (mesh untouched) when {@code type} is no copy.
     */
    private static final class CopyGl {
        private static final java.lang.reflect.Method[] NOT_COPY = new java.lang.reflect.Method[0];
        private static final java.lang.reflect.Method[] OWN_DRAW = new java.lang.reflect.Method[0];
        private static final ClassValue<java.lang.reflect.Method[]> METHODS = new ClassValue<>() {
            @Override
            protected java.lang.reflect.Method[] computeValue(Class<?> type) {
                for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                    if (c.getSimpleName().equals("LegacyRenderType") && c != net.eca.mv.LegacyRenderType.class
                            && RenderType.class.isAssignableFrom(c)) {
                        try {
                            Class<?> gl = Class.forName(c.getPackageName() + ".LegacyGl", false, c.getClassLoader());
                            java.lang.reflect.Method blend = gl.getMethod("blendFuncSeparate",
                                    com.mojang.blaze3d.platform.SourceFactor.class, com.mojang.blaze3d.platform.DestFactor.class,
                                    com.mojang.blaze3d.platform.SourceFactor.class, com.mojang.blaze3d.platform.DestFactor.class);
                            java.lang.reflect.Method draw = gl.getMethod("drawWithShader", com.mojang.blaze3d.vertex.MeshData.class);
                            if (java.lang.reflect.Modifier.isStatic(blend.getModifiers()) && java.lang.reflect.Modifier.isStatic(draw.getModifiers())) {
                                return new java.lang.reflect.Method[] {blend, draw};
                            }
                        } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
                            // no usable sibling LegacyGl: the copy's own draw below
                        }
                        return OWN_DRAW;
                    }
                }
                return NOT_COPY;
            }
        };

        private CopyGl() {
        }

        static boolean isCopy(RenderType type) {
            return type != null && METHODS.get(type.getClass()) != NOT_COPY;
        }

        static boolean drawMasked(RenderType type, com.mojang.blaze3d.vertex.MeshData mesh) {
            java.lang.reflect.Method[] ms = type == null ? NOT_COPY : METHODS.get(type.getClass());
            if (ms == NOT_COPY) {
                return false;   // no copy: this mod's LegacyGl path
            }
            if (ms == OWN_DRAW) {
                type.draw(mesh);   // the copy sets up, draws and clears; closes the mesh
                return true;
            }
            try {
                ms[0].invoke(null, com.mojang.blaze3d.platform.SourceFactor.DST_ALPHA, com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_DST_ALPHA,
                        com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE);
                ms[1].invoke(null, mesh);   // closes the mesh
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                if (e.getCause() instanceof Error err) throw err;
                throw new IllegalStateException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            return true;
        }
    }

    /**
     * The shader quad of a vanilla RenderType: 1.21.1-1.21.4's setupRenderState set the type's shader and the quad was
     * drawn with it under the DST_ALPHA mask blend; 1.21.5's set-up sets no shader, so the quad is drawn here with the
     * type's own pipeline (its shader, samplers, depth and write masks) into the type's target, its blend replaced by the
     * mask blend. A pipeline without blend keeps none, as the parent's NO_TRANSPARENCY shard ignored the blend func.
     */
    private static final class VanillaMask {
        private static final com.mojang.blaze3d.pipeline.BlendFunction MASK = new com.mojang.blaze3d.pipeline.BlendFunction(
                com.mojang.blaze3d.platform.SourceFactor.DST_ALPHA, com.mojang.blaze3d.platform.DestFactor.ONE_MINUS_DST_ALPHA,
                com.mojang.blaze3d.platform.SourceFactor.ZERO, com.mojang.blaze3d.platform.DestFactor.ONE);
        private static final java.util.Map<com.mojang.blaze3d.pipeline.RenderPipeline, com.mojang.blaze3d.pipeline.RenderPipeline> MASKED =
                new java.util.IdentityHashMap<>();

        private VanillaMask() {
        }

        private static com.mojang.blaze3d.pipeline.RenderPipeline masked(com.mojang.blaze3d.pipeline.RenderPipeline p) {
            if (p.getBlendFunction().isEmpty()) {
                return p;
            }
            return MASKED.computeIfAbsent(p, q -> q.toBuilder()
                    .withLocation(ResourceLocation.fromNamespaceAndPath(q.getLocation().getNamespace(),
                            q.getLocation().getPath() + "/eca_boss_bar_mask"))
                    .withBlend(MASK)
                    .build());
        }

        static void draw(RenderType type, com.mojang.blaze3d.vertex.MeshData mesh) {
            try (com.mojang.blaze3d.vertex.MeshData m = mesh) {
                com.mojang.blaze3d.pipeline.RenderPipeline pipeline = masked(type.getRenderPipeline());
                VertexFormat format = m.drawState().format();
                com.mojang.blaze3d.buffers.GpuBuffer vertices = format.uploadImmediateVertexBuffer(m.vertexBuffer());
                com.mojang.blaze3d.buffers.GpuBuffer indices;
                VertexFormat.IndexType indexType;
                if (m.indexBuffer() == null) {
                    RenderSystem.AutoStorageIndexBuffer seq = RenderSystem.getSequentialBuffer(m.drawState().mode());
                    indices = seq.getBuffer(m.drawState().indexCount());
                    indexType = seq.type();
                } else {
                    indices = format.uploadImmediateIndexBuffer(m.indexBuffer());
                    indexType = m.drawState().indexType();
                }
                com.mojang.blaze3d.pipeline.RenderTarget target = type.getRenderTarget();
                try (com.mojang.blaze3d.systems.RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        target.getColorTexture(), java.util.OptionalInt.empty(),
                        target.useDepth ? target.getDepthTexture() : null, java.util.OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertices);
                    if (RenderSystem.SCISSOR_STATE.isEnabled()) pass.enableScissor(RenderSystem.SCISSOR_STATE);
                    if (RenderSystem.STENCIL_TEST != null) pass.enableStencilTest(RenderSystem.STENCIL_TEST);
                    for (int i = 0; i < 12; i++) {
                        com.mojang.blaze3d.textures.GpuTexture t = RenderSystem.getShaderTexture(i);
                        if (t != null) pass.bindSampler("Sampler" + i, t);
                    }
                    pass.setIndexBuffer(indices, indexType);
                    pass.drawIndexed(0, m.drawState().indexCount());
                }
            }
        }
    }

    private static void drawRenderType(GuiGraphics graphics, RenderType renderType,
                                       int x, int y, int width, int height, int fullWidth) {
        float u1 = fullWidth <= 0 ? 0.0f : (float) width / (float) fullWidth;
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource.getBuffer(renderType);
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(matrix, x, y + height, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x + width, y + height, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(u1, 1.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x + width, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(u1, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        consumer.addVertex(matrix, x, y, 0).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f).setLight(light).setNormal(0.0f, 0.0f, 1.0f);
        graphics.flush();
    }
}
