// Fork multi-version layer: 1.21.2 version of the authors' file (render state instead of entity).
package net.eca.blender.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.blender.entity.BlenderEntityBinding;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.eca.blender.client.entity.BlenderEntityRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.eca.mv.EntityOf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class BlenderModelLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    public BlenderModelLayer(RenderLayerParent<S, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, S state,
                       float netHeadYaw, float headPitch) {
        // fork layer (1.21.2 render state): the entity is parked on the state by net.eca.mv.EntityOf
        if (!(EntityOf.get(state) instanceof LivingEntity entity)) {
            return;
        }
        float partialTick = state.partialTick;
        BlenderEntityBinding model = BlenderEntityBindings.resolve(entity);
        if (model == null || BlenderEntityBindings.replacesBody(model)) {
            return;
        }
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0f);
        BlenderEntityRenderer.render(entity, model, poseStack, buffers, packedLight, overlay, partialTick);
    }
}
