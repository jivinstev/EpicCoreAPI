package net.eca.client.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.EcaMod;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/*
 * 自定义血条着色器层的离屏绘制。纹理在 PIP 离屏纹理里以像素坐标绘制（投影为 GUI 同款的左上原点正交），
 * 1.21 中依赖 GL 颜色掩码 / 混合因子的三步合成在这里改为专用管线：
 *   1. 仅写 alpha 通道：贴图 alpha * 0.5（RGB 不写）；
 *   2. 着色器以 (DST_ALPHA, ZERO) 混合：RGB = 着色器 * dstAlpha，alpha 保持不变。
 * 结果是预乘 alpha 的纹理，与 PIP 的 GUI_TEXTURED_PREMULTIPLIED_ALPHA 贴回等价于 1.21 的
 * “着色器以 0.5 * 贴图 alpha 的权重叠在已画好的贴图上”。
 */
@OnlyIn(Dist.CLIENT)
public class BossBarShaderPipRenderer extends PictureInPictureRenderer<BossBarShaderPipState> {

    private static final int LIGHT = 15728880; // full bright (0xF000F0)

    private static final RenderPipeline MASK_ALPHA_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "pipeline/boss_bar_mask_alpha"))
        .withColorTargetState(new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO)),
            GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALPHA))
        .withDepthStencilState(Optional.empty())
        .withCull(false)
        .build();

    private static final Map<Identifier, RenderType> MASK_ALPHA_TYPES = new HashMap<>();

    @Override
    public Class<BossBarShaderPipState> getRenderStateClass() {
        return BossBarShaderPipState.class;
    }

    @Override
    protected String getTextureLabel() {
        return "ECA boss bar shader";
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return 0.0f;
    }

    @Override
    protected void renderToTexture(BossBarShaderPipState state, PoseStack poseStack, SubmitNodeCollector collector) {
        int guiScale = Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.guiScale;
        float left = (state.left() - state.x0()) * guiScale;
        float top = (state.top() - state.y0()) * guiScale;
        float right = (state.right() - state.x0()) * guiScale;
        float bottom = (state.bottom() - state.y0()) * guiScale;

        RenderSystem.getModelViewStack().pushMatrix().identity();
        EcaShaderInstance.setOpacity(state.alpha());
        try {
            if (state.maskTexture() != null) {
                // 仅写 alpha：贴图 alpha * 0.5 * alpha²（1.21 中 setShaderColor 的 alpha 同时作用于贴图与缩放两步）
                RenderType maskType = MASK_ALPHA_TYPES.computeIfAbsent(state.maskTexture(), texture ->
                    RenderType.create("eca_boss_bar_mask_alpha", RenderSetup.builder(MASK_ALPHA_PIPELINE)
                        .withTexture("Sampler0", texture)
                        .createRenderSetup()));
                int maskAlpha = Math.round(127.0f * state.alpha() * state.alpha());
                try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024)) {
                    BufferBuilder builder = new BufferBuilder(allocator, maskType.primitiveTopology(), maskType.format());
                    builder.addVertex(left, bottom, 0.0f).setUv(0.0f, state.v1()).setColor(255, 255, 255, maskAlpha);
                    builder.addVertex(right, bottom, 0.0f).setUv(state.u1(), state.v1()).setColor(255, 255, 255, maskAlpha);
                    builder.addVertex(right, top, 0.0f).setUv(state.u1(), 0.0f).setColor(255, 255, 255, maskAlpha);
                    builder.addVertex(left, top, 0.0f).setUv(0.0f, 0.0f).setColor(255, 255, 255, maskAlpha);
                    drawMesh(maskType.prepare(), builder.buildOrThrow(), "ECA boss bar mask");
                }
            }

            RenderType type = state.renderType();
            PreparedRenderType prepared = type.prepare();
            if (state.maskTexture() != null) {
                RenderPipeline masked = EcaShaderInstance.derivePipeline(type.pipeline(), "boss_bar_mask", builder -> builder
                    .withColorTargetState(new ColorTargetState(
                        Optional.of(new BlendFunction(BlendFactor.DST_ALPHA, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE)),
                        GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_ALL)));
                if (masked != type.pipeline()) {
                    prepared = new PreparedRenderType(masked, prepared.outputTarget(), prepared.dynamicTransforms(),
                        prepared.scissorState(), prepared.textures());
                    EcaShaderInstance.onPrepare(masked, prepared);
                }
            }
            try (ByteBufferBuilder allocator = new ByteBufferBuilder(1024)) {
                BufferBuilder builder = new BufferBuilder(allocator, type.primitiveTopology(), type.format());
                builder.addVertex(left, bottom, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 1.0f).setLight(LIGHT).setNormal(0.0f, 0.0f, 1.0f);
                builder.addVertex(right, bottom, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(state.u1(), 1.0f).setLight(LIGHT).setNormal(0.0f, 0.0f, 1.0f);
                builder.addVertex(right, top, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(state.u1(), 0.0f).setLight(LIGHT).setNormal(0.0f, 0.0f, 1.0f);
                builder.addVertex(left, top, 0.0f).setColor(1.0f, 1.0f, 1.0f, 1.0f).setUv(0.0f, 0.0f).setLight(LIGHT).setNormal(0.0f, 0.0f, 1.0f);
                drawMesh(prepared, builder.buildOrThrow(), "ECA boss bar shader");
            }
        } finally {
            EcaShaderInstance.clearOpacity();
            RenderSystem.getModelViewStack().popMatrix();
        }
    }

    private static void drawMesh(PreparedRenderType prepared, MeshData meshData, String label) {
        try (MeshData mesh = meshData;
             GpuBuffer vertexBuffer = RenderSystem.getDevice()
                 .createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
            int indexCount = mesh.drawState().indexCount();
            prepared.drawFromBuffer(vertexBuffer, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
        }
    }
}
