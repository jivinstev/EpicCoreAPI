# Residue: audit

Stopped at **audit**: 7 mixin target(s) do not match vanilla.

## Mixin audit mismatches

```
checked 0 mixin targets in build/mc1.21.5/mv/java against neoforge-21.5.98-client-extra-aka-minecraft-resources.jar


NO TARGETS CHECKED -- this is NOT a pass.
  Nothing under build/mc1.21.5/mv/java matched a mixin package with a vanilla @Mixin target.
  If the mod really ships no mixins, skip this gate deliberately;
  otherwise the scope is wrong (package name, --sources root, or wrong target).
```

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.5 --from-stage audit --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
