package net.eca.client.render.shader;

import net.eca.mv.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.eca.client.render.preset.ShaderPresetResourceProvider;
import net.minecraft.client.Minecraft;
import net.eca.mv.ShaderInstance;
import net.minecraft.resources.Identifier;
import net.eca.mv.RegisterShadersEvent;

import java.io.IOException;

@SuppressWarnings("removal")
public class StarlightShader {

    private static ShaderInstance shader;
    private static Uniform timeUniform;
    private static Uniform cameraYawUniform;
    private static Uniform cameraPitchUniform;
    private static Uniform colorKeyColorUniform;
    private static Uniform colorKeyToleranceUniform;
    private static Uniform localUvMinUniform;
    private static Uniform localUvScaleUniform;

    public static void register(RegisterShadersEvent event) throws IOException {
        ShaderInstance starlightShader = EcaShaderInstance.create(
            ShaderPresetResourceProvider.wrap(event.getResourceProvider()),
            Identifier.fromNamespaceAndPath("eca", "starlight"),
            DefaultVertexFormat.BLOCK
        );
        event.registerShader(starlightShader, instance -> {
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

    public static ShaderInstance getShader() {
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
                if (mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
                    net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
                    double yawRad = Math.toRadians(cam.yRot());
                    double pitchRad = Math.toRadians(cam.xRot());
                    double cosPitch = Math.cos(pitchRad);
                    net.minecraft.world.phys.Vec3 look = new net.minecraft.world.phys.Vec3(
                        -Math.sin(yawRad) * cosPitch,
                        -Math.sin(pitchRad),
                        Math.cos(yawRad) * cosPitch
                    );
                    float yaw = (float) Math.atan2(-look.x, look.z);
                    float pitch = (float) Math.asin(-look.y);
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
