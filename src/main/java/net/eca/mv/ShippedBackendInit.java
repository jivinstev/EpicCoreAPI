// OVERLAY-ONLY: community fork multi-version layer, 1.21.10 (FML 10).
// On ModLauncher (1.21.1 - 1.21.8) the shipped jar's ITransformationService is found in mods/ and starts the transformation
// backend before any game class loads. FML 10 has no ModLauncher, and it only discovers a ClassProcessorProvider on the
// launch class loader (EarlyServiceDiscovery lists the services a top-level mods/ jar must declare to be put there; a mod jar
// that did would be masked out of the transforming class loader, losing its mixins), so in a shipped jar the provider in
// META-INF/services is never seen. The AGENT backend does not need it: it self-attaches and retransforms, so it is enough
// that the backend is selected before the first transformed class loads. This subscriber selects it, from mod construction,
// through the same static initialiser the provider uses in dev (idempotent: a class initialises once).
// The COREMOD backend (the _cf jar, which has no agent) gets its processor from a separate FMLModType: LIBRARY jar nested
// in the _cf jar (jar-in-jar, build.gradle cfCoremodJar): FML puts every LIBRARY jar on its plugin loader before it builds
// the class-processor set, so that copy of the provider is found. When it was not, that is logged, not hidden.
package net.eca.mv;

import net.eca.coremod.EcaTransformationService;
import net.eca.coremod.EarlyLogWriter;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

@EventBusSubscriber(modid = "eca")
public final class ShippedBackendInit {

    private ShippedBackendInit() {
    }

    @SubscribeEvent
    public static void onConstruct(FMLConstructModEvent event) {
        EcaTransformationService.TransformationBackend backend = EcaTransformationService.transformationBackend();
        EarlyLogWriter.info("[CoreMod] Mod construction selected backend " + backend);
        if (backend == EcaTransformationService.TransformationBackend.COREMOD
                && !"true".equals(System.getProperty("net.eca.coremod.processorRegistered"))) {
            EarlyLogWriter.info("[CoreMod] WARNING: COREMOD backend selected but no ClassProcessorProvider ran: the nested "
                    + "FMLModType: LIBRARY coremod jar (META-INF/jarjar) was not loaded, so nothing is transformed");
        }
    }
}
