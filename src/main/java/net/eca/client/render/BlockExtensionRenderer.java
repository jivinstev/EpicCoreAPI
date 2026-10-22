// Fork multi-version layer: 1.21.5 version of the authors' file (BakedModel is BlockStateModel/BlockModelPart).
// 26.3 (#37): AfterTranslucentBlocks is posted inside the level's main render pass, after translucent terrain and before
// the particles, clouds and weather drawn into the same pass. The block passes are drawn INTO that pass
// (ShaderMaskRenderQueue.drawInto, under the stage's model-view and projection) instead of going to the after-level queue,
// which drew them over the particles and the weather. With Oculus shaders they still go to that queue.
// 26.3 (ECA-019): with improved transparency (Fabulous) LevelRenderer runs executeOit instead of
// executeClassicTransparency and posts no AfterTranslucentBlocks, so nothing was drawn. A frame that did not post it draws
// the passes at AfterLevel instead: no pass is open there, so each one is drawn at once in a pass of its own on the main
// target, with the view rotation pushed again (LevelRenderer.render popped it) under the level's projection, before the
// hand clears the depth.
package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eca.util.block_extension.BlockExtension;
import net.eca.util.block_extension.BlockExtensionManager;
import net.eca.util.block_extension.BlockExtensionSafeAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockExtensionRenderer {

    private static final double MAX_RENDER_DISTANCE_SQR = 256.0 * 256.0;
    private static final Map<Long, Set<BlockPos>> SECTION_BLOCKS = new HashMap<>();
    // 26.3 (ECA-019): whether this frame posted AfterTranslucentBlocks (not with improved transparency); cleared at AfterLevel
    private static boolean drawnInLevelPass;

    private BlockExtensionRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BlockExtensionRenderer::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(BlockExtensionRenderer::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(BlockExtensionRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(BlockExtensionRenderer::onAfterLevel);
    }

    public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState state) {
        long sectionKey = SectionPos.asLong(pos);
        Set<BlockPos> positions = SECTION_BLOCKS.get(sectionKey);
        if (BlockExtensionManager.getExtension(state.getBlock()) != null) {
            if (positions == null) {
                positions = new HashSet<>();
                SECTION_BLOCKS.put(sectionKey, positions);
            }
            positions.add(pos.immutable());
        } else if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                SECTION_BLOCKS.remove(sectionKey);
            }
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ClientLevel)) {
            return;
        }
        scanChunk(chunk);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ClientLevel)) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        SECTION_BLOCKS.keySet().removeIf(key -> SectionPos.x(key) == chunkPos.x() && SectionPos.z(key) == chunkPos.z());
    }

    private static void scanChunk(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
            long sectionKey = SectionPos.asLong(chunk.getPos().x(), sectionY, chunk.getPos().z());
            SECTION_BLOCKS.remove(sectionKey);
            if (!section.maybeHas(state -> BlockExtensionManager.getExtension(state.getBlock()) != null)) {
                continue;
            }
            Set<BlockPos> positions = new HashSet<>();
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            int baseZ = chunk.getPos().getMinBlockZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (BlockExtensionManager.getExtension(state.getBlock()) != null) {
                            positions.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
                        }
                    }
                }
            }
            if (!positions.isEmpty()) {
                SECTION_BLOCKS.put(sectionKey, positions);
            }
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        drawnInLevelPass = true;
        com.mojang.renderpearl.api.commands.RenderPass into =
            net.eca.client.render.shader.EcaShaderInstance.isOculusShadersActive() ? null : event.getRenderPass();
        render(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos, into, false);
    }

    // 26.3 (ECA-019): improved transparency posts no AfterTranslucentBlocks; draw at AfterLevel instead (see the header)
    private static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        boolean drawn = drawnInLevelPass;
        drawnInLevelPass = false;
        if (drawn || SECTION_BLOCKS.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        org.joml.Matrix4fStack modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.mul(event.getModelViewMatrix());
            render(event.getPoseStack(), event.getLevelRenderState().cameraRenderState.pos, null, true);
        } finally {
            modelView.popMatrix();
        }
    }

    private static void render(PoseStack poseStack, Vec3 camPos, com.mojang.renderpearl.api.commands.RenderPass into,
                               boolean drawNow) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || SECTION_BLOCKS.isEmpty()) {
            return;
        }

        Map<BatchKey, SpriteBatchingVertexConsumer> batches = new LinkedHashMap<>();
        Map<BlockExtension, List<ShaderMaskPass>> passCache = new HashMap<>();
        List<BlockPos> stale = new ArrayList<>();
        for (Set<BlockPos> section : SECTION_BLOCKS.values()) {
            for (BlockPos pos : section) {
                if (pos.distToCenterSqr(camPos) > MAX_RENDER_DISTANCE_SQR) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                BlockExtension extension = BlockExtensionManager.getExtension(state.getBlock());
                if (extension == null) {
                    stale.add(pos);
                    continue;
                }
                if (state.getRenderShape() != RenderShape.MODEL
                    || !BlockExtensionSafeAccess.shouldRender(extension, state, level, pos)) {
                    continue;
                }
                List<ShaderMaskPass> passes = passCache.computeIfAbsent(extension,
                    ignored -> extension.getBlockShaderPasses());
                if (passes == null) continue;
                boolean fullBright = BlockExtensionSafeAccess.isGlow(extension);
                for (ShaderMaskPass pass : passes) {
                    if (pass == null || pass.alpha() <= 0.0f) continue;
                    BatchKey key = new BatchKey(pass, fullBright);
                    SpriteBatchingVertexConsumer batch = batches.computeIfAbsent(key,
                        ignored -> new SpriteBatchingVertexConsumer(pass.renderType().format(), fullBright));
                    renderBlock(level, poseStack, camPos, pos, state, batch);
                }
            }
        }
        for (BlockPos pos : stale) {
            onBlockChanged(level, pos, level.getBlockState(pos));
        }
        batches.forEach((key, batch) -> enqueue(key.pass(), batch, into, drawNow));
    }

    private static void renderBlock(ClientLevel level, PoseStack poseStack, Vec3 camPos, BlockPos pos,
                                    BlockState state, SpriteBatchingVertexConsumer consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        // 1.21.5: a block model is a BlockStateModel that yields BlockModelParts; the level/pos/state overload is the one that
        // gives a model its ModelData (the 1.21.4 getModelData + tesselateBlock(..., modelData, renderType) pair)
        BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        poseStack.pushPose();
        // RenderLevelStageEvent 的 PoseStack 只含摄像机旋转，世界坐标须自行减去摄像机位置
        poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
        new net.minecraft.client.renderer.block.ModelBlockRenderer(true, false, minecraft.getBlockColors())
            .tesselateBlock((x, y, z, quad, instance) -> {
                poseStack.pushPose();
                poseStack.translate(x, y, z);
                consumer.putBakedQuad(poseStack.last(), quad, instance);
                poseStack.popPose();
            }, 0.0f, 0.0f, 0.0f, level, pos, state, model, state.getSeed(pos));
        poseStack.popPose();
    }

    private static void enqueue(ShaderMaskPass pass, SpriteBatchingVertexConsumer batch,
                                com.mojang.renderpearl.api.commands.RenderPass into, boolean drawNow) {
        batch.finish(spriteBatch -> {
            if (into != null) {
                ShaderMaskRenderQueue.drawInto(pass, spriteBatch.builder(), spriteBatch.builder().buildOrThrow(),
                    spriteBatch.uvTransform(), into);
            } else if (drawNow) {
                ShaderMaskRenderQueue.drawNow(pass, spriteBatch.builder(), spriteBatch.builder().buildOrThrow(),
                    spriteBatch.uvTransform());
            } else {
                ShaderMaskRenderQueue.enqueue(pass, spriteBatch.builder(), spriteBatch.builder().buildOrThrow(),
                    spriteBatch.uvTransform());
            }
        });
    }

    // 发光是消费端属性而非 pass 属性，共用同一 pass 的发光与非发光方块必须分批
    private record BatchKey(ShaderMaskPass pass, boolean fullBright) {
    }
}
