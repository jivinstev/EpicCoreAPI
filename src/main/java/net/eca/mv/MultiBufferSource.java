// OVERLAY-ONLY: shared fork-layer helper from mc-mod-version-upgrade templates/fork-layer/shared (not the authors' code).
package net.eca.mv;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * 26.2 has no {@code MultiBufferSource} (geometry is recorded into the frame's staged buffer, CATALOG §V100). This is
 * the 1.21 shape for fork code that still asks a buffer source for a {@link VertexConsumer} per render type and ends
 * the batch itself: the vertices are collected in {@link ByteBufferBuilder}s and each type's mesh is drawn on
 * {@code endBatch} -- a fork-layer {@link LegacyRenderType} (or a dependent's relocated copy) through its own draw, a vanilla type through
 * {@code prepare().drawFromBuffer(...)}, which draws into the type's output target (or the output override) itself.
 * {@link #main()} stands in for {@code renderBuffers().bufferSource()} and is flushed by {@link Flush}.
 */
// MV-REQUIRES: LegacyRenderType
// MV-REQUIRES: LegacyGl
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);

    /** A source whose every type shares {@code shared}. */
    static BufferSource immediate(ByteBufferBuilder shared) {
        return new BufferSource(shared, new LinkedHashMap<>());
    }

    /** A source where the types in {@code fixed} write into their own buffer and every other type shares {@code shared}. */
    static BufferSource immediateWithBuffers(SequencedMap<RenderType, ByteBufferBuilder> fixed, ByteBufferBuilder shared) {
        return new BufferSource(shared, fixed);
    }

    /** Draws one finished mesh of {@code type} now (the mesh is closed afterwards). */
    static void draw(RenderType type, MeshData mesh) {
        BufferSource.draw(type, mesh);
    }

    /**
     * Draws {@code mesh} through {@code type}'s own public {@code draw(MeshData)} when its class has one outside
     * net.minecraft (closing the mesh) and returns true; false (mesh untouched) otherwise. 26.2 removed vanilla's
     * {@code RenderType.draw(MeshData)}, so a dependent's own relocated copy of this helper's {@link LegacyRenderType}
     * (e.g. {@code net.the_last_sword.mv.LegacyRenderType}) is no {@code instanceof} of this one and would otherwise be
     * drawn through vanilla's {@code prepare().drawFromBuffer}, which binds none of its shader's own uniforms.
     */
    static boolean drawOwn(RenderType type, MeshData mesh) {
        if (type instanceof LegacyRenderType legacy) {
            legacy.draw(mesh);   // closes the mesh
            return true;
        }
        java.lang.reflect.Method own = OwnDraw.METHOD.get(type.getClass()).orElse(null);
        if (own == null) {
            return false;
        }
        try {
            own.invoke(type, mesh);   // closes the mesh
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            if (e.getCause() instanceof Error err) throw err;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    /** Per render-type class: its public non-vanilla {@code draw(MeshData)}, looked up once. */
    final class OwnDraw {
        static final ClassValue<java.util.Optional<java.lang.reflect.Method>> METHOD = new ClassValue<>() {
            @Override
            protected java.util.Optional<java.lang.reflect.Method> computeValue(Class<?> type) {
                try {
                    java.lang.reflect.Method m = type.getMethod("draw", MeshData.class);
                    if (m.getDeclaringClass().getName().startsWith("net.minecraft.")
                            || m.getReturnType() != void.class
                            || java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        return java.util.Optional.empty();
                    }
                    m.trySetAccessible();
                    return java.util.Optional.of(m);
                } catch (NoSuchMethodException | SecurityException e) {
                    return java.util.Optional.empty();
                }
            }
        };

        private OwnDraw() {
        }
    }

    /** The client-wide source ({@code renderBuffers().bufferSource()} on 1.21). */
    static BufferSource main() {
        return Main.INSTANCE;
    }

    final class Main {
        static final BufferSource INSTANCE = new BufferSource(new ByteBufferBuilder(786432), new LinkedHashMap<>());

        private Main() {
        }
    }

    class BufferSource implements MultiBufferSource {
        /** Backs every type without a buffer of its own; holds one type's vertices at a time. */
        protected final ByteBufferBuilder sharedBuffer;
        /** Types that always write into their own buffer, drawn in this order by {@link #endBatch()}. */
        protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
        /** The builders begun since their type was last drawn. */
        protected final Map<RenderType, BufferBuilder> startedBuilders = new HashMap<>();
        /** The type currently writing into {@link #sharedBuffer}, or null. */
        protected RenderType lastSharedType;

        protected BufferSource(ByteBufferBuilder sharedBuffer, SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers) {
            this.sharedBuffer = sharedBuffer;
            this.fixedBuffers = fixedBuffers;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            BufferBuilder open = startedBuilders.get(type);
            if (open != null) {
                if (type.canConsolidateConsecutiveGeometry()) {
                    return open;
                }
                // a type whose consecutive geometry must stay apart (strips, fans, line loops): draw what this
                // builder holds now, so the next caller's vertices start a new primitive instead of joining it
                endBatch(type);
            }
            ByteBufferBuilder own = fixedBuffers.get(type);
            ByteBufferBuilder target;
            if (own != null) {
                target = own;
            } else {
                // the shared buffer holds one type at a time: finish whatever was writing into it first
                if (lastSharedType != null) {
                    endBatch(lastSharedType);
                }
                target = sharedBuffer;
                lastSharedType = type;
            }
            BufferBuilder fresh = new BufferBuilder(target, type.primitiveTopology(), type.format());
            startedBuilders.put(type, fresh);
            return fresh;
        }

        /** Draws the type currently on the shared buffer, if any. */
        public void endLastBatch() {
            if (lastSharedType != null) {
                endBatch(lastSharedType);
            }
        }

        /** Draws everything begun: the shared-buffer type, then the fixed-buffer types in order, then any other. */
        public void endBatch() {
            endLastBatch();
            for (RenderType type : List.copyOf(fixedBuffers.keySet())) {
                endBatch(type);
            }
            for (RenderType type : new ArrayList<>(startedBuilders.keySet())) {
                endBatch(type);
            }
        }

        /** Draws what was written for {@code type}, if anything. */
        public void endBatch(RenderType type) {
            BufferBuilder builder = startedBuilders.remove(type);
            if (type == lastSharedType) {
                lastSharedType = null;
            }
            if (builder == null) {
                return;
            }
            MeshData mesh = builder.build();   // null when nothing was written
            if (mesh == null) {
                return;
            }
            if (type.sortOnUpload()) {
                // translucent types: order the quads back to front for the current projection before drawing; the
                // sorted index data is written into the same buffer the vertices were collected in
                ByteBufferBuilder written = fixedBuffers.getOrDefault(type, sharedBuffer);
                mesh.sortQuads(written, RenderSystem.getProjectionType().vertexSorting());
            }
            draw(type, mesh);
        }

        static void draw(RenderType type, MeshData mesh) {
            if (mesh == null) {
                return;
            }
            if (MultiBufferSource.drawOwn(type, mesh)) {   // ours or a dependent's copy: its own draw closes the mesh
                return;
            }
            try (MeshData m = mesh) {
                LegacyGl.Uploaded up = LegacyGl.Uploaded.of(m);
                // vanilla's own immediate draw: it opens the pass on the type's output target and honours
                // RenderSystem.outputColor/DepthTextureOverride, as LegacyGl.draw does for a legacy type
                type.prepare().drawFromBuffer(up.vertices(), up.indices(), up.indexType(), 0, 0, up.indexCount());
            }
        }
    }

    /** 26.2's level renderer no longer ends the main batch: end it after the translucent features, the level and the frame. */
    @EventBusSubscriber(modid = "eca", value = Dist.CLIENT)
    final class Flush {
        private Flush() {
        }

        @SubscribeEvent
        public static void afterTranslucentFeatures(RenderLevelStageEvent.AfterTranslucentFeatures event) {
            main().endBatch();
        }

        @SubscribeEvent
        public static void afterLevel(RenderLevelStageEvent.AfterLevel event) {
            main().endBatch();
        }

        @SubscribeEvent
        public static void frameEnd(RenderFrameEvent.Post event) {
            main().endBatch();
        }
    }
}
