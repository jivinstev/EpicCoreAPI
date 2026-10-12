Gates for `eca` at `e000ab4` for Minecraft 1.21.8 (full environment) -- run by CI with no model and no fixes applied:

| check | result | detail |
|---|---|---|
| author's own build (`build agentJar cfJar devJar jvmtiBackendJar mrJar proAgentJar proJar`) | PASS | epic-core-api-1.1.9_cf.jar, epic-core-api-1.1.9_dev.jar, epic-core-api-1.1.9_mr.jar, epic-core-api-1.1.9_pro.jar |
| Gate A -- mixin-config integrity | PASS | 2 test(s) |
| Data -- nothing NeoForge would silently ignore | PASS | 1.21-clean |
| Payloads -- every StreamCodec.unit payload has equal instances | PASS | audit-unit-codecs: checked 425 file(s), 0 finding(s) |
| Data loads -- the server rejected none of the mod's data files | PASS | no rejection logged |
| Gate B -- headless server loads the mod, GameTests | PASS | 6 required test(s) passed |
| Gate C -- real client, `launch` | PASS | PASS — reached title screen, client loaded without crashing |
| Gate C -- real client, `spawn` | PASS | PASS — created world, spawned eca creatures, world ticked 200 ticks with no crash; 0 entity types spawned |
| Gate C -- real client, `battle` | PASS | PASS — battle royale ran 600 ticks with no crash (survivors: team A 0, team B 0) |
| Gate C -- real client, `gauntlet` | PASS | PASS — gauntlet ran 10 item/block/use/effect steps with no crash |

Not covered by any automated gate: gameplay by a person.
