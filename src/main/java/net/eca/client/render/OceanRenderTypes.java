package net.eca.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.eca.client.render.preset.PresetRenderTypes;
import net.eca.client.render.shader.OceanShader;
import net.eca.client.render.shader.EcaShaderInstance;

@SuppressWarnings("removal")
public class OceanRenderTypes {

    private static final Identifier BUBBLE_TEXTURE = Identifier.fromNamespaceAndPath("eca", "textures/shader/ocean_bubble.png");

    private static final EcaShaderInstance.State SHADER_STATE = EcaShaderInstance.state(OceanShader::getShader, OceanShader::applyUniforms);

    public static final RenderType BOSS_BAR = RenderType.create("ocean_boss_bar",
        RenderSetup.builder(SHADER_STATE.pipeline("ocean_boss_bar", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .withTexture("Sampler0", BUBBLE_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType BOSS_LAYER = RenderType.create("ocean_boss_layer",
        RenderSetup.builder(SHADER_STATE.pipeline("ocean_boss_layer", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)))
            .withTexture("Sampler0", BUBBLE_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType SKYBOX = RenderType.create("ocean_skybox",
        RenderSetup.builder(SHADER_STATE.pipeline("ocean_skybox", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .withTexture("Sampler0", BUBBLE_TEXTURE)
            .affectsCrumbling()
            .createRenderSetup()
    );

    public static final RenderType ITEM = RenderType.create("ocean_item",
        RenderSetup.builder(SHADER_STATE.pipeline("ocean_item", pb -> pb
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

    public static RenderType createEntityEffect(Identifier texture) {
        return RenderType.create("ocean_entity_effect",
            RenderSetup.builder(SHADER_STATE.pipeline("ocean_entity_effect", pb -> pb
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

    //方块扩展覆盖层：Sampler0 绑的整张贴图即一个气泡，着色器按 UV 全域取它再撒布
    public static final RenderType BLOCK = PresetRenderTypes.block("ocean", SHADER_STATE,
        BUBBLE_TEXTURE);

    private OceanRenderTypes() {}
}
