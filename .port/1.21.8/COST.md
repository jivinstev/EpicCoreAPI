# Cost of the 1.21.8 target

The first target run through tools/fork-port.py from the start (04:33Z to 05:50Z). The unattended stages:
setup, staging, 8 generated class moves, measure (185 errors) and the Haiku-first loop (185 -> 0 for $1.30 of
workers: Haiku 59 requests $0.19, Sonnet 11 requests $1.11). Everything after that was residue the tool handed
back, worked by Sonnet workers in fresh contexts while the main session only routed and committed:
- 8 shared helpers the loop had overlaid in the fork -> the shared 1.21.8 library (1.21.6+ uniform blocks:
  the shader compat rewrites a mod's plain uniforms into std140 blocks);
- 6 mixin targets (renderLevel, ValueOutput/ValueInput, ServerCommonPacketListenerImpl.send) and the sound
  mixin's PlayResult;
- the gate harness on 1.21.8 (2D GUI pose, Screenshot.grab, GameTest registrar without bus());
- @OnlyIn stripped from the whole prepared tree (layer step strip_onlyin);
- RenderLevelStageEvent listeners on concrete stages; the bidirectional payload's client handler;
- and the tooling gaps each exposed (shared-helper overlays, stub signals ignored, callback-kind audit,
  event-listener audit wiring, gate-summary noise, harness table inheritance).
Every gate green: author's build, A, data, payloads, B 6/6, Gate C launch/spawn/battle/gauntlet; mixin audit
and 55 event listeners clean.

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $25.64** (estimated from tokens at list prices as of 2026-09-25, 2026-10-08) over 1 session(s): 1.3 h active, 1.3 h wall (2026-10-12T04:33Z to 2026-10-12T05:50Z)
- model: claude-haiku-5-5 (88 req), claude-opus-5-5 (61 req), claude-sonnet-5-5 (167 req); effort: medium 316
- tokens: output 205,049 (thinking 118,298), input 654, cache read 98,176,034, cache write 2,112,055 (5m 1,447,941 / 1h 664,114)
- size: 43 Java files, 6 mixin classes; target Minecraft 1.21.8

By where the work happened (label: requests, est. USD):
  port: 209 requests, $19.75
  tools: 46 requests, $5.32
  other: 61 requests, $0.57

fork-port.py's own block, for its last run only:

## fork-port.py run since 2026-10-12T04:32:58+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $24.99** (estimated from tokens at list prices as of 2026-09-25, 2026-10-08) over 1 session(s): 1.19 h active, 1.19 h wall (2026-10-12T04:33Z to 2026-10-12T05:44Z)
- model: claude-haiku-5-5 (88 req), claude-opus-5-5 (57 req), claude-sonnet-5-5 (167 req); effort: medium 312
- tokens: output 202,746 (thinking 117,819), input 644, cache read 95,317,031, cache write 2,108,671 (5m 1,447,941 / 1h 660,730)
- size: 43 Java files, 6 mixin classes; target Minecraft 1.21.8

## fork-port.py run since 2026-10-12T05:59:11+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.32** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.0 h active, 0.0 h wall (2026-10-12T05:59Z to 2026-10-12T05:59Z)
- model: claude-opus-5-5 (2 req); effort: medium 2
- tokens: output 610 (thinking 0), input 4, cache read 1,453,706, cache write 2,353 (5m 0 / 1h 2,353)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8

## fork-port.py run since 2026-10-15T01:07:44+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $1.93** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.0 h active, 0.0 h wall (2026-10-15T01:08Z to 2026-10-15T01:08Z)
- model: claude-opus-5-5 (1 req); effort: medium 1
- tokens: output 16 (thinking 0), input 2, cache read 0, cache write 384,974 (5m 384,974 / 1h 0)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T16:09:39+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.97** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.05 h active, 0.05 h wall (2026-10-15T16:09Z to 2026-10-15T16:12Z)
- model: claude-opus-5-5 (21 req); effort: medium 21
- tokens: output 777 (thinking 64), input 62, cache read 2,366,986, cache write 92,350 (5m 84,705 / 1h 7,645)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T18:49:28+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.77** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.03 h active, 0.03 h wall (2026-10-15T18:49Z to 2026-10-15T18:51Z)
- model: claude-opus-5-5 (15 req); effort: medium 15
- tokens: output 500 (thinking 0), input 44, cache read 3,474,320, cache write 11,380 (5m 7,449 / 1h 3,931)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '333': 1, '111': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-16T12:34:15+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $4.88** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 1.06 h active, 2.03 h wall (2026-10-16T10:44Z to 2026-10-16T12:46Z)
- model: claude-opus-5-5 (73 req); effort: medium 73
- tokens: output 4,179 (thinking 695), input 158, cache read 16,469,972, cache write 285,577 (5m 262,103 / 1h 23,474)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 73 (≈ $6.27), other targets' 701, outside this checkout 3942. Records of concurrent targets add up.
- window: requests from 2026-10-15T18:52:00.000Z to 2026-10-16T12:46:18.298Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T12:34:15+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $3.40** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.49 h active, 0.54 h wall (2026-10-16T12:48Z to 2026-10-16T13:20Z)
- model: claude-opus-5-5 (38 req); effort: medium 38
- tokens: output 3,757 (thinking 478), input 88, cache read 9,902,255, cache write 256,969 (5m 236,603 / 1h 20,366)
- size: 42 Java files, 6 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 61 (≈ $4.02), other targets' 54, outside this checkout 192. Records of concurrent targets add up.
- window: requests from 2026-10-16T12:46:18.299Z to 2026-10-16T13:20:38.905Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T12:34:15+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $4.79** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.49 h active, 0.49 h wall (2026-10-16T13:33Z to 2026-10-16T14:02Z)
- model: claude-opus-5-5 (91 req); effort: medium 91
- tokens: output 2,521 (thinking 142), input 184, cache read 17,190,874, cache write 253,654 (5m 243,448 / 1h 10,206)
- size: 45 Java files, 7 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 8 (≈ $1.03), other targets' 31, outside this checkout 95. Records of concurrent targets add up.
- window: requests from 2026-10-16T13:20:38.906Z to 2026-10-16T14:02:40.051Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T12:34:15+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $11.97** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.42 h active, 0.42 h wall (2026-10-16T14:06Z to 2026-10-16T14:31Z)
- model: claude-opus-5-5 (73 req); effort: medium 73
- tokens: output 5,838 (thinking 348), input 152, cache read 25,980,218, cache write 1,321,396 (5m 1,304,318 / 1h 17,078)
- size: 45 Java files, 7 mixin classes; target Minecraft 1.21.8
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 1 (≈ $0.04), other targets' 2, outside this checkout 87. Records of concurrent targets add up.
- window: requests from 2026-10-16T14:02:40.052Z to 2026-10-16T14:31:34.534Z (requests before it are not in this record)

## fork-port.py run since 2026-10-21T16:40:41+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.96** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.09 h active, 0.09 h wall (2026-10-21T18:58Z to 2026-10-21T19:03Z)
- price tier: top (29 req); effort: medium 29
- tokens: output 308 (thinking 0), input 58, cache read 1,870,400, cache write 116,461 (5m 116,461 / 1h 0)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 24 (≈ $0.53), other targets' 0, outside this checkout 1277. Records of concurrent targets add up.
- window: requests from 2026-10-16T14:31:34.535Z to 2026-10-21T19:03:52.928Z (requests before it are not in this record)

## fork-port.py run since 2026-10-21T16:40:41+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.66** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.09 h active, 0.09 h wall (2026-10-21T20:46Z to 2026-10-21T20:51Z)
- price tier: top (8 req); effort: medium 8
- tokens: output 140 (thinking 0), input 18, cache read 416,198, cache write 115,025 (5m 115,025 / 1h 0)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 21 (≈ $0.83), other targets' 0, outside this checkout 771. Records of concurrent targets add up.
- window: requests from 2026-10-21T19:03:52.929Z to 2026-10-21T20:51:28.753Z (requests before it are not in this record)
## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $1.72** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.53 h active, 2.07 h wall (2026-10-21T18:58Z to 2026-10-21T21:02Z)
- price tier: top (40 req); effort: medium 40
- tokens: output 475 (thinking 0), input 82, cache read 2,371,923, cache write 246,667 (5m 246,667 / 1h 0)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 33 (≈ $1.01), other targets' 0, outside this checkout 1384. Records of concurrent targets add up.
- window: requests from 2026-10-21T15:26:54Z to 2026-10-21T21:02:50.948Z (requests before it are not in this record)

## fork-port.py run since 2026-10-26T22:19:11+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.89** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.13 h active, 0.13 h wall (2026-10-26T22:28Z to 2026-10-26T22:36Z)
- price tier: top (32 req); effort: medium 32
- tokens: output 13,996 (thinking 5,347), input 64, cache read 1,688,151, cache write 54,489 (5m 54,489 / 1h 0)
- size: 487 Java files, 50 mixin classes; target Minecraft 1.21.8
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 3 (≈ $0.06), other targets' 4, outside this checkout 549. Records of concurrent targets add up.
- window: requests from 2026-10-21T21:02:50.949Z to 2026-10-26T22:36:12.910Z (requests before it are not in this record)

## fork-port.py run since 2026-10-27T22:17:09+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.36** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.05 h active, 0.05 h wall (2026-10-27T22:27Z to 2026-10-27T22:30Z)
- price tier: top (16 req); effort: medium 16
- tokens: output 5,859 (thinking 1,083), input 32, cache read 748,177, cache write 19,219 (5m 19,219 / 1h 0)
- size: 487 Java files, 50 mixin classes; target Minecraft 1.21.8
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 0 (unpriced), other targets' 2, outside this checkout 329. Records of concurrent targets add up.
- window: requests from 2026-10-26T22:36:12.911Z to 2026-10-27T22:30:47.119Z (requests before it are not in this record)
