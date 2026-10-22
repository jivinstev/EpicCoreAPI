package net.eca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eca.util.bossshow.BossShowDefinition.Frame;
import net.eca.util.bossshow.BossShowEditorState;
import net.eca.util.bossshow.BossShowInterpolator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.LevelRenderer;
import net.eca.mv.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//编辑器可视化：锚点光柱 + 摄像机路径折线 + 关键帧头颅
@EventBusSubscriber(modid = "eca", value = Dist.CLIENT)
public final class BossShowAnchorRenderer {

    private static final float BEAM_HEIGHT = 6.0f;
    private static final float BEAM_HALF = 0.06f;
    private static final float BASE_HALF = 0.4f;

    private static final float PATH_R = 0.2f, PATH_G = 0.9f, PATH_B = 0.4f, PATH_A = 0.8f;

    //头颅半透明度
    private static final float HEAD_ALPHA = 0.5f;
    //序号文字距头颅落点的高度（头颅高 0.5 格，再往上留一点）
    private static final double LABEL_Y_OFFSET = 0.7;

    //头颅模型与 RenderType 懒加载缓存：资源加载完成后固定不变
    private static SkullModelBase playerHeadModel = null;
    private static RenderType playerHeadRenderType = null;

    private BossShowAnchorRenderer() {}

    // 26.3 (ECA-019): whether this frame posted AfterTranslucentBlocks; cleared at AfterLevel
    private static boolean drawnInLevelPass;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        drawnInLevelPass = true;
        if (!BossShowEditorState.isActive()) return;
        if (!BossShowEditorState.hasAnchor()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buffer = net.eca.mv.MultiBufferSource.main();

        // 26.3 (#37): AfterTranslucentBlocks is posted inside the level's main render pass. Every batch this handler ends
        // -- its own endBatch calls and each switch between the lines, head and label types -- is drawn INTO that pass
        // (with the view rotation still on the model-view stack) instead of being deferred to AfterLevel.
        try (MultiBufferSource.BufferSource.Scope inPass = buffer.drawingInto(event.getRenderPass())) {
            render(event.getPoseStack(), mc, buffer);
        }
    }

