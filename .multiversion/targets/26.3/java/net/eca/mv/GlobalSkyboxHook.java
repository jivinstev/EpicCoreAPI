// OVERLAY-ONLY: community fork multi-version layer (1.21.5 copy of the 1.21.3 overlay: the draw calls go through
// net.eca.mv.LegacyGl / RenderType.draw). The authors' LevelRendererMixin.renderSky TAIL injection,
// moved to RenderLevelStageEvent AFTER_SKY: in 1.21.2 the sky is a frame-graph pass and NeoForge fires that
// stage at the end of it. The drawing code is the authors', unchanged.
// 26.3 (#32): AfterSky is posted only inside the sky pass (lambda$addSkyPass$0), which 26.3's render() does not add when
// shouldRenderSky is false and addSkyPass does not add for lava / powder snow, a sky-blocking effect or Skybox NONE (the
// Nether), and it is posted even after a custom CustomSkyboxRenderer.renderSky drew the sky. So the 26.3
// LevelRendererMixin overlay draws the skybox from a frame pass of its own and sets passOwned; the AfterSky handler then
// stands down. The PoseStack starts from identity: render() has pushed the view rotation onto RenderSystem's model-view
// stack, which the helpers upload as ModelViewMat (the old mulPose(event.getModelViewMatrix()) applied it twice).
package net.eca.mv;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.eca.client.render.shader.EcaShaderInstance;
import net.eca.config.EcaConfiguration;
import net.eca.util.entity_extension.EntityExtensionClientState;
import net.eca.util.entity_extension.ForceLoadingManager;
import net.eca.util.entity_extension.GlobalSkyboxExtension;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import net.eca.EcaMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = EcaMod.MOD_ID, value = Dist.CLIENT)
public final class GlobalSkyboxHook {
    private GlobalSkyboxHook() {
    }

    // ==================== 全局天空盒渲染 ====================

