package net.eca.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.eca.client.render.preset.PresetRenderTypes;
import net.eca.client.render.shader.OceanShader;

@SuppressWarnings("removal")
@OnlyIn(Dist.CLIENT)
public class OceanRenderTypes {

    private static final ResourceLocation BUBBLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("eca", "textures/shader/ocean_bubble.png");

    private static final net.eca.mv.ShaderShard SHADER_STATE = new net.eca.mv.ShaderShard(OceanShader::getShader) {
        @Override
        public void setupRenderState() {
            super.setupRenderState();
            OceanShader.applyUniforms();
        }
    };

    public static final RenderType BOSS_BAR = net.eca.mv.LegacyRenderType.create("ocean_boss_bar",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTextureState(new RenderStateShard.TextureStateShard(BUBBLE_TEXTURE, net.minecraft.util.TriState.FALSE, false))
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType BOSS_LAYER = net.eca.mv.LegacyRenderType.create("ocean_boss_layer",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTextureState(new RenderStateShard.TextureStateShard(BUBBLE_TEXTURE, net.minecraft.util.TriState.FALSE, false))
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType SKYBOX = net.eca.mv.LegacyRenderType.create("ocean_skybox",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        256,
        true,
        false,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTextureState(new RenderStateShard.TextureStateShard(BUBBLE_TEXTURE, net.minecraft.util.TriState.FALSE, false))
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.NO_DEPTH_TEST)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(false)
    );

    public static final RenderType ITEM = net.eca.mv.LegacyRenderType.create("ocean_item",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        true,
        false,
        net.eca.mv.LegacyRenderType.state()
            .setShaderState(SHADER_STATE)
            .setTextureState(RenderType.BLOCK_SHEET_MIPPED)
            .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
            .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
            .setOverlayState(RenderType.OVERLAY)
            .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_WRITE)
            .createCompositeState(true)
    );

    public static RenderType createEntityEffect(ResourceLocation texture) {
        return net.eca.mv.LegacyRenderType.create("ocean_entity_effect",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            true,
            true,
            net.eca.mv.LegacyRenderType.state()
                .setShaderState(SHADER_STATE)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, net.minecraft.util.TriState.FALSE, false))
                .setTransparencyState(net.eca.mv.LegacyRenderType.TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(net.eca.mv.LegacyRenderType.LEQUAL_DEPTH_TEST)
                .setLightmapState(RenderType.LIGHTMAP)
                .setOverlayState(RenderType.OVERLAY)
                .setCullState(net.eca.mv.LegacyRenderType.NO_CULL)
                .setWriteMaskState(net.eca.mv.LegacyRenderType.COLOR_DEPTH_WRITE)
                .createCompositeState(true)
        );
    }

    //方块扩展覆盖层：Sampler0 绑的整张贴图即一个气泡，着色器按 UV 全域取它再撒布
    public static final RenderType BLOCK = PresetRenderTypes.block("ocean", SHADER_STATE,
        new RenderStateShard.TextureStateShard(BUBBLE_TEXTURE, net.minecraft.util.TriState.FALSE, false));

    private OceanRenderTypes() {}
}
