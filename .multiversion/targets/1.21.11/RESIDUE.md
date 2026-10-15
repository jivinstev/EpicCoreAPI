# Residue: loop

Stopped at **loop**: 0 errors, but 1 unreviewed stub signal(s) from earlier runs (STUBS.json).

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
stub: .multiversion/targets/1.21.11/java/net/eca/mixin/LevelRendererMixin.java: removes 2 mixin injection handler(s): check each still applies somewhere, with the same effect
```

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.11 --from-stage loop --budget 3.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
