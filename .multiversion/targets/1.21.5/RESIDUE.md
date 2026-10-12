# Residue: audit

Stopped at **audit**: 2 mixin target(s) do not match vanilla.

## Mixin audit mismatches

```
      mixin:   (int, CloudStatus, float, Matrix4f, Matrix4f, Vec3, float)
      vanilla: void (int, CloudStatus, float, Vec3, float)
      mixin:   no such method in vanilla
2 MISMATCH(ES)
```

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.5 --from-stage audit --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
