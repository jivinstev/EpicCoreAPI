package net.eca.client.render.shader;

import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.InputStream;
import java.util.Optional;
import java.util.function.Consumer;

/* 26.x 已移除 RegisterShadersEvent。ECA 着色器在两个时机注册：客户端初始化时（RegisterRenderPipelinesEvent，
   此时资源包尚未加载，从模组自身 jar 读取程序 JSON，保证 RenderType 构造时着色器已存在），
   以及每次资源重载（资源包与第三方预设生效）。 */
public final class ShaderRegistration {

    private final ResourceProvider resourceProvider;

    private ShaderRegistration(ResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    public static ShaderRegistration of(ResourceProvider resourceProvider) {
        return new ShaderRegistration(resourceProvider);
    }

    public static ShaderRegistration fromModJar() {
        ClassLoader loader = ShaderRegistration.class.getClassLoader();
        return new ShaderRegistration(location -> {
            String path = "assets/" + location.getNamespace() + "/" + location.getPath();
            if (loader.getResource(path) == null) {
                return Optional.empty();
            }
            return Optional.of(new Resource(null, () -> {
                InputStream stream = loader.getResourceAsStream(path);
                if (stream == null) {
                    throw new java.io.FileNotFoundException(path);
                }
                return stream;
            }));
        });
    }

    public ResourceProvider getResourceProvider() {
        return resourceProvider;
    }

    public void registerShader(EcaShaderInstance shader, Consumer<EcaShaderInstance> onLoaded) {
        onLoaded.accept(shader);
    }
}
