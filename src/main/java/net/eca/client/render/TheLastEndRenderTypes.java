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
import net.eca.client.render.shader.TheLastEndShader;
import net.eca.client.render.shader.EcaShaderInstance;

public class TheLastEndRenderTypes {

    private static final EcaShaderInstance.State SHADER_STATE = EcaShaderInstance.state(TheLastEndShader::getShader, TheLastEndShader::applyUniforms);

    public static final RenderType BOSS_BAR = RenderType.create("the_last_end_boss_bar",
        RenderSetup.builder(SHADER_STATE.pipeline("the_last_end_boss_bar", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType BOSS_LAYER = RenderType.create("the_last_end_boss_layer",
        RenderSetup.builder(SHADER_STATE.pipeline("the_last_end_boss_layer", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)))
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType SKYBOX = RenderType.create("the_last_end_skybox",
        RenderSetup.builder(SHADER_STATE.pipeline("the_last_end_skybox", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .affectsCrumbling()
            .createRenderSetup()
    );

    public static final RenderType ITEM = RenderType.create("the_last_end_item",
        RenderSetup.builder(SHADER_STATE.pipeline("the_last_end_item", pb -> pb
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
        return RenderType.create("the_last_end_entity_effect",
            RenderSetup.builder(SHADER_STATE.pipeline("the_last_end_entity_effect", pb -> pb
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

    //方块扩展覆盖层：该着色器仅在 Color-Key 分支采样 Sampler0，用绑方块图集的默认档
    public static final RenderType BLOCK = PresetRenderTypes.block("the_last_end", SHADER_STATE);

    private TheLastEndRenderTypes() {}
}
