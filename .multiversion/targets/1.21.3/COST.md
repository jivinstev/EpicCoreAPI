# Cost: Epic Core API, 1.21.1 -> 1.21.3 (first fork-layer target)

Window: from the start of issue #49's work to the end of the 1.21.3 port and its harvest into the migrator, one
session, the session's own model (no file-loop workers). It covers more than this target: measuring every
1.21.x line, building the layer tooling (make-fork-layer.py, layer.gradle, ci-gates --mc, harness tables),
the 1.21.3 port (282 -> 0 errors, 16 mixin targets), its gates, the shared library, catalogue and skill.
The next target's record is the first that measures a port alone.

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $31.16** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 1.34 h active, 1.34 h wall (2026-10-12T00:56Z to 2026-10-12T02:16Z)
- model: claude-opus-5-5 (222 req); effort: medium 222
- tokens: output 170,775 (thinking 54,133), input 448, cache read 112,895,612, cache write 645,800 (5m 0 / 1h 645,800)
- size: 0 Java files, 0 mixin classes; target Minecraft ?

## fork-port.py run since 2026-10-15T12:45:43+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $3.15** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.16 h active, 0.16 h wall (2026-10-15T12:45Z to 2026-10-15T12:55Z)
- model: claude-opus-5-5 (67 req); effort: medium 67
- tokens: output 828 (thinking 38), input 140, cache read 9,545,490, cache write 243,801 (5m 242,780 / 1h 1,021)
- size: 10 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '333': 1, '111': 1}} -- the cost includes that work
