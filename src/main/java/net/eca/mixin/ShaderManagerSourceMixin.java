package net.eca.mixin;

import com.mojang.blaze3d.shaders.ShaderType;
import net.eca.client.render.shader.EcaShaderSources;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/* ECA 程序的 GLSL 由 EcaShaderSources 提供：预设目录优先，并把 150 的零散 uniform 升级为 uniform 块。 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public abstract class ShaderManagerSourceMixin {

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private void eca$provideEcaSource(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        String source = EcaShaderSources.resolve(id, type, cir.getReturnValue());
        if (source != cir.getReturnValue()) {
            cir.setReturnValue(source);
        }
    }
}
