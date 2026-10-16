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

## fork-port.py run since 2026-10-16T06:51:18+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.96** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.23 h active, 0.23 h wall (2026-10-16T06:50Z to 2026-10-16T07:04Z)
- model: claude-opus-5-5 (30 req); effort: medium 30
- tokens: output 932 (thinking 0), input 60, cache read 2,576,880, cache write 84,486 (5m 82,639 / 1h 1,847)
- size: 11 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 1 (≈ $0.19), other targets' 0, outside this checkout 3167. Records of concurrent targets add up.
- window: requests from 2026-10-15T18:14:00.000Z to 2026-10-16T07:04:07.150Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T07:19:25+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $5.09** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.28 h active, 0.28 h wall (2026-10-16T07:05Z to 2026-10-16T07:21Z)
- model: claude-opus-5-5 (101 req); effort: medium 101
- tokens: output 7,581 (thinking 472), input 212, cache read 15,647,960, cache write 344,335 (5m 315,315 / 1h 29,020)
- size: 11 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 0 (unpriced), other targets' 0, outside this checkout 57. Records of concurrent targets add up.
- window: requests from 2026-10-16T07:04:07.151Z to 2026-10-16T07:21:34.476Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T08:02:19+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $3.96** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.69 h active, 0.8 h wall (2026-10-16T07:30Z to 2026-10-16T08:18Z)
- model: claude-opus-5-5 (60 req); effort: medium 60
- tokens: output 1,626 (thinking 345), input 124, cache read 6,318,843, cache write 513,339 (5m 481,081 / 1h 32,258)
- size: 11 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 2 (≈ $0.07), other targets' 101, outside this checkout 125. Records of concurrent targets add up.
- window: requests from 2026-10-16T07:21:34.477Z to 2026-10-16T08:18:02.483Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T08:02:19+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $3.89** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.96 h active, 1.91 h wall (2026-10-16T08:21Z to 2026-10-16T10:15Z)
- model: claude-opus-5-5 (68 req); effort: medium 68
- tokens: output 20,081 (thinking 4,422), input 150, cache read 11,507,809, cache write 202,638 (5m 144,454 / 1h 58,184)
- size: 11 Java files, 6 mixin classes; target Minecraft 1.21.3
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '111': 1, '333': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 53 (≈ $5.14), other targets' 80, outside this checkout 185. Records of concurrent targets add up.
- window: requests from 2026-10-16T08:18:02.484Z to 2026-10-16T10:15:46.158Z (requests before it are not in this record)
