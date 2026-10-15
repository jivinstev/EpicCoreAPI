# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate C -- real client, `launch`: FAIL -- Caused by: org.spongepowered.asm.mixin.injection..

## Failing gates

- Gate C -- real client, `launch`: FAIL -- Caused by: org.spongepowered.asm.mixin.injection.throwables.InjectionError: Critical injection failure: Variable modifier method eca$modifyBufferColor(Lorg/jom

## To resume

`python3 tools/fork-port.py --repo . --target 26.1.2 --from-stage gates --budget 3.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