    /**
     * 26.3 (ECA-019): with improved transparency (Fabulous) LevelRenderer runs executeOit instead of
     * executeClassicTransparency and posts no AfterTranslucentBlocks, so the editor's anchor was not drawn at all. A frame
     * that did not post it draws the anchor here: no pass is open at AfterLevel, so every batch is drawn at once on the
     * main target, with the view rotation pushed again (LevelRenderer.render popped it) under the level's projection,
     * before the hand clears the depth. What other code left in the shared source is drawn first, outside that push.
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        boolean drawn = drawnInLevelPass;
        drawnInLevelPass = false;
        if (drawn) return;
        if (!BossShowEditorState.isActive()) return;
        if (!BossShowEditorState.hasAnchor()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buffer = net.eca.mv.MultiBufferSource.main();
        buffer.endBatch();
        org.joml.Matrix4fStack modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.mul(event.getModelViewMatrix());
            render(event.getPoseStack(), mc, buffer);
            buffer.endBatch();
        } finally {
            modelView.popMatrix();
        }
    }

    private static void render(PoseStack pose, Minecraft mc, MultiBufferSource.BufferSource buffer) {
        double ax = BossShowEditorState.getAnchorX();
        double ay = BossShowEditorState.getAnchorY();
        double az = BossShowEditorState.getAnchorZ();
        float anchorYaw = BossShowEditorState.getAnchorYawDeg();

        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 camPos = cam.position();

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer vc = buffer.getBuffer(RenderTypes.lines());

        //锚点光柱
        AABB beam = new AABB(
            ax - BEAM_HALF, ay, az - BEAM_HALF,
            ax + BEAM_HALF, ay + BEAM_HEIGHT, az + BEAM_HALF
        );
        renderShape(pose, vc, net.minecraft.world.phys.shapes.Shapes.create(beam), 0.0, 0.0, 0.0,
            net.minecraft.util.ARGB.colorFromFloat(1.0f, 0.2f, 1.0f, 0.2f), 1.0f);

        AABB base = new AABB(
            ax - BASE_HALF, ay, az - BASE_HALF,
            ax + BASE_HALF, ay + 0.02, az + BASE_HALF
        );
        renderShape(pose, vc, net.minecraft.world.phys.shapes.Shapes.create(base), 0.0, 0.0, 0.0,
            net.minecraft.util.ARGB.colorFromFloat(1.0f, 0.2f, 1.0f, 0.2f), 1.0f);

        //摄像机路径折线
        List<Frame> frames = BossShowEditorState.getFrames();
        if (frames.size() >= 2) {
            renderCameraPath(pose, vc, frames, ax, ay, az, anchorYaw);
        }

        buffer.endBatch(RenderTypes.lines());

        //关键帧头颅 + 顺序序号
        List<Integer> kfIndices = BossShowEditorState.getKeyframeFrameIndices();
        if (!kfIndices.isEmpty()) {
            ensureHeadModels(mc);
            if (playerHeadModel != null) {
                Quaternionf camRot = cam.rotation();
                for (int i = 0; i < kfIndices.size(); i++) {
                    int frameIdx = kfIndices.get(i);
                    if (frameIdx < frames.size()) {
                        renderKeyframeHead(pose, buffer, frames.get(frameIdx),
                            ax, ay, az, anchorYaw, i + 1, camRot, mc.font);
                    }
                }
                //显式 flush 头颅(translucent)与序号文字
                buffer.endBatch();
            }
        }

        pose.popPose();
    }

    // 26.3: no shape-outline helper is left to call. Every edge VoxelShape.forAllEdges reports is shifted by the
    // offset and handed to outlineEdge, which emits it as one line segment whose normal is its own direction.
    private static void renderShape(PoseStack pose, VertexConsumer vc, net.minecraft.world.phys.shapes.VoxelShape shape,
                                    double x, double y, double z, int color, float lineWidth) {
        final PoseStack.Pose entry = pose.last();
        final float ox = (float) x, oy = (float) y, oz = (float) z;
        shape.forAllEdges((ax, ay, az, bx, by, bz) -> outlineEdge(vc, entry,
                new float[] {ox + (float) ax, oy + (float) ay, oz + (float) az},
                new float[] {ox + (float) bx, oy + (float) by, oz + (float) bz},
                color, lineWidth));
    }

    private static void outlineEdge(VertexConsumer vc, PoseStack.Pose entry, float[] from, float[] to,
                                    int argb, float width) {
        float[] dir = {to[0] - from[0], to[1] - from[1], to[2] - from[2]};
        float lenSq = dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2];
        if (lenSq <= 1.0e-12f) {
            return;   // a degenerate edge has no direction for the line normal
        }
        float inv = 1.0f / (float) Math.sqrt(lenSq);
        for (int i = 0; i < 3; i++) {
            dir[i] *= inv;
        }
        for (float[] end : new float[][] {from, to}) {
            vc.addVertex(entry, end[0], end[1], end[2])
                    .setColor(argb)
                    .setNormal(entry, dir[0], dir[1], dir[2])
                    .setLineWidth(width);
        }
    }

    private static void renderCameraPath(PoseStack pose, VertexConsumer vc,
                                         List<Frame> frames,
                                         double ax, double ay, double az, float anchorYaw) {
        List<Vec3> vertices = new ArrayList<>();

        //用实际世界坐标连线，仅对几乎重合的相邻点去重，避免静止段产生零长度线段
        for (Frame f : frames) {
            Vec3 wp = BossShowInterpolator.anchorToWorld(f.dx(), f.dy(), f.dz(), ax, ay, az, anchorYaw);
            if (vertices.isEmpty() || vertices.get(vertices.size() - 1).distanceToSqr(wp) > 1.0e-4) {
                vertices.add(wp);
            }
        }

        if (vertices.size() < 2) return;

        var poseMatrix = pose.last().pose();
        var lastPose = pose.last();

        for (int i = 0; i < vertices.size() - 1; i++) {
            Vec3 p1 = vertices.get(i);
            Vec3 p2 = vertices.get(i + 1);
            Vec3 dir = p2.subtract(p1).normalize();
            float nx = (float) dir.x;
            float ny = (float) dir.y;
            float nz = (float) dir.z;
            vc.addVertex(poseMatrix, (float) p1.x, (float) p1.y, (float) p1.z)
                .setColor(PATH_R, PATH_G, PATH_B, PATH_A)
                .setNormal(lastPose, nx, ny, nz);
            vc.addVertex(poseMatrix, (float) p2.x, (float) p2.y, (float) p2.z)
                .setColor(PATH_R, PATH_G, PATH_B, PATH_A)
                .setNormal(lastPose, nx, ny, nz);
        }
    }

    private static void renderKeyframeHead(PoseStack pose, MultiBufferSource.BufferSource buffer,
                                           Frame frame,
                                           double ax, double ay, double az, float anchorYaw,
                                           int ordinal, Quaternionf camRot, Font font) {
        Vec3 wp = BossShowInterpolator.anchorToWorld(frame.dx(), frame.dy(), frame.dz(), ax, ay, az, anchorYaw);
        //复刻 SkullBlockRenderer.renderSkull 的变换，但走 translucent 渲染类型以支持半透明
        //其内部 scale(-1,-1,1) 等价绕 Z 轴 180° 翻转，故 yaw 加 180° 抵消
        float worldYaw = frame.yaw() + anchorYaw + 180f;

        pose.pushPose();
        pose.translate(wp.x, wp.y, wp.z);
        pose.scale(-1.0f, -1.0f, 1.0f);
        SkullModelBase.State headState = new SkullModelBase.State();
        headState.animationPos = 0f;
        headState.yRot = worldYaw;
        headState.xRot = 0f;
        playerHeadModel.setupAnim(headState);
        playerHeadModel.renderToBuffer(pose, buffer.getBuffer(playerHeadRenderType),
            0xF000F0, OverlayTexture.NO_OVERLAY,
            ARGB.color((int) (HEAD_ALPHA * 255f), 255, 255, 255));
        pose.popPose();

        renderHeadLabel(pose, buffer, wp, ordinal, camRot, font);
    }

    //在头颅上方渲染朝向摄像机的序号（公告板）
    private static void renderHeadLabel(PoseStack pose, MultiBufferSource.BufferSource buffer,
                                        Vec3 wp, int ordinal, Quaternionf camRot, Font font) {
        String label = Integer.toString(ordinal);
        pose.pushPose();
        pose.translate(wp.x, wp.y + LABEL_Y_OFFSET, wp.z);
        pose.last().pose().rotate(camRot);
        pose.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f mat = pose.last().pose();
        float x = -font.width(label) / 2f;
        net.eca.mv.Fonts.drawInBatch(font, label, x, 0f, 0xFFFFFFFF, true, mat, buffer,
            Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }

    private static void ensureHeadModels(Minecraft mc) {
        if (playerHeadModel != null) return;
        Map<SkullBlock.Type, SkullModelBase> models =
            net.eca.mv.Skulls.createSkullRenderers(mc.getEntityModels());
        playerHeadModel = models.get(SkullBlock.Types.PLAYER);
        //translucent 渲染类型支持 alpha 混合，用 vanilla 头颅同款默认皮肤纹理
        playerHeadRenderType = RenderTypes.entityTranslucent(DefaultPlayerSkin.getDefaultTexture());
    }
}
