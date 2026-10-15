# Residue: loop

Stopped at **loop**: 0 errors, but 5 unreviewed stub signal(s) from earlier runs (STUBS.json).

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
stub: .multiversion/targets/1.21.10/cf/java/net/eca/coremod/EcaTransformationService.java: deletes overriding method(s) initialize, onLoad: whatever they did is gone -- port each to the new API, or say why vanilla no longer needs it
stub: .multiversion/targets/1.21.10/java/net/eca/coremod/EcaCoreTransformer.java: deletes overriding method(s) castVote, getTargetType, labels, targets: whatever they did is gone -- port each to the new API, or say why vanilla no longer needs it
stub: .multiversion/targets/1.21.10/java/net/eca/coremod/EcaTransformationService.java: deletes overriding method(s) initialize, onLoad: whatever they did is gone -- port each to the new API, or say why vanilla no longer needs it
stub: .multiversion/targets/1.21.10/java/net/eca/mixin/LevelRendererMixin.java: removes 2 mixin injection handler(s): check each still applies somewhere, with the same effect
stub: .multiversion/targets/1.21.10/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java: empties 3 method bodies
```

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.10 --from-stage loop --budget 3.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
