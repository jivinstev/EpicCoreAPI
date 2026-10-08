package net.eca.mixin;

import net.eca.util.EcaLogger;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(value = GenerationChunkHolder.class, priority = 1024)
public abstract class GenerationChunkHolderMixin {

    private static final int ECA_SAFE_CHUNK_LIMIT = 1_874_999;

    @Inject(method = "scheduleChunkGenerationTask", at = @At("HEAD"), cancellable = true)
    private void eca$guardChunkGeneration(
        ChunkStatus status,
        ChunkMap chunkMap,
        CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir
    ) {
        ChunkPos pos = ((GenerationChunkHolder) (Object) this).getPos();
        if (Math.abs(pos.x) > ECA_SAFE_CHUNK_LIMIT || Math.abs(pos.z) > ECA_SAFE_CHUNK_LIMIT) {
            EcaLogger.warn(
                "[ChunkMapMixin] blocked out-of-range chunk generation: {},{} status={}",
                pos.x,
                pos.z,
                status
            );
            cir.setReturnValue(GenerationChunkHolder.UNLOADED_CHUNK_FUTURE);
        }
    }
}
