# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate C -- real client, `gauntlet`: FAIL -- Caused by: net.neoforged.fml.ModLoadingExceptio.

## Failing gates

- Gate C -- real client, `gauntlet`: FAIL -- Caused by: net.neoforged.fml.ModLoadingException: Loading errors encountered:

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.10 --from-stage gates --budget 4.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
