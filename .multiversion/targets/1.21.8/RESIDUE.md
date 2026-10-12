# Residue: gates

Stopped at **gates**: 1 gate(s) red: Gate C -- real client, `launch`: FAIL -- Caused by: java.net.UnknownHostException: api.min.

## Failing gates

- Gate C -- real client, `launch`: FAIL -- Caused by: java.net.UnknownHostException: api.minecraftservices.com

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.8 --from-stage gates --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