    /** Set by the 26.3 LevelRendererMixin overlay, which draws the skybox from its own frame pass. */
    public static volatile boolean passOwned;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterSky event) {
        if (passOwned) {
            return;
        }
        render(new Matrix4f(), Minecraft.getInstance().gameRenderer.mainCamera());
    }

    /** The authors' renderSky TAIL handler body. frustumMatrix is what the PoseStack starts from: 1.21.1 passed the view
     *  matrix because its model-view stack did not hold it; on 26.3 the stack holds it, so callers pass identity. */
    public static void render(Matrix4f frustumMatrix, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || camera == null) {
            return;
        }
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(frustumMatrix);
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }

        GlobalSkyboxExtension skybox = getGlobalSkyboxExtension(minecraft.level.dimension().identifier());
        if (skybox == null || !skybox.enabled()) {
            return;
        }

        float size = Math.max(20.0f, skybox.size());
        float alpha = Mth.clamp(skybox.alpha(), 0.0f, 1.0f);
        if (alpha <= 0.0f) {
            return;
        }

        if (skybox.enableTexture() && skybox.texture() != null) {
            drawTextureSkybox(poseStack, skybox, size, alpha);
        }

        // Oculus光影激活时shader skybox延迟到GameRendererPostLevelMixin（管线合成后）渲染
        if (!EcaShaderInstance.isOculusShadersActive() && skybox.enableShader() && skybox.shaderRenderType() != null) {
            drawShaderSkybox(poseStack, skybox.shaderRenderType(), size, alpha);
        }
    }

    private static GlobalSkyboxExtension getGlobalSkyboxExtension(Identifier dimensionId) {
        return EntityExtensionClientState.getActiveSkybox(dimensionId);
    }

    private static void drawTextureSkybox(PoseStack poseStack, GlobalSkyboxExtension skybox, float size, float alpha) {
        LegacyGl.enableBlend();
        LegacyGl.depthMask(false);
        LegacyGl.setShader(LegacyGl.Vanilla.POSITION_TEX_COLOR);
        LegacyGl.setShaderTexture(0, (Identifier) skybox.texture());
        LegacyGl.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        float uvScale = Math.max(1.0f, skybox.textureUvScale());
        int red = (int)(Mth.clamp(skybox.textureRed(), 0.0f, 1.0f) * 255.0f);
        int green = (int)(Mth.clamp(skybox.textureGreen(), 0.0f, 1.0f) * 255.0f);
        int blue = (int)(Mth.clamp(skybox.textureBlue(), 0.0f, 1.0f) * 255.0f);
        int alphaInt = (int)(alpha * 255.0f);

        // 26.2 dropped Tesselator: a BufferBuilder is built over its own ByteBufferBuilder
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432)) {
            for (int i = 0; i < 6; ++i) {
                poseStack.pushPose();
                rotateToFace(poseStack, i);
                Matrix4f matrix = poseStack.last().pose();
                BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferBuilder.addVertex(matrix, -size, -size, -size).setUv(0.0f, 0.0f).setColor(red, green, blue, alphaInt);
                bufferBuilder.addVertex(matrix, -size, -size, size).setUv(0.0f, uvScale).setColor(red, green, blue, alphaInt);
                bufferBuilder.addVertex(matrix, size, -size, size).setUv(uvScale, uvScale).setColor(red, green, blue, alphaInt);
                bufferBuilder.addVertex(matrix, size, -size, -size).setUv(uvScale, 0.0f).setColor(red, green, blue, alphaInt);
                LegacyGl.drawWithShader(bufferBuilder.buildOrThrow());
                poseStack.popPose();
            }
        }

        LegacyGl.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        LegacyGl.depthMask(true);
        LegacyGl.disableBlend();
    }

    private static void drawShaderSkybox(PoseStack poseStack, RenderType renderType, float radius, float alpha) {
        int alphaInt = (int) (Mth.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        int light = 15728880;
        int segments = 32;
        int rings = 16;

        Matrix4f matrix = poseStack.last().pose();

        // 26.2 dropped Tesselator: a BufferBuilder is built over its own ByteBufferBuilder
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(786432)) {
            BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.BLOCK);

            for (int ring = 0; ring < rings; ring++) {
                float phi1 = (float) Math.PI * ring / rings;
                float phi2 = (float) Math.PI * (ring + 1) / rings;
                float y1 = (float) Math.cos(phi1) * radius;
                float y2 = (float) Math.cos(phi2) * radius;
                float r1 = (float) Math.sin(phi1) * radius;
                float r2 = (float) Math.sin(phi2) * radius;

                for (int seg = 0; seg < segments; seg++) {
                    float theta1 = (float) (2.0 * Math.PI * seg / segments);
                    float theta2 = (float) (2.0 * Math.PI * (seg + 1) / segments);

                    float x1 = (float) Math.cos(theta1);
                    float z1 = (float) Math.sin(theta1);
                    float x2 = (float) Math.cos(theta2);
                    float z2 = (float) Math.sin(theta2);

                    bufferBuilder.addVertex(matrix, x1 * r1, y1, z1 * r1).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(x1, y1 / radius, z1);
                    bufferBuilder.addVertex(matrix, x1 * r2, y2, z1 * r2).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(x1, y2 / radius, z1);
                    bufferBuilder.addVertex(matrix, x2 * r2, y2, z2 * r2).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(x2, y2 / radius, z2);
                    bufferBuilder.addVertex(matrix, x2 * r1, y1, z2 * r1).setColor(255, 255, 255, alphaInt).setUv(0.0f, 0.0f).setLight(light).setNormal(x2, y1 / radius, z2);
                }
            }

            // 1.21.5: a RenderType carries its own pipeline state, so it draws the mesh itself (setup, draw, clear);
            // 26.2's RenderType has no draw(MeshData), so the layer's LegacyRenderType draws it
            MeshData mesh = bufferBuilder.buildOrThrow();
            if (renderType instanceof LegacyRenderType legacy) {
                legacy.draw(mesh);
            } else {
                LegacyGl.setupRenderState(renderType);
                LegacyGl.drawWithShader(mesh);
                LegacyGl.clearRenderState(renderType);
            }
        }
    }

    private static void rotateToFace(PoseStack poseStack, int face) {
        if (face == 1) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(90.0)));
        } else if (face == 2) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(-90.0)));
        } else if (face == 3) {
            poseStack.mulPose(new Matrix4f().rotationX((float) Math.toRadians(180.0)));
        } else if (face == 4) {
            poseStack.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(90.0)));
        } else if (face == 5) {
            poseStack.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-90.0)));
        }
    }
}
