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
