# 1.21.10 -- open items

- Shipped jar: PROVEN with tools/shipped-gate.py (real NeoForge 21.10.64 server, the _mr jar in mods/): the agent
  backend is selected and the containers in ServerLevel replaced, as on 1.21.1. FML 10 cannot discover a class
  processor from a mods/ jar, so the shipped path starts the AGENT backend from mod construction (ShippedBackendInit);
  on a JVM where the agent cannot attach, a shipped 1.21.10 jar has no coremod fallback (1.21.1 had one) and logs a
  warning instead.
- Not yet run: Gate C (client) on 1.21.10 -- GeckoLib 5.3 submit-based layers and the 12 submit mixins are
  compile- and audit-proven only.
