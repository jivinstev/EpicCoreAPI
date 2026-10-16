// Fork multi-version layer, 1.21.10 only (new file, no counterpart in the authors' tree).
// FML 10 (NeoForge 21.9+) has no ModLauncher, so the authors' META-INF/services/cpw.mods.modlauncher.api.ITransformationService
// registration is inert. This is the FML 10 way to get EcaCoreTransformer (a ClassProcessor there) installed: FML loads every
// net.neoforged.neoforgespi.transformation.ClassProcessorProvider service (META-INF/services, see the resources overlay) while it
// builds the class-processor set, before any game class loads, and the provider hands over the processors.
package net.eca.coremod;

import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;

public final class EcaClassProcessorProvider implements ClassProcessorProvider {

    @Override
    public void createProcessors(Context context, Collector collector) {
        // First touch of EcaTransformationService runs its static initialiser (fresh EcaBootstrap.log, backend selection),
        // which is what the ITransformationService constructor path did on ModLauncher.
        EcaTransformationService.TransformationBackend backend = EcaTransformationService.transformationBackend();
        EarlyLogWriter.info("[CoreMod] ClassProcessorProvider registered EcaCoreTransformer; backend=" + backend);
        System.setProperty("net.eca.coremod.processorRegistered", "true");
        collector.add(new EcaCoreTransformer());
    }
}
