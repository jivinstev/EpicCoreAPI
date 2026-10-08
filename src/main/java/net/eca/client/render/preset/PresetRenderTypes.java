package net.eca.client.render.preset;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/* 通用预设 RenderType 工厂：把原本每个内置预设各写一份的 5 种 RenderType 抽成"名字 + ShaderState"参数化的构造。
   各档的顶点格式与渲染状态与内置预设逐项一致，保证自定义预设在 boss 条 / 实体层 / 天空盒 / 物品 / 实体效果上的行为完全等价。 */
public final class PresetRenderTypes {

    private PresetRenderTypes() {}

    public static RenderType bossBar(String name, EcaShaderInstance.State shaderState) {
        return RenderType.create(name + "_boss_bar",
            RenderSetup.builder(shaderState.pipeline(name + "_boss_bar", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
                .sortOnUpload()
                .createRenderSetup()
        );
    }

    public static RenderType bossLayer(String name, EcaShaderInstance.State shaderState) {
        return RenderType.create(name + "_boss_layer",
            RenderSetup.builder(shaderState.pipeline(name + "_boss_layer", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)))
                .sortOnUpload()
                .createRenderSetup()
        );
    }

    public static RenderType skybox(String name, EcaShaderInstance.State shaderState) {
        return RenderType.create(name + "_skybox",
            RenderSetup.builder(shaderState.pipeline(name + "_skybox", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
                .affectsCrumbling()
                .createRenderSetup()
        );
    }

    public static RenderType item(String name, EcaShaderInstance.State shaderState) {
        return RenderType.create(name + "_item",
            RenderSetup.builder(shaderState.pipeline(name + "_item", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)))
                .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                .useOverlay()
                .affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        );
    }

    //Sampler0 绑方块图集，适用于只在 Color-Key 分支采样基础贴图的着色器
    public static RenderType block(String name, EcaShaderInstance.State shaderState) {
        return block(name, shaderState, TextureAtlas.LOCATION_BLOCKS);
    }

    /* 叠加层顶点由「世界坐标 − 摄像机」直接烘出，原方块则经区块相对坐标 + per-chunk 平移得到；
       两条浮点路径给出同一平面的不同深度，逐帧摇摆即 Z-fighting。用与原版破坏贴花同一档的
       多边形偏移把片元按坡度拉向观察者，任意距离与视角下都成立。
       CULL 是该偏移的配套前提，不是风格取舍：偏移同样作用于背面片元，保留 NO_CULL 会让背面
       被拉到正面之前透出来，等于把 Z-fighting 换成一种更稳定的穿模。
       textureState 由调用方给出——有的着色器 Sampler0 绑的是自己的单图贴图，整张即一片花瓣或
       叶片，按 UV 全域取用后程序化撒布，绑成方块图集会取错像素。 */
    public static RenderType block(String name, EcaShaderInstance.State shaderState,
                                   Identifier textureState) {
        return RenderType.create(name + "_block_overlay",
            RenderSetup.builder(shaderState.pipeline(name + "_block_overlay", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false, -1.0F, -10.0F))))
                .withTexture("Sampler0", textureState)
                .useLightmap()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        );
    }

    static RenderType entityEffect(String name, EcaShaderInstance.State shaderState, Identifier texture) {
        return RenderType.create(name + "_entity_effect",
            RenderSetup.builder(shaderState.pipeline(name + "_entity_effect", pb -> pb
                    .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .withCull(false)))
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
        );
    }
}
