# Residue: 1.21.10 -- compiles, audits and Gate A/B pass, but NOT done

**The coremod is wired for FML 10 in dev and proved by Gate B; production loading is NOT proved.** FML 10 has no ModLauncher, so
`EcaClassProcessorProvider` + `META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider` now register
`EcaCoreTransformer` (a `ClassProcessor`). A dev Gate B run writes `logs/EcaBootstrap.log` with the same ten `Installed protected
containers ... target=` / hook lines as 1.21.8. FML finds a provider with `ServiceLoader` on the launch class loader before the game
layer exists, so in a shipped jar (a `MOD` in `mods/`) the services file is expected NOT to be seen; the jar (or a JiJ'd
library holding the provider and processor) would need manifest `FMLModType: LIBRARY`. Read from FML's bytecode, never run.

Also unverified (no Gate C yet): GeckoLib 5.3 submit-based Geo layers (bone hiding as custom-geometry tasks,
shader-mask passes queued and flushed after the level -- no longer depth-sorted among translucent geometry),
the 12 mixins moved to submit/SubmitNodeCollector, the FML 10 early-window provider.
