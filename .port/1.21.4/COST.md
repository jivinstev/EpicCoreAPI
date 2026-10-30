# Cost of the 1.21.4 target

Window: from the start of the 1.21.4 work (02:35Z) through its gates. 1.21.3's shared library already
covered most of the jump: the target began at 62 errors.

What the window covered:
- the port itself (62 -> 0 errors across main, cf, pro and jvmti; 11 overlay files, 4 of them mixins);
- the shared 1.21.4 library in the migrator (rows + 8 helper classes for every fork);
- one harness row, two tool fixes (the mixin audit's nested-target false positive, port-cost sizing);
- the full gates once (author's build, A, data, payloads, B, Gate C launch/spawn/battle/gauntlet), all green.

Models: Opus for the main work, Sonnet subagents for the GUI and texture overlays.

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $14.60** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.47 h active, 0.47 h wall (2026-10-12T02:35Z to 2026-10-12T03:03Z)
- model: claude-opus-5-5 (81 req), claude-sonnet-5-5 (13 req); effort: medium 94
- tokens: output 58,885 (thinking 15,470), input 188, cache read 44,293,218, cache write 1,001,772 (5m 624,049 / 1h 377,723)
- size: 11 Java files, 4 mixin classes; target Minecraft 1.21.4

## fork-port.py run since 2026-10-15T15:42:05+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $1.70** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.03 h active, 0.03 h wall (2026-10-15T15:42Z to 2026-10-15T15:44Z)
- model: claude-opus-5-5 (17 req); effort: medium 17
- tokens: output 4,088 (thinking 987), input 34, cache read 6,066,899, cache write 75,657 (5m 66,578 / 1h 9,079)
- size: 11 Java files, 4 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '111': 1, '333': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-15T18:23:37+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.06** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.0 h active, 0.0 h wall (2026-10-15T18:23Z to 2026-10-15T18:23Z)
- model: claude-opus-5-5 (2 req); effort: medium 2
- tokens: output 58 (thinking 0), input 4, cache read 185,900, cache write 3,586 (5m 3,586 / 1h 0)
- size: 12 Java files, 4 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'309927': 1, '333': 1, '111': 1}} -- the cost includes that work

## fork-port.py run since 2026-10-16T07:34:27+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.95** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.22 h active, 0.22 h wall (2026-10-16T07:32Z to 2026-10-16T07:45Z)
- model: claude-opus-5-5 (20 req); effort: medium 20
- tokens: output 631 (thinking 0), input 40, cache read 2,141,395, cache write 100,005 (5m 96,928 / 1h 3,077)
- size: 12 Java files, 4 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 1 (≈ $0.19), other targets' 142, outside this checkout 3234. Records of concurrent targets add up.
- window: requests from 2026-10-15T18:24:00.000Z to 2026-10-16T07:45:55.412Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T08:26:19+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $4.28** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.76 h active, 0.83 h wall (2026-10-16T07:47Z to 2026-10-16T08:36Z)
- model: claude-opus-5-5 (100 req); effort: medium 100
- tokens: output 6,831 (thinking 1,317), input 208, cache read 13,130,633, cache write 296,891 (5m 286,143 / 1h 10,748)
- size: 13 Java files, 5 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '111': 1, '333': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 11 (≈ $0.33), other targets' 70, outside this checkout 102. Records of concurrent targets add up.
- window: requests from 2026-10-16T07:45:55.413Z to 2026-10-16T08:36:55.714Z (requests before it are not in this record)

## fork-port.py run since 2026-10-16T08:26:19+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $2.74** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.9 h active, 1.62 h wall (2026-10-16T08:39Z to 2026-10-16T10:16Z)
- model: claude-opus-5-5 (40 req); effort: medium 40
- tokens: output 5,440 (thinking 1,281), input 88, cache read 6,248,296, cache write 261,723 (5m 237,610 / 1h 24,113)
- size: 13 Java files, 5 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '111': 1, '333': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 51 (≈ $5.04), other targets' 51, outside this checkout 197. Records of concurrent targets add up.
- window: requests from 2026-10-16T08:36:55.715Z to 2026-10-16T10:16:26.128Z (requests before it are not in this record)

