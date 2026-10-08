package net.eca.compat;

import net.eca.client.render.GeoEntityExtensionLayer;
import net.eca.client.render.GeoBlockExtensionLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class GeckoLibCompat {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoReplacedCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoBlockCompileRenderLayers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoCompileRenderLayers(GeoRenderEvent.Entity.CompileRenderLayers event) {
        GeoEntityRenderer geoRenderer = event.getRenderer();
        geoRenderer.addRenderLayer(new GeoEntityExtensionLayer<>(geoRenderer, animatable -> (Entity) animatable));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoReplacedCompileRenderLayers(GeoRenderEvent.ReplacedEntity.CompileRenderLayers event) {
        GeoReplacedEntityRenderer geoRenderer = event.getRenderer();
        geoRenderer.addRenderLayer(new GeoEntityExtensionLayer<>(geoRenderer, animatable -> geoRenderer.getCurrentEntity()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoBlockCompileRenderLayers(GeoRenderEvent.Block.CompileRenderLayers event) {
        GeoBlockRenderer geoRenderer = event.getRenderer();
        geoRenderer.addRenderLayer(new GeoBlockExtensionLayer<>(geoRenderer));
    }
}
