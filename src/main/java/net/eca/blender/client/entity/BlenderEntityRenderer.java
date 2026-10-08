package net.eca.blender.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.blender.animation.BlenderPlaybackState;
import net.eca.blender.client.animation.BlenderAnimationClientState;
import net.eca.blender.client.model.BlenderModelAsset;
import net.eca.blender.client.render.BlenderModelRenderer;
import net.eca.blender.client.resource.BlenderModelManager;
import net.eca.blender.entity.BlenderEntityBinding;
import net.eca.blender.model.BlenderRenderRequest;
import net.eca.util.EcaLogger;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class BlenderEntityRenderer {
    private static final Set<Class<?>> FAILURES = ConcurrentHashMap.newKeySet();

    private BlenderEntityRenderer() { }

    public static boolean render(LivingEntity entity, BlenderEntityBinding binding, PoseStack poseStack,
                                 SubmitNodeCollector buffers, int packedLight, int overlay, float partialTick) {
        try {
            if (entity == null || binding == null || !binding.enabled() || !binding.shouldRender(entity)) return false;
            BlenderModelAsset asset = BlenderModelManager.INSTANCE.get(binding.modelId());
            if (asset == null) return false;
            BlenderPlaybackState playback = BlenderAnimationClientState.get(entity);
            BlenderRenderRequest request = new BlenderRenderRequest(asset.id, playback == null ? binding.animation(entity) : null,
                playback, (entity.tickCount + partialTick) / 20.0f,
                entity.level().getGameTime(), partialTick, playback == null ? binding.animationSpeed(entity) : 1,
                asset.blendRuntime == null ? null : binding.nodeClock(entity, asset.definition.nodeTimeSource()),
                asset.blendRuntime == null ? Map.of() : binding.nodeParameters(entity),
                binding.scale(entity), binding.offsetX(entity), binding.offsetY(entity), binding.offsetZ(entity));
            // The renderer writes pose-transformed vertices immediately; record them per render type and
            // replay them through the deferred submit collector.
            Map<RenderType, RecordingConsumer> recorded = new LinkedHashMap<>();
            boolean rendered = BlenderModelRenderer.render(request, poseStack,
                type -> recorded.computeIfAbsent(type, t -> new RecordingConsumer()), packedLight, overlay);
            if (rendered) {
                for (Map.Entry<RenderType, RecordingConsumer> entry : recorded.entrySet()) {
                    RecordingConsumer recording = entry.getValue();
                    buffers.submitCustomGeometry(poseStack, entry.getKey(), (pose, buffer) -> recording.replay(buffer));
                }
            }
            return rendered;
        } catch (Throwable failure) {
            if (binding != null && FAILURES.add(binding.getClass())) EcaLogger.error("Blender entity binding failed during rendering", failure);
            return false;
        }
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private final List<Consumer<VertexConsumer>> ops = new ArrayList<>();

        void replay(VertexConsumer buffer) {
            for (Consumer<VertexConsumer> op : ops) op.accept(buffer);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            ops.add(b -> b.addVertex(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            ops.add(c -> c.setColor(r, g, b, a));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            ops.add(c -> c.setColor(color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            ops.add(c -> c.setUv(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            ops.add(c -> c.setUv1(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            ops.add(c -> c.setUv2(u, v));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            ops.add(c -> c.setNormal(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            ops.add(c -> c.setLineWidth(width));
            return this;
        }
    }
}
