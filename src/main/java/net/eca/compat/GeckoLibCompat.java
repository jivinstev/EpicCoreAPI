// 26.1.2 (GeckoLib 5.5, com.geckolib): the 1.21.10 overlay with the package move; the GeoRenderEvent.*.CompileRenderLayers types are still interfaces.
// 1.21.10 (GeckoLib 5.3): GeoRenderEvent.* became interfaces; the postable events are the concrete Compile*RenderLayersEvent classes
// (NeoForge bus NPEs on an interface listener type). Based on the 1.21.5 version of the authors' file (GeckoLib 5: layers are added through the event's addLayer;
// the entity comes from the animatable (GeoEntity) or the related object (replaced entity)).
package net.eca.compat;

import net.eca.client.render.GeoEntityExtensionLayer;
import net.eca.client.render.GeoBlockExtensionLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.Entity;
import com.geckolib.event.block.CompileBlockRenderLayersEvent;
import com.geckolib.event.entity.CompileEntityRenderLayersEvent;
import com.geckolib.event.replacedentity.CompileReplacedEntityRenderLayersEvent;
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
    private static void onGeoCompileRenderLayers(CompileEntityRenderLayersEvent event) {
        event.addLayer(new GeoEntityExtensionLayer(event.getRenderer(), (animatable, related) -> (Entity) animatable));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoReplacedCompileRenderLayers(CompileReplacedEntityRenderLayersEvent event) {
        event.addLayer(new GeoEntityExtensionLayer(event.getRenderer(), (animatable, related) -> (Entity) related));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void onGeoBlockCompileRenderLayers(CompileBlockRenderLayersEvent event) {
        event.addLayer(new GeoBlockExtensionLayer(event.getRenderer()));
    }
}
