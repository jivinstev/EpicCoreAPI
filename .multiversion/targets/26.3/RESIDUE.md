# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate B -- headless server loads the mod, GameTests: FAIL -- Caused by: org.spongepowered.a.

## Failing gates

- Gate B -- headless server loads the mod, GameTests: FAIL -- Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: Invalid descriptor on eca.mixins.json:InventoryMixin 

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.3 --from-stage gates --budget 5.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
