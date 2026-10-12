# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate A -- mixin-config integrity: FAIL -- Gate A failed (0 failure(s)); see /home/user/Epi.

## Failing gates

- Gate A -- mixin-config integrity: FAIL -- Gate A failed (0 failure(s)); see /home/user/EpicCoreAPI/build/mc1.21.5/fork-port/gates/gateA.log

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.5 --from-stage gates --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
