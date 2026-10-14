# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate A -- mixin-config integrity: FAIL -- the gate harness has no rename table for Minecra.

## Failing gates

- Gate A -- mixin-config integrity: FAIL -- the gate harness has no rename table for Minecraft 26.1.2 (templates/upstream-harness/versions/26.1.2.renames.tsv): it is written for 1.21.1

## To resume

`python3 tools/fork-port.py --repo . --target 26.1.2 --from-stage gates --budget 5.0 --gates ab`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
