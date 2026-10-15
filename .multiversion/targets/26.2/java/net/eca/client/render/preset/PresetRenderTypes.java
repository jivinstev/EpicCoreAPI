package net.eca.client.render.preset;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/* 通用预设 RenderType 工厂：把原本每个内置预设各写一份的 5 种 RenderType 抽成"名字 + ShaderState"参数化的构造。
   各档的顶点格式与渲染状态与内置预设逐项一致，保证自定义预设在 boss 条 / 实体层 / 天空盒 / 物品 / 实体效果上的行为完全等价。 */
public final class PresetRenderTypes {

    private PresetRenderTypes() {}

    public static RenderType bossBar(String name, net.eca.mv.ShaderShard shaderState) {
        return net.eca.mv.LegacyRenderType.create(name + "_boss_bar",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
                .createCompositeState(false)
        );
    }

    public static RenderType bossLayer(String name, net.eca.mv.ShaderShard shaderState) {
        return net.eca.mv.LegacyRenderType.create(name + "_boss_layer",
            DefaultVertexFormat.ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
                .createCompositeState(false)
        );
    }

    public static RenderType skybox(String name, net.eca.mv.ShaderShard shaderState) {
        return net.eca.mv.LegacyRenderType.create(name + "_skybox",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
                .createCompositeState(false)
        );
    }

    public static RenderType item(String name, net.eca.mv.ShaderShard shaderState) {
        return net.eca.mv.LegacyRenderType.create(name + "_item",
            DefaultVertexFormat.ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            false,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTextureState(net.eca.mv.LegacyRenderType.BLOCK_SHEET_MIPPED)
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
                .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
                .setOverlayState(net.eca.mv.LegacyRenderType.OVERLAY)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
                .createCompositeState(true)
        );
    }

    //Sampler0 绑方块图集，适用于只在 Color-Key 分支采样基础贴图的着色器
    public static RenderType block(String name, net.eca.mv.ShaderShard shaderState) {
        return block(name, shaderState, net.eca.mv.LegacyRenderType.BLOCK_SHEET_MIPPED);
    }

    /* 叠加层顶点由「世界坐标 − 摄像机」直接烘出，原方块则经区块相对坐标 + per-chunk 平移得到；
       两条浮点路径给出同一平面的不同深度，逐帧摇摆即 Z-fighting。用与原版破坏贴花同一档的
       多边形偏移把片元按坡度拉向观察者，任意距离与视角下都成立。
       CULL 是该偏移的配套前提，不是风格取舍：偏移同样作用于背面片元，保留 NO_CULL 会让背面
       被拉到正面之前透出来，等于把 Z-fighting 换成一种更稳定的穿模。
       textureState 由调用方给出——有的着色器 Sampler0 绑的是自己的单图贴图，整张即一片花瓣或
       叶片，按 UV 全域取用后程序化撒布，绑成方块图集会取错像素。 */
    public static RenderType block(String name, net.eca.mv.ShaderShard shaderState,
                                   net.eca.mv.LegacyRenderType.Shard textureState) {
        return net.eca.mv.LegacyRenderType.create(name + "_block_overlay",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            2097152,
            true,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTextureState(textureState)
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
                .setLayeringState(net.eca.mv.LegacyRenderType.POLYGON_OFFSET_LAYERING)
                .setLightmapState(net.eca.mv.LegacyRenderType.LIGHTMAP)
                .setCullState(net.eca.mv.LegacyRenderType.CULL)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
                .createCompositeState(true)
        );
    }

    static RenderType entityEffect(String name, net.eca.mv.ShaderShard shaderState, Identifier texture) {
        return net.eca.mv.LegacyRenderType.create(name + "_entity_effect",
            DefaultVertexFormat.ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(shaderState)
                .setTextureState(new net.eca.mv.LegacyRenderType.TextureStateShard(texture, false))
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
                .setLightmapState(net.eca.mv.LegacyRenderType.LIGHTMAP)
                .setOverlayState(net.eca.mv.LegacyRenderType.OVERLAY)
                .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_DEPTH_WRITE)
                .createCompositeState(true)
        );
    }
}
