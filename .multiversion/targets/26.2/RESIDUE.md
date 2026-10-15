# Residue: gates

Stopped at **gates**: 1 gate(s) red: Shipped jar -- the built jar boots in a real server, the mod's own logs show its subsystem.

## Failing gates

- Shipped jar -- the built jar boots in a real server, the mod's own logs show its subsystems ran: FAIL -- epic-core-api-1.1.9_mr-mc26.2.jar: FAIL boot -- the server exited (-6) before 'Done (': [15Oct2

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.2 --from-stage gates --budget 1.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
