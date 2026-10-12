// OVERLAY-ONLY: community fork multi-version layer (not the authors' code).
package net.eca.mv;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.RenderStateShard;

import java.util.function.Supplier;

/**
 * 1.21.1's ShaderStateShard took a Supplier of a ShaderInstance; 1.21.2's takes a ShaderProgram key that the
 * vanilla ShaderManager compiles. ECA builds its own programs (see {@link ShaderInstance}), so this shard binds
 * the supplied program directly. Not final: the authors subclass the shard anonymously.
 */
public class ShaderShard extends RenderStateShard.ShaderStateShard {
    private final Supplier<? extends CompiledShaderProgram> shader;

    public ShaderShard(Supplier<? extends CompiledShaderProgram> shader) {
        super();
        this.shader = shader;
    }

    @Override
    public void setupRenderState() {
        RenderSystem.setShader(this.shader.get());
    }

    @Override
    public void clearRenderState() {
    }
}
