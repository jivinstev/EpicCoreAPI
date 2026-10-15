# Cost of the 1.21.5 target

The whole hop, 03:30Z to 04:32Z: 888 errors at the start (62 for 1.21.4), almost all 1.21.5's switch from
immediate-mode GL to RenderPipeline/RenderPass, plus NBT getters returning Optional and the removal of the
@GameTest API. The window also built, for every fork: the shared 1.21.5 render compat library, file-loop's
--layer mode, tools/fork-port.py (and fixed the five bugs its first runs found), and the gate harness's
1.21.5 GameTest support. Opus designed and routed; Sonnet workers in fresh contexts did the burn-down
(823 -> 0), the two mixin retargets and the harness fix. Every gate green (author's build, A, data, payloads,
B 6/6, Gate C launch/spawn/battle/gauntlet).

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $37.12** (estimated from tokens at list prices as of 2026-09-25, 2026-10-08) over 1 session(s): 1.03 h active, 1.03 h wall (2026-10-12T03:30Z to 2026-10-12T04:32Z)
- model: claude-haiku-5-5 (6 req), claude-opus-5-5 (160 req), claude-sonnet-5-5 (160 req); effort: medium 326
- tokens: output 133,209 (thinking 53,864), input 686, cache read 147,013,205, cache write 1,793,078 (5m 1,514,538 / 1h 278,540)
- size: 15 Java files, 6 mixin classes; target Minecraft 1.21.5

By where the work happened (label: requests, est. USD):
  port: 260 requests, $29.20
  tools: 60 requests, $7.89
  other: 6 requests, $0.02

The block below was appended by tools/fork-port.py for its own last run only:

## fork-port.py run since 2026-10-12T04:01:30+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $13.03** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.42 h active, 0.42 h wall (2026-10-12T04:01Z to 2026-10-12T04:26Z)
- model: claude-opus-5-5 (65 req), claude-sonnet-5-5 (41 req); effort: medium 106
- tokens: output 30,458 (thinking 6,249), input 234, cache read 52,021,017, cache write 679,997 (5m 621,256 / 1h 58,741)
- size: 15 Java files, 6 mixin classes; target Minecraft 1.21.5

## fork-port.py run since 2026-10-15T01:14:08+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $1.93** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.0 h active, 0.0 h wall (2026-10-15T01:14Z to 2026-10-15T01:14Z)
- model: claude-opus-5-5 (1 req); effort: medium 1
- tokens: output 16 (thinking 0), input 2, cache read 0, cache write 385,599 (5m 385,599 / 1h 0)
- size: 15 Java files, 6 mixin classes; target Minecraft 1.21.5
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T15:57:36+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.42** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.07 h active, 0.07 h wall (2026-10-15T15:57Z to 2026-10-15T16:01Z)
- model: claude-opus-5-5 (13 req); effort: medium 13
- tokens: output 1,423 (thinking 113), input 30, cache read 1,443,004, cache write 15,360 (5m 5,676 / 1h 9,684)
- size: 15 Java files, 6 mixin classes; target Minecraft 1.21.5
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T18:36:04+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.27** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.04 h active, 0.04 h wall (2026-10-15T18:36Z to 2026-10-15T18:38Z)
- model: claude-opus-5-5 (8 req); effort: medium 8
- tokens: output 150 (thinking 0), input 16, cache read 1,162,778, cache write 6,594 (5m 6,594 / 1h 0)
- size: 16 Java files, 6 mixin classes; target Minecraft 1.21.5
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work
