package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

// 1.21: a BufferBuilder is a one-shot writer over a ByteBufferBuilder, which owns the native memory.
// The pool therefore keeps the ByteBufferBuilders and hands out a fresh BufferBuilder (or the bare
// ByteBufferBuilder, for MultiBufferSource.immediate) on each acquire.
public final class RenderBufferPool {

    // 小批次先分配 24 KiB，需要时再由缓冲区扩容。
    private static final int INITIAL_SIZE = 4096 * 6;
    private final Deque<ByteBufferBuilder> available = new ArrayDeque<>();
    private final Map<BufferBuilder, ByteBufferBuilder> builders = new IdentityHashMap<>();
    private final Map<ByteBufferBuilder, Boolean> buffers = new IdentityHashMap<>();

    public BufferBuilder acquire(com.mojang.renderpearl.api.pipeline.PrimitiveTopology mode, VertexFormat format) {
        ByteBufferBuilder backing = take();
        BufferBuilder builder = new BufferBuilder(backing, mode, format);
        builders.put(builder, backing);
        return builder;
    }

    public ByteBufferBuilder acquireBuffer() {
        ByteBufferBuilder backing = take();
        buffers.put(backing, Boolean.TRUE);
        return backing;
    }

    private ByteBufferBuilder take() {
        ByteBufferBuilder backing = available.pollFirst();
        return backing == null ? new ByteBufferBuilder(INITIAL_SIZE) : backing;
    }

    public void release(BufferBuilder builder) {
        if (builder == null) {
            return;
        }
        ByteBufferBuilder backing = builders.remove(builder);
        if (backing == null) {
            return;
        }
        recycle(backing);
    }

    public void release(ByteBufferBuilder backing) {
        if (backing == null || buffers.remove(backing) == null) {
            return;
        }
        recycle(backing);
    }

    private void recycle(ByteBufferBuilder backing) {
        // 被中断的帧留下的未关闭 MeshData 作废（之后的 close 因代数不符而无效），
        // 仍在写入、尚未 build 的顶点也一并丢弃，复用时缓冲区从零开始。
        backing.discard();
        ByteBufferBuilder.Result pending = backing.build();
        if (pending != null) {
            pending.close();
        }
        // 原生内存不会随 Java 引用丢弃而释放，保留峰值容量供后续帧复用。
        available.addLast(backing);
    }

    public void releaseAll() {
        for (BufferBuilder builder : new ArrayList<>(builders.keySet())) {
            release(builder);
        }
        for (ByteBufferBuilder backing : new ArrayList<>(buffers.keySet())) {
            release(backing);
        }
    }
}
