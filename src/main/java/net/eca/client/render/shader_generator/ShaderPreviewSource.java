package net.eca.client.render.shader_generator;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public interface ShaderPreviewSource {

    Component displayName();

    RenderType bossBar();

    RenderType skybox();

    RenderType item();

    RenderType entity(Identifier texture);
}
