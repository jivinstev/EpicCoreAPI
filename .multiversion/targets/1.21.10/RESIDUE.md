# Residue: 1.21.10 -- compiles, audits and Gate A/B pass, but NOT done

**The coremod is inert.** FML 10 (NeoForge 21.10) has no ModLauncher. The unattended loop turned
EcaCoreTransformer into a ClassProcessor and removed EcaTransformationService's ITransformationService role,
but nothing registers a ClassProcessorProvider and the old META-INF/services file is inert. The mod builds,
loads and passes Gate A/B while its bytecode transformers never run. Must be wired and proven at runtime before
this target is called working.

Also unverified (no Gate C yet): GeckoLib 5.3 submit-based Geo layers (bone hiding as custom-geometry tasks,
shader-mask passes queued and flushed after the level -- no longer depth-sorted among translucent geometry),
the 12 mixins moved to submit/SubmitNodeCollector, the FML 10 early-window provider.
