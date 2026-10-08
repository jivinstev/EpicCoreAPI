package net.eca.compat;

import net.eca.client.render.GeoEntityExtensionLayer;
import net.eca.client.render.GeoBlockExtensionLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.Entity;
import com.geckolib.event.GeoRenderEvent;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.GeoReplacedEntityRenderer;

public class GeckoLibCompat {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoReplacedCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoBlockCompileRenderLayers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoCompileRenderLayers(GeoRenderEvent.Entity.CompileRenderLayers event) {
        GeoEntityRenderer geoRenderer = event.getRenderer();
        event.addLayer(new GeoEntityExtensionLayer<>(geoRenderer, animatable -> (Entity) animatable));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoReplacedCompileRenderLayers(GeoRenderEvent.ReplacedEntity.CompileRenderLayers event) {
        GeoReplacedEntityRenderer geoRenderer = event.getRenderer();
        event.addLayer(new GeoEntityExtensionLayer<>(geoRenderer, animatable -> geoRenderer.getCurrentEntity()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoBlockCompileRenderLayers(GeoRenderEvent.Block.CompileRenderLayers event) {
        GeoBlockRenderer geoRenderer = event.getRenderer();
        event.addLayer(new GeoBlockExtensionLayer<>(geoRenderer));
    }
}