## fork-port.py run since 2026-10-21T16:14:53+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.84** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.07 h active, 0.07 h wall (2026-10-21T18:47Z to 2026-10-21T18:51Z)
- price tier: top (25 req); effort: medium 25
- tokens: output 790 (thinking 137), input 50, cache read 1,371,428, cache write 109,418 (5m 107,433 / 1h 1,985)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 27 (≈ $0.64), other targets' 0, outside this checkout 616. Records of concurrent targets add up.
- window: requests from 2026-10-16T10:16:26.129Z to 2026-10-21T18:51:59.329Z (requests before it are not in this record)

## fork-port.py run since 2026-10-21T16:14:53+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.07** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.07 h active, 0.07 h wall (2026-10-21T19:12Z to 2026-10-21T19:16Z)
- price tier: top (5 req); effort: medium 5
- tokens: output 62 (thinking 0), input 10, cache read 187,709, cache write 6,038 (5m 6,038 / 1h 0)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 4 (≈ $0.18), other targets' 0, outside this checkout 342. Records of concurrent targets add up.
- window: requests from 2026-10-21T18:51:59.330Z to 2026-10-21T19:16:25.010Z (requests before it are not in this record)
## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $1.04** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.48 h active, 0.8 h wall (2026-10-21T18:47Z to 2026-10-21T19:35Z)
- price tier: top (32 req); effort: medium 32
- tokens: output 871 (thinking 137), input 66, cache read 1,620,321, cache write 138,720 (5m 136,735 / 1h 1,985)
- size: 0 Java files, 0 mixin classes; target Minecraft ?
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 29 (≈ $0.72), other targets' 0, outside this checkout 852. Records of concurrent targets add up.
- window: requests from 2026-10-21T15:26:54Z to 2026-10-21T19:35:19.037Z (requests before it are not in this record)

## fork-port.py run since 2026-10-26T19:58:45+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.44** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.19 h active, 0.19 h wall (2026-10-26T20:08Z to 2026-10-26T20:19Z)
- price tier: top (19 req); effort: medium 19
- tokens: output 6,290 (thinking 1,929), input 38, cache read 854,203, cache write 27,650 (5m 27,650 / 1h 0)
- size: 477 Java files, 49 mixin classes; target Minecraft 1.21.4
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 3 (≈ $0.08), other targets' 3, outside this checkout 315. Records of concurrent targets add up.
- window: requests from 2026-10-21T19:35:19.038Z to 2026-10-26T20:19:36.634Z (requests before it are not in this record)

## fork-port.py run since 2026-10-27T20:15:28+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.32** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.04 h active, 0.04 h wall (2026-10-27T20:24Z to 2026-10-27T20:27Z)
- price tier: top (11 req); effort: medium 11
- tokens: output 4,707 (thinking 797), input 22, cache read 553,292, cache write 23,332 (5m 23,332 / 1h 0)
- size: 477 Java files, 49 mixin classes; target Minecraft 1.21.4
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 0 (unpriced), other targets' 4, outside this checkout 205. Records of concurrent targets add up.
- window: requests from 2026-10-26T20:19:36.635Z to 2026-10-27T20:27:07.474Z (requests before it are not in this record)

## fork-port.py run since 2026-10-30T01:20:47+00:00

## Cost (recorded by tools/port-cost.py -- do not edit by hand)
- **≈ $0.42** (estimated from tokens at list prices as of 2026-09-25) over 1 session(s): 0.06 h active, 0.06 h wall (2026-10-30T01:41Z to 2026-10-30T01:44Z)
- price tier: top (14 req); effort: medium 14
- tokens: output 5,301 (thinking 1,143), input 28, cache read 1,081,685, cache write 19,751 (5m 19,751 / 1h 0)
- size: 478 Java files, 49 mixin classes; target Minecraft 1.21.4
- ⚠ these sessions also touched other mods: {'c677f97b': {'226410': 2, '309927': 1, '333': 1, '111': 1}} -- the cost includes that work
- attribution: each request is charged to one target at most, by the paths and target its own tool calls name (a request naming none keeps the label of the one before it in the same transcript; a subagent or loop worker starts from its prompt). Not in the total above: this checkout's shared/orchestration requests 0 (unpriced), other targets' 8, outside this checkout 1253. Records of concurrent targets add up.
- window: requests from 2026-10-27T20:27:07.475Z to 2026-10-30T01:44:47.904Z (requests before it are not in this record)
