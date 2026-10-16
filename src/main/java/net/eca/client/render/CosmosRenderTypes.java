package net.eca.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.eca.client.render.preset.PresetRenderTypes;
import net.eca.client.render.shader.CosmosShader;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@SuppressWarnings("removal")
public class CosmosRenderTypes {

    private static final net.eca.mv.ShaderShard SHADER_STATE = new net.eca.mv.ShaderShard(CosmosShader::getShader) {
        @Override
        public void setupRenderState() {
            super.setupRenderState();
            CosmosShader.applyUniforms();
        }
    };

    public static final RenderType BOSS_BAR = net.eca.mv.LegacyRenderType.create("cosmos_boss_bar",
        DefaultVertexFormat.BLOCK,
        PrimitiveTopology.QUADS,
        256,
        false,
        true,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType BOSS_LAYER = net.eca.mv.LegacyRenderType.create("cosmos_boss_layer",
        DefaultVertexFormat.ENTITY,
        PrimitiveTopology.QUADS,
        256,
        false,
        true,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType SKYBOX = net.eca.mv.LegacyRenderType.create("cosmos_skybox",
        DefaultVertexFormat.BLOCK,
        PrimitiveTopology.QUADS,
        256,
        true,
        false,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType ITEM = net.eca.mv.LegacyRenderType.create("cosmos_item",
        DefaultVertexFormat.ENTITY,
        PrimitiveTopology.QUADS,
        256,
        true,
        false,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTextureState(net.eca.mv.LegacyRenderType.BLOCK_SHEET_MIPPED)
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
            .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
            .setOverlayState(net.eca.mv.LegacyRenderType.OVERLAY)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(true)
    );

    public static RenderType createEntityEffect(Identifier texture) {
        return net.eca.mv.LegacyRenderType.create("cosmos_entity_effect",
            DefaultVertexFormat.ENTITY,
            PrimitiveTopology.QUADS,
            256,
            true,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(SHADER_STATE)
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

    //方块扩展覆盖层：该着色器仅在 Color-Key 分支采样 Sampler0，用绑方块图集的默认档
    public static final RenderType BLOCK = PresetRenderTypes.block("cosmos", SHADER_STATE);

    private CosmosRenderTypes() {}
}
