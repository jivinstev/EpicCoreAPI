Gates for `eca` at `89a2606` for Minecraft 1.21.11 (full environment) -- run by CI with no model and no fixes applied:

| check | result | detail |
|---|---|---|
| author's own build (`build agentJar cfCoremodJar cfJar devJar jvmtiBackendJar mrJar proAgentJar proJar`) | PASS | epic-core-api-1.1.9-fix_cf.jar, epic-core-api-1.1.9-fix_dev.jar, epic-core-api-1.1.9-fix_mr.jar, epic-core-api-1.1.9-fix_pro.jar |
| Gate A -- mixin-config integrity | PASS | 2 test(s) |
| Data -- nothing NeoForge would silently ignore | PASS | 1.21-clean |
| Descriptors -- vanilla names in strings outside the mixins (report) | REPORT | 8 to read: src/cf/java/net/eca/coremod/EcaTransformationService.java:117: "net.neoforged.fml.loading.ModDirTransformerDiscoverer": class net.neoforged.fml.loading.ModDirTransformerDiscoverer is not in this version; src/cf/java/net/eca/coremod/RuntimeExtensionBridge.java:5: "net/neoforged/fml/earlydisplay/DisplayWindow": class net.neoforged.fml.earlydisplay.DisplayWindow is not in this version; src/main/java/net/eca/coremod/EcaTransformationService.java:163: "net.neoforged.fml.loading.ModDirTransformerDiscoverer": class net.neoforged.fml.loading.ModDirTransformerDiscoverer is not in this version; src/main |
| Payloads -- every StreamCodec.unit payload has equal instances | PASS | audit-unit-codecs: checked 463 file(s), 0 finding(s) |
| Gate C -- real client, `spawn` | skipped | a library: no entity of its own to spawn; proven by a dependent's Gate C against this jar (tools/fork-branches.py library-record) |
| Gate C -- real client, `battle` | skipped | a library: no entity of its own to spawn; proven by a dependent's Gate C against this jar (tools/fork-branches.py library-record) |
| Data loads -- the server rejected none of the mod's data files | PASS | no rejection logged |
| Gate B -- headless server loads the mod, GameTests | PASS | 6 required test(s) passed |
| Gate C -- real client, `launch` | PASS | PASS — reached title screen, client loaded without crashing |
| Gate C -- real client, `gauntlet` | PASS | PASS — gauntlet ran 10 item/block/use/effect steps with no crash |
| Shipped jar -- the built jar boots in a real server, the mod's own logs show its subsystems ran | PASS | epic-core-api-1.1.9-fix_mr.jar: 3 check(s) green |
| Shipped jar -- the built jar boots in a real server, the mod's own logs show its subsystems ran (epic-core-api-1.1.9-fix_cf.jar) | PASS | epic-core-api-1.1.9-fix_cf.jar: 5 check(s) green |

Not covered by any automated gate: gameplay by a person.
