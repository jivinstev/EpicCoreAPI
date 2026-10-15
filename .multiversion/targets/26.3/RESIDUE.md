# Residue: loop

Stopped at **loop**: 0 errors, but 1 unreviewed stub signal(s) from earlier runs (STUBS.json).

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
stub: .multiversion/targets/26.3/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java: empties 1 method body; empties 4 method bodies
```

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.3 --from-stage loop --budget 3.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
