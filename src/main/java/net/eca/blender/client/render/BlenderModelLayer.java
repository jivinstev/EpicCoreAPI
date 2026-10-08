package net.eca.blender.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.blender.entity.BlenderEntityBinding;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.eca.blender.client.entity.BlenderEntityRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class BlenderModelLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    public BlenderModelLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, S state,
                       float yRot, float xRot) {
        BlenderEntityBinding model = BlenderEntityBindings.resolve(state);
        if (model == null || BlenderEntityBindings.replacesBody(model)) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        BlenderEntityRenderer.render(state, model, poseStack, collector, packedLight, overlay);
    }
}
