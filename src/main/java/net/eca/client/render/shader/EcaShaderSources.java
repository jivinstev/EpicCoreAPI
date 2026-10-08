package net.eca.client.render.shader;

import com.mojang.blaze3d.shaders.ShaderType;
import net.eca.util.EcaLogger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* 26.x 的着色器源码由 ShaderManager 按 id 提供，ShaderManagerSourceMixin 把其中属于 ECA 程序的源码交给这里：
   先按程序自己的资源源（预设目录优先）取源码，再把 GLSL 150 的零散 uniform 升级为 26.x 的 uniform 块。
   作者的 GLSL 正文保持原样，第三方预设也照常可用。 */
public final class EcaShaderSources {

    private static final Pattern VERSION = Pattern.compile("(?m)^#version\\s+150[^\\n]*$");
    private static final Pattern LOOSE = Pattern.compile("(?m)^[ \\t]*uniform[ \\t]+(?!sampler)(\\w+)[ \\t]+(\\w+)[ \\t]*;[ \\t]*\\r?\\n");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    //原版块成员名：与 ECA 自有成员重名的加 Mc 前缀（只为占位，std140 按偏移绑定）
    private static final Map<String, List<String>> VANILLA_BLOCKS = Map.of(
        "DynamicTransforms", List.of("mat4 ModelViewMat", "vec4 ColorModulator", "vec3 ModelOffset", "mat4 TextureMat"),
        "Projection", List.of("mat4 ProjMat"),
        "Globals", List.of("ivec3 CameraBlockPos", "vec3 CameraOffset", "vec2 ScreenSize", "float GlintAlpha",
            "float GameTime", "int MenuBlurRadius", "int UseRgss"));
    private static final Map<String, String> BLOCK_OF = Map.of(
        "ModelViewMat", "DynamicTransforms", "ProjMat", "Projection",
        "GameTime", "Globals", "ScreenSize", "Globals", "GlintAlpha", "Globals");

    private EcaShaderSources() {}

    public static String resolve(Identifier id, ShaderType type, String vanillaSource) {
        for (EcaShaderInstance program : EcaShaderInstance.current()) {
            boolean vertex = type == ShaderType.VERTEX && id.equals(program.vertexShader());
            boolean fragment = type == ShaderType.FRAGMENT && id.equals(program.fragmentShader());
            if (vertex || fragment) {
                String source = read(program, id, type).orElse(vanillaSource);
                return source == null ? null : upgrade(source, program);
            }
        }
        return vanillaSource;
    }

    //经程序的资源源读取（ShaderPresetResourceProvider 会优先返回预设目录中的同名文件）
    private static Optional<String> read(EcaShaderInstance program, Identifier id, ShaderType type) {
        Identifier file = type.idConverter().idToFile(id);
        Optional<Resource> resource = program.resourceProvider().getResource(file);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(org.apache.commons.io.IOUtils.toString(reader));
        } catch (IOException e) {
            EcaLogger.warn("[EcaShaderSources] failed to read {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    static String upgrade(String source, EcaShaderInstance program) {
        Matcher version = VERSION.matcher(source);
        if (!version.find()) {
            return source;
        }
        Set<String> block = new LinkedHashSet<>();
        for (EcaShaderInstance.Uniform uniform : program.blockMembers()) {
            block.add(uniform.getName());
        }
        StringBuilder body = new StringBuilder();
        Matcher loose = LOOSE.matcher(source);
        int last = 0;
        while (loose.find()) {
            String name = loose.group(2);
            if (block.contains(name) || BLOCK_OF.containsKey(name)) {
                body.append(source, last, loose.start());
                last = loose.end();
            } else if (WARNED.add(program.getLocation() + "/" + name)) {
                EcaLogger.warn("[EcaShaderSources] {} declares uniform {} that no program lists; it stays 0", program.getLocation(), name);
            }
        }
        body.append(source.substring(last));
        String text = body.toString();
        StringBuilder header = new StringBuilder("#version 330\n\n");
        Set<String> vanilla = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : BLOCK_OF.entrySet()) {
            if (Pattern.compile("\\b" + entry.getKey() + "\\b").matcher(text).find()) {
                vanilla.add(entry.getValue());
            }
        }
        for (String name : List.of("DynamicTransforms", "Projection", "Globals")) {
            if (!vanilla.contains(name)) {
                continue;
            }
            header.append("layout(std140) uniform ").append(name).append(" {\n");
            for (String member : VANILLA_BLOCKS.get(name)) {
                String field = member.substring(member.indexOf(' ') + 1);
                boolean ours = BLOCK_OF.containsKey(field) && BLOCK_OF.get(field).equals(name);
                header.append("    ").append(ours ? member : member.replace(" " + field, " Mc" + field)).append(";\n");
            }
            header.append("};\n\n");
        }
        header.append("layout(std140) uniform ").append(EcaShaderInstance.BLOCK).append(" {\n");
        for (EcaShaderInstance.Uniform uniform : program.blockMembers()) {
            header.append("    ").append(uniform.glslType()).append(' ').append(uniform.getName()).append(";\n");
        }
        header.append("};\n");
        Matcher at = VERSION.matcher(text);
        at.find();
        return text.substring(0, at.start()) + header + text.substring(at.end());
    }
}
