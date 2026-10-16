Gates for `eca` at `9b5c6c3` for Minecraft 1.21.3 (full environment) -- run by CI with no model and no fixes applied:

| check | result | detail |
|---|---|---|
| author's own build (`build agentJar cfJar devJar jvmtiBackendJar mrJar proAgentJar proJar`) | PASS | epic-core-api-1.1.9_cf-mc1.21.3.jar, epic-core-api-1.1.9_dev-mc1.21.3.jar, epic-core-api-1.1.9_mr-mc1.21.3.jar, epic-core-api-1.1.9_pro-mc1.21.3.jar |
| Gate A -- mixin-config integrity | PASS | 2 test(s) |
| Data -- nothing NeoForge would silently ignore | PASS | 1.21-clean |
| Payloads -- every StreamCodec.unit payload has equal instances | PASS | audit-unit-codecs: checked 427 file(s), 0 finding(s) |
| Data loads -- the server rejected none of the mod's data files | PASS | no rejection logged |
| Gate B -- headless server loads the mod, GameTests | PASS | 5 required test(s) passed |
| Gate C -- real client, `launch` | PASS | PASS — reached title screen, client loaded without crashing |
| Gate C -- real client, `spawn` | PASS | PASS — created world, spawned eca creatures, world ticked 200 ticks with no crash; 0 entity types spawned |
| Gate C -- real client, `battle` | PASS | PASS — battle royale ran 600 ticks with no crash (survivors: team A 0, team B 0) |
| Gate C -- real client, `gauntlet` | PASS | PASS — gauntlet ran 10 item/block/use/effect steps with no crash |
| Shipped jar -- the built jar boots in a real server, the mod's own logs show its subsystems ran | PASS | epic-core-api-1.1.9_mr-mc1.21.3.jar: 3 check(s) green |

Not covered by any automated gate: gameplay by a person.
