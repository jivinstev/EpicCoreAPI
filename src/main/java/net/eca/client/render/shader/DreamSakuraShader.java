package net.eca.client.render.shader;

import net.eca.client.render.shader.EcaShaderInstance.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.eca.client.render.preset.ShaderPresetResourceProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.IOException;

@SuppressWarnings("removal")
public class DreamSakuraShader {

    private static EcaShaderInstance shader;
    private static Uniform timeUniform;
    private static Uniform cameraYawUniform;
    private static Uniform cameraPitchUniform;
    private static Uniform colorKeyColorUniform;
    private static Uniform colorKeyToleranceUniform;
    private static Uniform localUvMinUniform;
    private static Uniform localUvScaleUniform;

    public static void register(ShaderRegistration event) throws IOException {
        EcaShaderInstance dreamSakuraShader = EcaShaderInstance.create(
            ShaderPresetResourceProvider.wrap(event.getResourceProvider()),
            Identifier.fromNamespaceAndPath("eca", "dream_sakura"),
            DefaultVertexFormat.BLOCK
        );
        event.registerShader(dreamSakuraShader, instance -> {
            shader = instance;
            timeUniform = shader.getUniform("GameTime");
            cameraYawUniform = shader.getUniform("CameraYaw");
            cameraPitchUniform = shader.getUniform("CameraPitch");
            colorKeyColorUniform = shader.getUniform("ColorKeyColor");
            colorKeyToleranceUniform = shader.getUniform("ColorKeyTolerance");
            localUvMinUniform = shader.getUniform("LocalUvMin");
            localUvScaleUniform = shader.getUniform("LocalUvScale");
        });
    }

    public static EcaShaderInstance getShader() {
        return shader;
    }

    public static void applyUniforms() {
        try {
            if (timeUniform != null) {
                float systemTime = (System.currentTimeMillis() % 1000000L) / 1000.0F;
                timeUniform.set(systemTime);
            }
            if (cameraYawUniform != null || cameraPitchUniform != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
                    float yaw = (float) Math.toRadians(mc.gameRenderer.mainCamera().getYRot());
                    float pitch = (float) Math.toRadians(mc.gameRenderer.mainCamera().getXRot());
                    if (cameraYawUniform != null) {
                        cameraYawUniform.set(yaw);
                    }
                    if (cameraPitchUniform != null) {
                        cameraPitchUniform.set(pitch);
                    }
                }
            }
            EcaShaderInstance.applyColorKeyUniforms(colorKeyColorUniform, colorKeyToleranceUniform);
            EcaShaderInstance.applyLocalUvBoundsUniforms(localUvMinUniform, localUvScaleUniform);
        } catch (Exception ignored) {
        }
    }

    public static boolean isAvailable() {
        return shader != null;
    }

    public static void clear() {
        shader = null;
        timeUniform = null;
        cameraYawUniform = null;
        cameraPitchUniform = null;
        colorKeyColorUniform = null;
        colorKeyToleranceUniform = null;
        localUvMinUniform = null;
        localUvScaleUniform = null;
    }
}
