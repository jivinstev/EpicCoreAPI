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

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

//编辑器可视化：锚点光柱 + 摄像机路径折线 + 关键帧头颅
@EventBusSubscriber(modid = "eca", value = Dist.CLIENT)
public final class BossShowAnchorRenderer {

    private static final int FULL_BRIGHT = 0xF000F0;
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

    //26.x 不再即时绘制：几何体提交到 SubmitNodeCollector，由渲染器统一绘制
    @SubscribeEvent
    public static void onRenderLevel(SubmitCustomGeometryEvent event) {
        if (!BossShowEditorState.isActive()) return;
        if (!BossShowEditorState.hasAnchor()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double ax = BossShowEditorState.getAnchorX();
        double ay = BossShowEditorState.getAnchorY();
        double az = BossShowEditorState.getAnchorZ();
        float anchorYaw = BossShowEditorState.getAnchorYawDeg();

        Camera cam = mc.gameRenderer.mainCamera();
        Vec3 camPos = cam.position();
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        float lineWidth = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;

        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        List<Frame> frames = BossShowEditorState.getFrames();
        collector.submitCustomGeometry(pose, RenderTypes.lines(), (last, vc) -> {
            //锚点光柱
            AABB beam = new AABB(
                ax - BEAM_HALF, ay, az - BEAM_HALF,
                ax + BEAM_HALF, ay + BEAM_HEIGHT, az + BEAM_HALF
            );
            renderLineBox(last, vc, beam, 0.2f, 1.0f, 0.2f, 1.0f, lineWidth);

            AABB base = new AABB(
                ax - BASE_HALF, ay, az - BASE_HALF,
                ax + BASE_HALF, ay + 0.02, az + BASE_HALF
            );
            renderLineBox(last, vc, base, 0.2f, 1.0f, 0.2f, 1.0f, lineWidth);

            //摄像机路径折线
            if (frames.size() >= 2) {
                renderCameraPath(last, vc, frames, ax, ay, az, anchorYaw, lineWidth);
            }
        });

        //关键帧头颅 + 顺序序号
        List<Integer> kfIndices = BossShowEditorState.getKeyframeFrameIndices();
        if (!kfIndices.isEmpty()) {
            ensureHeadModels(mc);
            if (playerHeadModel != null) {
                Quaternionf camRot = cam.rotation();
                for (int i = 0; i < kfIndices.size(); i++) {
                    int frameIdx = kfIndices.get(i);
                    if (frameIdx < frames.size()) {
                        renderKeyframeHead(pose, collector, frames.get(frameIdx),
                            ax, ay, az, anchorYaw, i + 1, camRot, mc.font);
                    }
                }
            }
        }

        pose.popPose();
    }

    //线框盒：逐条棱输出顶点（原 ShapeRenderer.renderLineBox 已不可用）
    private static void renderLineBox(PoseStack.Pose last, VertexConsumer vc, AABB bb,
                                      float r, float g, float bl, float a, float lineWidth) {
        Matrix4f m = last.pose();
        double[][] c = {
            {bb.minX, bb.minY, bb.minZ}, {bb.maxX, bb.minY, bb.minZ},
            {bb.maxX, bb.minY, bb.maxZ}, {bb.minX, bb.minY, bb.maxZ},
            {bb.minX, bb.maxY, bb.minZ}, {bb.maxX, bb.maxY, bb.minZ},
            {bb.maxX, bb.maxY, bb.maxZ}, {bb.minX, bb.maxY, bb.maxZ}
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] e : edges) {
            double[] p1 = c[e[0]];
            double[] p2 = c[e[1]];
            Vec3 dir = new Vec3(p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]).normalize();
            vc.addVertex(m, (float) p1[0], (float) p1[1], (float) p1[2])
                .setColor(r, g, bl, a)
                .setNormal(last, (float) dir.x, (float) dir.y, (float) dir.z)
                .setLineWidth(lineWidth);
            vc.addVertex(m, (float) p2[0], (float) p2[1], (float) p2[2])
                .setColor(r, g, bl, a)
                .setNormal(last, (float) dir.x, (float) dir.y, (float) dir.z)
                .setLineWidth(lineWidth);
        }
    }

    private static void renderCameraPath(PoseStack.Pose lastPose, VertexConsumer vc,
                                         List<Frame> frames,
                                         double ax, double ay, double az, float anchorYaw,
                                         float lineWidth) {
        List<Vec3> vertices = new ArrayList<>();

        //用实际世界坐标连线，仅对几乎重合的相邻点去重，避免静止段产生零长度线段
        for (Frame f : frames) {
            Vec3 wp = BossShowInterpolator.anchorToWorld(f.dx(), f.dy(), f.dz(), ax, ay, az, anchorYaw);
            if (vertices.isEmpty() || vertices.get(vertices.size() - 1).distanceToSqr(wp) > 1.0e-4) {
                vertices.add(wp);
            }
        }

        if (vertices.size() < 2) return;

        var poseMatrix = lastPose.pose();

        for (int i = 0; i < vertices.size() - 1; i++) {
            Vec3 p1 = vertices.get(i);
            Vec3 p2 = vertices.get(i + 1);
            Vec3 dir = p2.subtract(p1).normalize();
            float nx = (float) dir.x;
            float ny = (float) dir.y;
            float nz = (float) dir.z;
            vc.addVertex(poseMatrix, (float) p1.x, (float) p1.y, (float) p1.z)
                .setColor(PATH_R, PATH_G, PATH_B, PATH_A)
                .setNormal(lastPose, nx, ny, nz)
                .setLineWidth(lineWidth);
            vc.addVertex(poseMatrix, (float) p2.x, (float) p2.y, (float) p2.z)
                .setColor(PATH_R, PATH_G, PATH_B, PATH_A)
                .setNormal(lastPose, nx, ny, nz)
                .setLineWidth(lineWidth);
        }
    }

    private static void renderKeyframeHead(PoseStack pose, SubmitNodeCollector collector,
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
        SkullModelBase.State state = new SkullModelBase.State();
        state.animationPos = 0f;
        state.yRot = worldYaw;
        state.xRot = 0f;
        collector.submitModel(playerHeadModel, state, pose, playerHeadRenderType,
            FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
            ARGB.color((int) (HEAD_ALPHA * 255f), 255, 255, 255), null, 0, null);
        pose.popPose();

        renderHeadLabel(pose, collector, wp, ordinal, camRot, font);
    }

    //在头颅上方渲染朝向摄像机的序号（公告板）
    private static void renderHeadLabel(PoseStack pose, SubmitNodeCollector collector,
                                        Vec3 wp, int ordinal, Quaternionf camRot, Font font) {
        String label = Integer.toString(ordinal);
        pose.pushPose();
        pose.translate(wp.x, wp.y + LABEL_Y_OFFSET, wp.z);
        pose.mulPose(camRot);
        pose.scale(-0.025f, -0.025f, 0.025f);
        float x = -font.width(label) / 2f;
        collector.submitText(pose, x, 0f, FormattedCharSequence.forward(label, Style.EMPTY), true,
            Font.DisplayMode.NORMAL, FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
        pose.popPose();
    }

    private static void ensureHeadModels(Minecraft mc) {
        if (playerHeadModel != null) return;
        playerHeadModel = SkullBlockRenderer.createModel(mc.getEntityModels(), SkullBlock.Types.PLAYER);
        //translucent 渲染类型支持 alpha 混合，用 vanilla 头颅同款默认皮肤纹理
        playerHeadRenderType = RenderTypes.entityTranslucent(DefaultPlayerSkin.getDefaultTexture());
    }
}
