package net.eca.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.blender.client.render.BlenderModelLayer;
import net.eca.blender.client.entity.BlenderEntityRenderer;
import net.eca.blender.client.entity.BlenderEntityBindings;
import net.eca.blender.entity.BlenderEntityBinding;
import net.eca.client.render.EntityExtensionLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
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

    @Inject(method = "render", at = @At("HEAD"))
    private void eca$resetBlenderReplacement(T entity, float entityYaw, float partialTick,
                                             PoseStack poseStack, SubmitNodeCollector buffers,
                                             int packedLight, CallbackInfo ci) {
        eca$blenderReplacedBody = false;
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("RETURN"))
    private void eca$addExtensionLayer(EntityRendererProvider.Context context, M model, float shadowRadius, CallbackInfo ci) {
        LivingEntityRenderer<T, S, M> self = (LivingEntityRenderer<T, S, M>) (Object) this;
        self.addLayer(new EntityExtensionLayer<>(self));
        self.addLayer(new BlenderModelLayer<>(self));
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"
        )
    )
    private void eca$renderBlenderReplacement(EntityModel<S> model, PoseStack poseStack, VertexConsumer consumer,
                                              int packedLight, int packedOverlay, int color, T entity, float entityYaw,
                                              float partialTick, PoseStack methodPoseStack,
                                              SubmitNodeCollector buffers, int methodPackedLight) {
        BlenderEntityBinding blender = BlenderEntityBindings.resolve(entity);
        if (BlenderEntityBindings.replacesBody(blender)
            && BlenderEntityRenderer.render(entity, blender, poseStack, buffers, methodPackedLight,
                packedOverlay, partialTick)) {
            eca$blenderReplacedBody = true;
            return;
        }
        model.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, color);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"
        )
    )
    private void eca$skipVanillaLayersAfterReplacement(RenderLayer layer, PoseStack poseStack,
                                                        SubmitNodeCollector buffers, int packedLight,
                                                        Entity layerEntity, float limbSwing,
                                                        float limbSwingAmount, float partialTick,
                                                        float ageInTicks, float netHeadYaw, float headPitch,
                                                        T entity, float entityYaw, float methodPartialTick,
                                                        PoseStack methodPoseStack,
                                                        SubmitNodeCollector methodBuffers,
                                                        int methodPackedLight) {
        if (!eca$blenderReplacedBody) {
            layer.render(poseStack, buffers, packedLight, layerEntity, limbSwing, limbSwingAmount,
                partialTick, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
