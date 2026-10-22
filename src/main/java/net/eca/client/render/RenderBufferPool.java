package net.eca.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class RenderBufferPool {

    // 构造参数会乘以六；小批次先分配 24 KiB，需要时再由缓冲区扩容。
    private static final int INITIAL_SIZE = 4096;
    private final Deque<BufferBuilder> available = new ArrayDeque<>();
    private final Set<BufferBuilder> inUse = Collections.newSetFromMap(new IdentityHashMap<>());

    public BufferBuilder acquire() {
        BufferBuilder builder = available.pollFirst();
        if (builder == null) {
            builder = new BufferBuilder(INITIAL_SIZE);
        }
        inUse.add(builder);
        return builder;
    }

    public void release(BufferBuilder builder) {
        if (!inUse.contains(builder)) {
            return;
        }
        if (builder.building()) {
            builder.end().release();
        }
        builder.discard();
        builder.unsetDefaultColor();
        inUse.remove(builder);
        // 原生内存不会随 Java 引用丢弃而释放，保留峰值容量供后续帧复用。
        available.addLast(builder);
    }

    public void releaseAll() {
        for (BufferBuilder builder : inUse.toArray(BufferBuilder[]::new)) {
            release(builder);
        }
    }
}
