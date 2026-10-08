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
import net.eca.client.render.shader.ForestShader;
import net.eca.client.render.shader.EcaShaderInstance;
@SuppressWarnings("removal")
public class ForestRenderTypes {

    private static final Identifier LEAVES_TEXTURE = Identifier.fromNamespaceAndPath("eca", "textures/shader/forest_leaves.png");

    private static final EcaShaderInstance.State SHADER_STATE = EcaShaderInstance.state(ForestShader::getShader, ForestShader::applyUniforms);

    public static final RenderType BOSS_BAR = RenderType.create("forest_boss_bar",
        RenderSetup.builder(SHADER_STATE.pipeline("forest_boss_bar", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .withTexture("Sampler0", LEAVES_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType BOSS_LAYER = RenderType.create("forest_boss_layer",
        RenderSetup.builder(SHADER_STATE.pipeline("forest_boss_layer", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)))
            .withTexture("Sampler0", LEAVES_TEXTURE)
            .sortOnUpload()
            .createRenderSetup()
    );

    public static final RenderType SKYBOX = RenderType.create("forest_skybox",
        RenderSetup.builder(SHADER_STATE.pipeline("forest_skybox", pb -> pb
                .withVertexBinding(0, DefaultVertexFormat.BLOCK)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))))
            .withTexture("Sampler0", LEAVES_TEXTURE)
            .affectsCrumbling()
            .createRenderSetup()
    );

    public static final RenderType ITEM = RenderType.create("forest_item",
        RenderSetup.builder(SHADER_STATE.pipeline("forest_item", pb -> pb
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
        return RenderType.create("forest_entity_effect",
            RenderSetup.builder(SHADER_STATE.pipeline("forest_entity_effect", pb -> pb
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

    //方块扩展覆盖层：Sampler0 绑的整张贴图即一片叶子，着色器按 UV 全域取它再平铺撒布
    public static final RenderType BLOCK = PresetRenderTypes.block("forest", SHADER_STATE,
        LEAVES_TEXTURE);

    private ForestRenderTypes() {}
}
