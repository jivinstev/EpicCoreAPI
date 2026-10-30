package net.eca.client.render.shader_generator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.RenderBufferPool;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.joml.Matrix3x2f;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.List;

public final class ShaderPreviewRenderer {

    private static final ItemStack PREVIEW_ITEM = createPreviewItem();
    private static final RenderBufferPool PREVIEW_BUFFERS = new RenderBufferPool();
    private static final PerspectiveProjectionMatrixBuffer PREVIEW_PROJECTION = new PerspectiveProjectionMatrixBuffer("eca_shader_preview");

    public static void render(
        GuiGraphics graphics,
        ShaderPreviewSource source,
        ShaderPreviewTarget target,
        int left,
        int top,
        int right,
        int bottom,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        if (source == null || right <= left || bottom <= top) {
            return;
        }

        // 1.21.6+: GuiGraphics only records a GuiRenderState that GuiRenderer draws after the screen; an immediate draw
        // here would land under the hand's perspective projection and under the panel fills. The preview is drawn
        // into a picture-in-picture texture of exactly this rectangle when GuiRenderer prepares the frame, and that
        // texture is blitted in GUI order (above the fills submitted before it), clipped like 1.21.1's enableScissor.
        submit(graphics, left, top, right, bottom, (x0, y0, x1, y1) -> {
            try {
                switch (target) {
                    case PLANE -> renderBlockSurface(
                        source.skybox(),
                        left + 16,
                        top + 16,
                        right - 16,
                        bottom - 16,
                        x0, y0, x1, y1
                    );
                    case SKYBOX -> renderBlockSurface(
                        source.skybox(), left, top, right, bottom, x0, y0, x1, y1
                    );
                    case BOSS_BAR -> {
                        int width = Math.max(80, Math.min(right - left - 40, 360));
                        int height = Math.max(16, Math.min(42, (bottom - top) / 5));
                        int centerX = (left + right) / 2;
                        int centerY = (top + bottom) / 2;
                        renderBlockSurface(
                            source.bossBar(),
                            centerX - width / 2,
                            centerY - height / 2,
                            centerX + width / 2,
                            centerY + height / 2,
                            x0, y0, x1, y1
                        );
                    }
                    case ITEM -> renderItem(source, left, top, right, bottom);
                    case ENTITY -> renderEntity(
                        source,
                        left,
                        top,
                        right,
                        bottom,
                        mouseX,
                        mouseY,
                        partialTick
                    );
                }
            } finally {
                Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            }
        });
    }

