// Fork multi-version layer: 1.21.5 version of the authors' file (GeckoLib 5: layers are added through the event's addLayer;
// the entity comes from the animatable (GeoEntity) or the related object (replaced entity)).
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

public class GeckoLibCompat {

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoReplacedCompileRenderLayers);
        NeoForge.EVENT_BUS.addListener(GeckoLibCompat::onGeoBlockCompileRenderLayers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoCompileRenderLayers(GeoRenderEvent.Entity.CompileRenderLayers event) {
        event.addLayer(new GeoEntityExtensionLayer(event.getRenderer(), (animatable, related) -> (Entity) animatable));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoReplacedCompileRenderLayers(GeoRenderEvent.ReplacedEntity.CompileRenderLayers event) {
        event.addLayer(new GeoEntityExtensionLayer(event.getRenderer(), (animatable, related) -> (Entity) related));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoBlockCompileRenderLayers(GeoRenderEvent.Block.CompileRenderLayers event) {
        event.addLayer(new GeoBlockExtensionLayer(event.getRenderer()));
    }
}
