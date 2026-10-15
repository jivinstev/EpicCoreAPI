# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate A -- mixin-config integrity: FAIL -- Gate A failed (0 failure(s)); see /home/user/eca.

## Failing gates

- Gate A -- mixin-config integrity: FAIL -- Gate A failed (0 failure(s)); see /home/user/eca-layer/build/mc26.2/fork-port/gates/gateA.log -- first compile error(s): eca/test/ClientBootSmokeTest.java:128

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.2 --from-stage gates --budget 5.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
