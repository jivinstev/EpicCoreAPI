package net.eca.client.render.shader_generator;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.eca.client.render.shader.EcaShaderInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.Model;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.joml.Matrix3x2f;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
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
import java.util.Optional;
import java.util.OptionalDouble;

public final class ShaderPreviewRenderer {

    private static final ItemStack PREVIEW_ITEM = createPreviewItem();
    private static final ProjectionMatrixBuffer PREVIEW_PROJECTION = new ProjectionMatrixBuffer("eca_shader_preview");
    /** 26.3 (#33): where the forced preview geometry is collected; drawn by endBatch through the shared helper, so a
     *  LegacyRenderType draws with its shader's own uniform blocks bound (vanilla's feature path binds only its own).
     *  A vanilla type drawn this way goes to the main target (26.3 has no per-type output target), which is where
     *  renderFeatures draws the rest of the preview too. */
    private static final net.eca.mv.MultiBufferSource.BufferSource PREVIEW_BUFFERS =
        net.eca.mv.MultiBufferSource.immediate(new ByteBufferBuilder(262144));

    public static void render(
        GuiGraphicsExtractor graphics,
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
                Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
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
        // 26.2: Tesselator is gone; the builder writes into its own buffer, freed once the mesh is drawn
        try (ByteBufferBuilder buffer = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 8)) {
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

            BufferBuilder builder = new BufferBuilder(buffer, renderType.primitiveTopology(), DefaultVertexFormat.BLOCK);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, -1.0F, 1.0F, 0.0F, 0.0F);
            blockVertex(builder, 1.0F, 1.0F, 1.0F, 0.0F);
            blockVertex(builder, 1.0F, -1.0F, 1.0F, 1.0F);
            blockVertex(builder, -1.0F, -1.0F, 0.0F, 1.0F);
            draw(renderType, builder.buildOrThrow());
        } finally {
            RenderSystem.setProjectionMatrix(savedProjection, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            modelView.popMatrix();
        }
    }

    /** 1.21.1's {@code RenderType.draw(MeshData)}: the shared 26.2 helper (a legacy type draws with its own uniforms). */
    private static void draw(RenderType renderType, MeshData mesh) {
        net.eca.mv.MultiBufferSource.draw(renderType, mesh);
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
        Level level = minecraft.level;
        List<BakedQuad> quads = net.eca.mv.ItemModels.quads(PREVIEW_ITEM, level, minecraft.player, 0);
        // 1.21.10: an item is submitted as render state and drawn by a feature dispatcher; 26.2 has no buffer source to
        // force, so the submits themselves carry the preview's render type, as the forced MultiBufferSource did
        SubmitNodeStorage submits = new ForcedSubmits(source.item(), PREVIEW_BUFFERS);
        FeatureRenderDispatcher features = featureDispatcher();
        ItemStackRenderState itemState = new ItemStackRenderState();

        int size = Math.max(40, Math.min(right - left, bottom - top) * 2 / 5);
        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D); // 1.21.1's GUI default
            float[] bounds = computeUvBounds(quads);
            EcaShaderInstance.setLocalUvBounds(
                bounds[0],
                bounds[1],
                bounds[2],
                bounds[3]
            );
            pose.translate((left + right) / 2.0, (top + bottom) / 2.0, 180.0);
            pose.scale(size, -size, size);
            pose.mulPose(new Matrix4f().rotateY(0.65F).rotateX(-0.35F));
            minecraft.getItemModelResolver().updateForTopItem(itemState, PREVIEW_ITEM, ItemDisplayContext.GUI, level, minecraft.player, 0);
            itemState.submit(pose, submits, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
            PREVIEW_BUFFERS.endBatch();
            renderFeatures(features, submits);
        } finally {
            EcaShaderInstance.clearLocalUvBounds();
            pose.popPose();
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

        RenderType entityType = source.entity(minecraft.player.getSkin().body().texturePath());
        SubmitNodeStorage submits = new ForcedSubmits(entityType, PREVIEW_BUFFERS);
        FeatureRenderDispatcher features = featureDispatcher();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        int size = Math.max(24, Math.min(right - left, bottom - top) / 3);
        float horizontal = (float) Math.atan(((left + right) * 0.5F - mouseX) / 80.0F);
        float vertical = (float) Math.atan(((top + bottom) * 0.5F - mouseY) / 80.0F);
        Matrix4f orientation = new Matrix4f()
            .rotateZ((float) Math.PI)
            .rotateX(vertical * 0.35F)
            .rotateY(horizontal * 0.35F);

        PoseStack pose = new PoseStack();
        pose.pushPose();
        try {
            pose.translate((left + right) / 2.0, bottom - 28.0, 220.0);
            pose.mulPose(new Matrix4f().scaling(size, size, -size));
            pose.mulPose(orientation);
            minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
            // 1.21.10: the camera orientation, light and shadow are per-submit render state, not dispatcher switches
            CameraRenderState camera = new CameraRenderState();
            camera.orientation = new Quaternionf().rotateX(vertical * 0.35F);
            EntityRenderState state = dispatcher.extractEntity(minecraft.player, partialTick);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            net.eca.mv.Mv.run(() -> {
                dispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submits);
                PREVIEW_BUFFERS.endBatch();
                renderFeatures(features, submits);
            });
        } finally {
            pose.popPose();
        }
    }

    // 26.3: the dispatcher prepares a frame from the storage and draws it in a render pass on the main target -- or, inside
    // GuiPipRenderer, on its picture-in-picture texture (the fork layer's output override, net.eca.mv.LegacyGl.setOutput)
    private static void renderFeatures(FeatureRenderDispatcher features, SubmitNodeStorage submits) {
        try (
            FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(submits);
            RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "ECA shader preview", net.eca.mv.LegacyGl.mainColorView(), Optional.empty(), net.eca.mv.LegacyGl.mainDepthView(), OptionalDouble.empty())
        ) {
            RenderSystem.bindDefaultUniforms(renderPass);
            FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
        }
    }

    // 26.2: a feature dispatcher draws through the game renderer's staged vertex buffer, so the game renderer's own is used
    private static FeatureRenderDispatcher featureDispatcher() {
        return Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
    }

    /** Hands every order the forcing collection below. */
    private static final class ForcedSubmits extends SubmitNodeStorage {
        private final RenderType forced;
        private final net.eca.mv.MultiBufferSource.BufferSource buffers;

        ForcedSubmits(RenderType forced, net.eca.mv.MultiBufferSource.BufferSource buffers) {
            this.forced = forced;
            this.buffers = buffers;
        }

        @Override
        public SubmitNodeCollection order(int order) {
            return this.getSubmitsPerOrder().computeIfAbsent(order, ignored -> new ForcedCollection(this.forced, this.buffers, false, new net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase()));
        }
    }

    /**
     * Every model, block, item and custom-geometry submit with the forced render type, as the forced buffer source handed
     * it out on 1.21. 26.3 (#33): the forced type is the preview's LegacyRenderType, whose shader has uniform blocks of its
     * own, and 26.3's feature path (prepareFrame -> PreparedRenderType.drawFromBuffer) binds only vanilla's. So these
     * submits are not recorded for the dispatcher: their vertices go straight into the shared MultiBufferSource helper,
     * posed from the submit as the model feature renderer would pose them, and the caller's endBatch draws them through
     * LegacyRenderType's own draw. Whatever else is submitted (name tags, text, shadows) keeps its vanilla type and still
     * goes through renderFeatures.
     */
    private static final class ForcedCollection extends SubmitNodeCollection {
        private final RenderType forced;
        private final net.eca.mv.MultiBufferSource.BufferSource buffers;
        private final QuadInstance quadInstance = new QuadInstance();

        ForcedCollection(RenderType forced, net.eca.mv.MultiBufferSource.BufferSource buffers, boolean sorted,
                         net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase phase) {
            super(sorted, phase);
            this.forced = forced;
            this.buffers = buffers;
        }

        @Override
        public <S> void submitModel(
            Model<? super S> model,
            S state,
            PoseStack poseStack,
            RenderType ignored,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            UvMapping uvMapping,
            int outlineColor
        ) {
            com.mojang.blaze3d.vertex.VertexConsumer consumer = this.buffers.getBuffer(this.forced);
            if (uvMapping != null) {
                consumer = uvMapping.wrap(consumer);
            }
            model.setupAnim(state);
            var hidden = net.neoforged.neoforge.client.ClientHooks.updateModelVisibility(model, state);
            try {
                model.renderToBuffer(poseStack, consumer, lightCoords, overlayCoords, tintedColor);
            } finally {
                net.neoforged.neoforge.client.ClientHooks.resetVisibility(hidden);
            }
        }

        @Override
        public void submitBlockModel(
            PoseStack poseStack,
            RenderType ignored,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor
        ) {
            com.mojang.blaze3d.vertex.VertexConsumer consumer = this.buffers.getBuffer(this.forced);
            PoseStack.Pose pose = poseStack.last();
            this.quadInstance.setLightCoords(lightCoords);
            this.quadInstance.setOverlayCoords(overlayCoords);
            for (BlockStateModelPart part : parts) {
                for (Direction side : Direction.values()) {
                    putTinted(pose, part.getQuads(side), tintLayers, consumer);
                }
                putTinted(pose, part.getQuads(null), tintLayers, consumer);
            }
        }

        private void putTinted(PoseStack.Pose pose, List<BakedQuad> quads, int[] tintLayers,
                               com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            for (BakedQuad quad : quads) {
                int layer = quad.materialInfo().tintIndex();
                this.quadInstance.setColor(layer >= 0 && layer < tintLayers.length ? tintLayers[layer] : -1);
                consumer.putBakedQuad(pose, quad, this.quadInstance);
            }
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType ignored, SubmitNodeCollector.CustomGeometryRenderer renderer) {
            renderer.render(poseStack.last(), this.buffers.getBuffer(this.forced));
        }

        // 26.2: an item submit names no render type (each quad carries its own), so its quads go into the forced type
        @Override
        public void submitItem(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            ItemQuads quads,
            ItemStackRenderState.FoilType foilType
        ) {
            com.mojang.blaze3d.vertex.VertexConsumer consumer = this.buffers.getBuffer(this.forced);
            PoseStack.Pose pose = poseStack.last();
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(lightCoords);
            instance.setOverlayCoords(overlayCoords);
            for (BakedQuad quad : quads.all()) {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                int layer = material.tintIndex();
                instance.setColor(material.isTinted() && layer >= 0 && layer < tintLayers.length ? tintLayers[layer] : -1);
                consumer.putBakedQuad(pose, quad, instance);
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
        // 1.21.11: BakedQuad keeps each vertex's UV packed (packedUV), not in an int[] vertex array
        // 26.1.2: one packedUV0..3 accessor per vertex
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < 4; vertex++) {
                long packedUv = switch (vertex) {
                    case 0 -> quad.packedUV0();
                    case 1 -> quad.packedUV1();
                    case 2 -> quad.packedUV2();
                    default -> quad.packedUV3();
                };
                float u = net.minecraft.client.model.geom.builders.UVPair.unpackU(packedUv);
                float v = net.minecraft.client.model.geom.builders.UVPair.unpackV(packedUv);
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
    public static void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, GuiPainter painter) {
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
        private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("eca_gui_pip");

        // 26.2: a picture-in-picture renderer has no buffer source of its own (its features go through the dispatcher)
        public GuiPipRenderer() {
            super();
        }

        @Override
        public Class<GuiPip> getRenderStateClass() {
            return GuiPip.class;
        }

        @Override
        protected void renderToTexture(GuiPip state, PoseStack unused, SubmitNodeCollector unusedSubmits) {
            int width = state.x1() - state.x0();
            int height = state.y1() - state.y0();
            // 26.3: the rectangle's offset is in the projection (not the model-view), so what is collected for this texture's
            // pass (GuiTexturePasses, drawn after renderToTexture returns and the model-view is popped) lands in place too;
            // the painter's draws go to this renderer's texture through the fork layer's output override
            // (PictureInPictureRendererMixin, net.eca.mv.LegacyGl.setOutput), as 26.2's RenderSystem override did
            // 26.2+/26.3 depth is reverse-Z: as vanilla's Projection.getMatrix, the ortho is built with near and far swapped
            // (setOrtho(..., zFar, zNear, device.isZZeroToOne())); a forward ortho clipped or back-to-front sorted the item
            // and entity previews (z 180-220). The 1.21.1 GUI range -10000..10000 is kept.
            boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
            RenderSystem.setProjectionMatrix(
                projection.getBuffer(new Matrix4f().setOrtho(state.x0(), state.x0() + width, state.y0() + height, state.y0(),
                    10000.0F, -10000.0F, zeroToOne)),
                com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC
            );
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                modelView.identity();
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