    // x0..y1: the picture-in-picture area (its texture is the viewport), where 1.21.1 mapped onto the whole GUI
    private static void renderBlockSurface(
        RenderType renderType,
        int left,
        int top,
        int right,
        int bottom,
        int x0,
        int y0,
        int x1,
        int y1
    ) {
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();

            float areaWidth = x1 - x0;
            float areaHeight = y1 - y0;
            float centerX = ((left + right - 2.0F * x0) / areaWidth) - 1.0F;
            float centerY = 1.0F - ((top + bottom - 2.0F * y0) / areaHeight);
            float scaleX = (right - left) / areaWidth;
            float scaleY = (bottom - top) / areaHeight;
            Matrix4f projection = new Matrix4f()
                .translation(centerX, centerY, 0.0F)
                .scale(scaleX, scaleY, 1.0F);
            RenderSystem.setProjectionMatrix(PREVIEW_PROJECTION.getBuffer(projection), com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);

            BufferBuilder builder = Tesselator.getInstance().begin(renderType.mode(), DefaultVertexFormat.BLOCK);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            renderType.draw(builder.buildOrThrow());
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            modelView.popMatrix();
        }
    }

    private static void blockVertex(
        BufferBuilder builder,
        float x,
        float y,
        float u,
        float v
    ) {
        builder.addVertex((float) (x), (float) (y), (float) (0.5F))
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setLight(0xF000F0)
            .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static void renderItem(
        ShaderPreviewSource source,
        int left,
        int top,
        int right,
        int bottom
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        Level level = minecraft.level;
        List<BakedQuad> quads = net.eca.mv.ItemModels.quads(PREVIEW_ITEM, level, minecraft.player, 0);
        ByteBufferBuilder buffer = PREVIEW_BUFFERS.acquireBuffer();
        MultiBufferSource.BufferSource delegate = MultiBufferSource.immediate(buffer);

        int size = Math.max(40, Math.min(right - left, bottom - top) * 2 / 5);
        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D); // 1.21.1's GUI default
            float[] bounds = computeUvBounds(quads);
            MultiBufferSource forced = ignored -> delegate.getBuffer(source.item());
            EcaShaderInstance.setLocalUvBounds(
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3]
            );
            pose.translate((left + right) / 2.0, (top + bottom) / 2.0, 180.0);
            pose.scale(size, -size, size);
            pose.mulPose(new Quaternionf().rotateY(0.65F).rotateX(-0.35F));
            itemRenderer.renderStatic(PREVIEW_ITEM, ItemDisplayContext.GUI, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, forced, level, 0);
            delegate.endBatch();
        } finally {
            try {
                PREVIEW_BUFFERS.release(buffer);
            } finally {
                EcaShaderInstance.clearLocalUvBounds();
                pose.popPose();
            }
        }
    }

    private static void renderEntity(
        ShaderPreviewSource source,
        int left,
        int top,
        int right,
        int bottom,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        RenderType entityType = source.entity(minecraft.player.getSkin().texture());
        ByteBufferBuilder buffer = PREVIEW_BUFFERS.acquireBuffer();
        MultiBufferSource.BufferSource delegate = MultiBufferSource.immediate(buffer);
        MultiBufferSource forced = ignored -> delegate.getBuffer(entityType);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        int size = Math.max(24, Math.min(right - left, bottom - top) / 3);
        float horizontal = (float) Math.atan(((left + right) * 0.5F - mouseX) / 80.0F);
        float vertical = (float) Math.atan(((top + bottom) * 0.5F - mouseY) / 80.0F);
        Quaternionf orientation = new Quaternionf()
            .rotateZ((float) Math.PI)
            .rotateX(vertical * 0.35F)
            .rotateY(horizontal * 0.35F);

        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            pose.translate((left + right) / 2.0, bottom - 28.0, 220.0);
            pose.mulPose(new Matrix4f().scaling(size, size, -size));
            pose.mulPose(orientation);
            minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            dispatcher.overrideCameraOrientation(new Quaternionf().rotateX(vertical * 0.35F));
            dispatcher.setRenderShadow(false);
            net.eca.mv.Mv.run(() -> dispatcher.render(
                minecraft.player,
                0.0,
                0.0,
                0.0,
                partialTick,
                pose,
                forced,
                0xF000F0
            ));
            delegate.endBatch();
        } finally {
            try {
                PREVIEW_BUFFERS.release(buffer);
            } finally {
                dispatcher.setRenderShadow(true);
                pose.popPose();
            }
        }
    }

    private static ItemStack createPreviewItem() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    // 1.21.5: an item layer holds the model's quads directly (one list, every face), so the per-direction getQuads loop is gone
    private static float[] computeUvBounds(List<BakedQuad> quads) {
        float uMin = Float.POSITIVE_INFINITY;
        float vMin = Float.POSITIVE_INFINITY;
        float uMax = Float.NEGATIVE_INFINITY;
        float vMax = Float.NEGATIVE_INFINITY;

        if (quads != null) {
            float[] bounds = includeQuads(quads, uMin, vMin, uMax, vMax);
            uMin = bounds[0];
            vMin = bounds[1];
            uMax = bounds[2];
            vMax = bounds[3];
        }

        if (uMin > uMax || vMin > vMax) {
            return new float[]{0.0F, 0.0F, 1.0F, 1.0F};
        }
        return new float[]{
            uMin,
            vMin,
            1.0F / Math.max(uMax - uMin, 1.0E-6F),
            1.0F / Math.max(vMax - vMin, 1.0E-6F)
        };
    }

    private static float[] includeQuads(
        List<BakedQuad> quads,
        float uMin,
        float vMin,
        float uMax,
        float vMax
    ) {
        int stride = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
        int uvOffset = 4;
        for (BakedQuad quad : quads) {
            int[] vertices = quad.vertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * stride + uvOffset;
                if (base + 1 >= vertices.length) {
                    continue;
                }
                float u = Float.intBitsToFloat(vertices[base]);
                float v = Float.intBitsToFloat(vertices[base + 1]);
                uMin = Math.min(uMin, u);
                vMin = Math.min(vMin, v);
                uMax = Math.max(uMax, u);
                vMax = Math.max(vMax, v);
            }
        }
        return new float[]{uMin, vMin, uMax, vMax};
    }

    // ==================== 1.21.8: deferred GUI drawing (picture in picture) ====================

    /** Draws immediate content (its own shaders, render types, GL state) in the GUI coordinates of [x0, x1) x [y0, y1),
     *  which is the whole viewport while it runs; the GUI pose and scissor of the submitting call apply to the result. */
    @FunctionalInterface
    public interface GuiPainter {
        void paint(int x0, int y0, int x1, int y1);
    }

    /**
     * Records `painter` for the rectangle [x0, x1) x [y0, y1) of the current GUI pose. When GuiRenderer prepares the
     * frame, {@link GuiPipRenderer} runs it into a texture of the rectangle's size (times the GUI scale), cleared to
     * transparent, with an orthographic projection over the rectangle in GUI units (z -10000..10000, as the 1.21.1 GUI)
     * and the model-view moved by (-x0, -y0); the texture is then blitted (premultiplied alpha) into the GUI render
     * state at this call's place in the GUI order, with this call's pose and scissor -- above what was submitted
     * before it over the same pixels, below what comes after.
     */
    public static void submit(GuiGraphics graphics, int x0, int y0, int x1, int y1, GuiPainter painter) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        ScreenRectangle area = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        ScreenRectangle bounds = scissor != null ? scissor.intersection(area) : area;
        graphics.submitPictureInPictureRenderState(new GuiPip(painter, x0, y0, x1, y1, pose, scissor, bounds));
    }

    public record GuiPip(
        GuiPainter painter,
        int x0,
        int y0,
        int x1,
        int y1,
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
    ) implements PictureInPictureRenderState {
        @Override
        public float scale() {
            return 1.0F;
        }
    }

    public static final class GuiPipRenderer extends PictureInPictureRenderer<GuiPip> {
        private final PerspectiveProjectionMatrixBuffer projection = new PerspectiveProjectionMatrixBuffer("eca_gui_pip");

        public GuiPipRenderer(MultiBufferSource.BufferSource bufferSource) {
            super(bufferSource);
        }

        @Override
        public Class<GuiPip> getRenderStateClass() {
            return GuiPip.class;
        }

        @Override
        protected void renderToTexture(GuiPip state, PoseStack unused) {
            int width = state.x1() - state.x0();
            int height = state.y1() - state.y0();
            RenderSystem.setProjectionMatrix(
                projection.getBuffer(new Matrix4f().setOrtho(0.0F, width, height, 0.0F, -10000.0F, 10000.0F)),
                com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC
            );
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.identity().translate(-state.x0(), -state.y0(), 0.0F);
                state.painter().paint(state.x0(), state.y0(), state.x1(), state.y1());
            } finally {
                modelView.popMatrix();
            }
        }

        @Override
        protected String getTextureLabel() {
            return "eca_gui_pip";
        }

        @Override
        public void close() {
            super.close();
            projection.close();
        }
    }

    @EventBusSubscriber(modid = "eca", value = Dist.CLIENT)
    public static final class GuiPipRegistration {
        @SubscribeEvent
        public static void register(RegisterPictureInPictureRenderersEvent event) {
            event.register(GuiPip.class, GuiPipRenderer::new);
        }

        private GuiPipRegistration() {}
    }

    private ShaderPreviewRenderer() {}
}
