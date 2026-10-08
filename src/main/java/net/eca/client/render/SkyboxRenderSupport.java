package net.eca.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.eca.EcaMod;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/* LevelRendererMixin 的绘制辅助：26.x 的绘制是显式的管线 / 缓冲区调用，不再有 setShader / setShaderTexture 全局状态。
   放在独立类里是因为 Mixin 类不适合带静态初始化。 */
@OnlyIn(Dist.CLIENT)
public final class SkyboxRenderSupport {

    // 1.21 中纹理天空盒用 position_tex_color 着色器 + 开混合 + 关深度写入
    private static final RenderPipeline TEXTURE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(EcaMod.MOD_ID, "pipeline/skybox_texture"))
        .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
        .build();

    private static final Map<Identifier, RenderType> TEXTURE_TYPES = new HashMap<>();

    // 26.x 的雾是一个 uniform 块切片（RenderSystem.getShaderFog），不再是起止距离两个浮点
    private static GpuBuffer noFogBuffer;

    private SkyboxRenderSupport() {
    }

    public static RenderType textureType(Identifier texture) {
        return TEXTURE_TYPES.computeIfAbsent(texture, location ->
            RenderType.create("eca_skybox_texture", RenderSetup.builder(TEXTURE_PIPELINE)
                .withTexture("Sampler0", location)
                .createRenderSetup()));
    }

    // 与原版 FogRenderer 的“无雾”缓冲同布局：颜色 + 六个距离均为 Float.MAX_VALUE
    public static GpuBufferSlice noFog() {
        if (noFogBuffer == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer buffer = stack.malloc(FogRenderer.FOG_UBO_SIZE);
                Std140Builder.intoBuffer(buffer)
                    .putVec4(0.0f, 0.0f, 0.0f, 0.0f)
                    .putFloat(Float.MAX_VALUE)
                    .putFloat(Float.MAX_VALUE)
                    .putFloat(Float.MAX_VALUE)
                    .putFloat(Float.MAX_VALUE)
                    .putFloat(Float.MAX_VALUE)
                    .putFloat(Float.MAX_VALUE);
                noFogBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "ECA force-loaded no fog", GpuBuffer.USAGE_UNIFORM, buffer.flip());
            }
        }
        return noFogBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE);
    }

    public static void drawMesh(RenderType renderType, MeshData meshData, String label) {
        try (MeshData mesh = meshData;
             GpuBuffer vertexBuffer = RenderSystem.getDevice()
                 .createBuffer(() -> label, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())) {
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
            int indexCount = mesh.drawState().indexCount();
            renderType.prepare().drawFromBuffer(vertexBuffer, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
        }
    }
}
