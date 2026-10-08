package net.eca.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextureSizeCache {

    private static final int DEFAULT_WIDTH = 182;
    private static final int DEFAULT_HEIGHT = 5;
    private static final Map<Identifier, Size> CACHE = new ConcurrentHashMap<>();

    public static Size get(Identifier location) {
        if (location == null) {
            return new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        return CACHE.computeIfAbsent(location, TextureSizeCache::loadSize);
    }

    private static Size loadSize(Identifier location) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        try {
            Resource resource = resourceManager.getResource(location).orElse(null);
            if (resource == null) {
                return new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            }

            try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                return new Size(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            return new Size(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }
    }

    public record Size(int width, int height) {
    }

    private TextureSizeCache() {}
}
