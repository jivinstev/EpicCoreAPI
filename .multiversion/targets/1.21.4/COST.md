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
