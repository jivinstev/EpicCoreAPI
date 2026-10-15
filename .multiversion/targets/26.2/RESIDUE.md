# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate C -- real client, `launch`: FAIL -- java.lang.NoClassDefFoundError thrown in net.eca..

## Failing gates

- Gate C -- real client, `launch`: FAIL -- java.lang.NoClassDefFoundError thrown in net.eca.event.EcaClientEventHandler.onClientLoggingOut, caught and only logged (2x); java.lang.IllegalStateException t

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.2 --from-stage gates --budget 5.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
