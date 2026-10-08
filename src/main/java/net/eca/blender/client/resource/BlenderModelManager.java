package net.eca.blender.client.resource;

import net.eca.blender.client.model.BlenderModelAsset;
import net.eca.blender.model.BlenderModelDefinition;
import net.eca.blender.client.runtime.BlendModelLoader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.eca.util.EcaLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("removal")
public final class BlenderModelManager extends SimplePreparableReloadListener<Map<Identifier, BlenderModelAsset>> {
    public static final BlenderModelManager INSTANCE = new BlenderModelManager();
    private static final String ROOT = "eca/blender";

    private volatile Map<Identifier, BlenderModelAsset> models = Map.of();
    private final Set<Identifier> dynamicTextures = new HashSet<>();

    private BlenderModelManager() {
    }

    public BlenderModelAsset get(Identifier id) {
        return id == null ? null : models.get(id);
    }

    @Override
    protected Map<Identifier, BlenderModelAsset> prepare(ResourceManager resourceManager,
                                                               ProfilerFiller profiler) {
        Map<Identifier, BlenderModelAsset> loaded = new HashMap<>();
        Map<Identifier, Resource> definitions = resourceManager.listResources(ROOT,
            location -> location.getPath().endsWith("/definition.json"));
        for (Map.Entry<Identifier, Resource> entry : definitions.entrySet()) {
            Identifier definitionLocation = entry.getKey();
            try {
                String path = definitionLocation.getPath();
                int start = ROOT.length() + 1;
                int end = path.length() - "/definition.json".length();
                if (start >= end) {
                    throw new IOException("Definition has no model id");
                }
                Identifier modelId = Identifier.fromNamespaceAndPath(definitionLocation.getNamespace(),
                    path.substring(start, end));
                JsonObject json;
                try (InputStream input = entry.getValue().open();
                     InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                    json = JsonParser.parseReader(reader).getAsJsonObject();
                }
                BlenderModelDefinition definition = BlenderModelDefinition.parse(json);
                String folder = path.substring(0, path.length() - "definition.json".length());
                Identifier modelLocation = Identifier.fromNamespaceAndPath(definitionLocation.getNamespace(),
                    folder + definition.modelFile());
                BlenderModelAsset asset = definition.modelFile().endsWith(".blend")
                    ? BlendModelLoader.load(resourceManager, modelLocation, modelId, definition)
                    : GltfModelLoader.load(resourceManager, modelLocation, modelId, definition);
                loaded.put(modelId, asset);
            } catch (Exception exception) {
                EcaLogger.error("Failed to load Blender model definition " + definitionLocation, exception);
            }
        }
        return Map.copyOf(loaded);
    }

    @Override
    protected void apply(Map<Identifier, BlenderModelAsset> prepared, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        var textureManager = Minecraft.getInstance().getTextureManager();
        for (BlenderModelAsset asset : models.values()) closePrograms(asset);
        dynamicTextures.forEach(textureManager::release);
        dynamicTextures.clear();

        Map<Identifier, BlenderModelAsset> accepted = new HashMap<>();
        for (Map.Entry<Identifier, BlenderModelAsset> entry : prepared.entrySet()) {
            boolean valid = true;
            for (BlenderModelAsset.TextureData texture : entry.getValue().textures) {
                try (ByteArrayInputStream input = new ByteArrayInputStream(texture.bytes())) {
                    NativeImage image = NativeImage.read(input);
                    Identifier textureId = texture.location();
                    textureManager.register(textureId, new DynamicTexture(() -> "eca_blender_" + textureId, image));
                    dynamicTextures.add(textureId);
                } catch (Exception exception) {
                    EcaLogger.error("Failed to create texture for Blender model " + entry.getKey(), exception);
                    valid = false;
                    break;
                }
            }
            if (valid) {
                try {
                    for (BlenderModelAsset.Material material : entry.getValue().materials) {
                        if (material.program() != null) material.program().load();
                    }
                    accepted.put(entry.getKey(), entry.getValue());
                } catch (Exception exception) {
                    EcaLogger.error("Failed to compile blend material for " + entry.getKey(), exception);
                    valid = false;
                }
            }
            if (!valid) {
                closePrograms(entry.getValue());
                for (BlenderModelAsset.TextureData texture : entry.getValue().textures) {
                    if (dynamicTextures.remove(texture.location())) textureManager.release(texture.location());
                }
            }
        }
        models = Map.copyOf(accepted);
        EcaLogger.info("Loaded {} Blender model resource(s)", models.size());
    }

    private static void closePrograms(BlenderModelAsset asset) {
        for (BlenderModelAsset.Material material : asset.materials) {
            if (material.program() != null) material.program().close();
        }
    }
}
