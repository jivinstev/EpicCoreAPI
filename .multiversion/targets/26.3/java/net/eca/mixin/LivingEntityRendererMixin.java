// Fork multi-version layer: 1.21.10 version of the authors' mixin. LivingEntityRenderer renders a LivingEntityRenderState (1.21.2); the
// entity comes from net.eca.mv.EntityOf, the partial tick from the state. 1.21.9 made the render a SUBMIT: submit(state, poseStack,
// SubmitNodeCollector, CameraRenderState) hands the model to the collector (submitModel) instead of calling
// EntityModel.renderToBuffer, so the Blender body replacement redirects that call, and the layers are submitted the same way.
// The Blender body is still written straight into the shared buffer source (as net.eca.blender.client.render.BlenderModelLayer does).
package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.blender.client.render.BlenderModelLayer;
import net.eca.blender.client.entity.BlenderEntityRenderer;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.eca.blender.entity.BlenderEntityBinding;
import net.eca.client.render.EntityExtensionLayer;
import net.eca.mv.EntityOf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Unique
    private boolean eca$blenderReplacedBody;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void eca$resetBlenderReplacement(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                             CameraRenderState cameraState, CallbackInfo ci) {
        eca$blenderReplacedBody = false;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void eca$addExtensionLayer(EntityRendererProvider.Context context, M model, float shadowRadius, CallbackInfo ci) {
        LivingEntityRenderer<T, S, M> self = (LivingEntityRenderer<T, S, M>) (Object) this;
        self.addLayer(new EntityExtensionLayer<>(self));
        self.addLayer(new BlenderModelLayer<>(self));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
        )
    )
    private void eca$renderBlenderReplacement(SubmitNodeCollector collector, Model<? super S> model, S modelState,
                                              PoseStack poseStack, RenderType renderType, int packedLight,
                                              int packedOverlay, int color, UvMapping uvMapping, int outlineColor,
                                              S state, PoseStack methodPoseStack, SubmitNodeCollector methodCollector,
                                              CameraRenderState cameraState) {
        if (EntityOf.get(state) instanceof LivingEntity entity) {
            BlenderEntityBinding blender = BlenderEntityBindings.resolve(entity);
            if (BlenderEntityBindings.replacesBody(blender)
                && BlenderEntityRenderer.render(entity, blender, poseStack,
                    net.eca.mv.MultiBufferSource.main(), packedLight,
                    packedOverlay, state.partialTick)) {
                eca$blenderReplacedBody = true;
                return;
            }
        }
        collector.submitModel((Model) model, modelState, poseStack, renderType, packedLight, packedOverlay, color, uvMapping,
            outlineColor);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"
        )
    )
    private void eca$skipVanillaLayersAfterReplacement(RenderLayer layer, PoseStack poseStack,
                                                        SubmitNodeCollector buffers, int packedLight,
                                                        EntityRenderState layerState, float yRot, float xRot,
                                                        S state, PoseStack methodPoseStack,
                                                        SubmitNodeCollector methodBuffers, CameraRenderState cameraState) {
        if (!eca$blenderReplacedBody) {
            layer.submit(poseStack, buffers, packedLight, state, yRot, xRot);
        }
    }
}
