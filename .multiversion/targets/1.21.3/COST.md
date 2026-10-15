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

## fork-port.py run since 2026-10-15T18:02:54+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.39** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.11 h active, 0.11 h wall (2026-10-15T18:02Z to 2026-10-15T18:09Z)
- model: claude-opus-5-5 (8 req); effort: medium 8
- tokens: output 575 (thinking 0), input 18, cache read 1,572,763, cache write 9,095 (5m 4,570 / 1h 4,525)
- size: 10 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '333': 1, '111': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T18:12:28+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.27** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.02 h active, 0.02 h wall (2026-10-15T18:12Z to 2026-10-15T18:13Z)
- model: claude-opus-5-5 (4 req); effort: medium 4
- tokens: output 278 (thinking 0), input 8, cache read 1,243,078, cache write 2,591 (5m 467 / 1h 2,124)
- size: 11 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '333': 1, '111': 1}} -- the cost includes that work
