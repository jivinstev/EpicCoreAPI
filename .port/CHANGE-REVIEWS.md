# Changes reviewed once

A change to one file a fresh review passed: a version branch whose reviewed inputs changed only by these
exact changes (the same file before, the same file after) keeps its REVIEW.md (tools/fork-branches.py
commit_current). Written by the keep-current round driver; a union record.

| file | before | after | verdict | reviewed at |
|---|---|---|---|---|
| `gradle.properties` | 403e0818007e5ce8bef9c037a75e893e5cfbb964 | 9440bf1d5282f4d44db0668580e8d4092af03bc8 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/blender/client/runtime/BlendMaterialProgram.java` | af64b9cb3b7450abbd8dd26d5341a88ebd980708 | 87dd8339d90ded0ce8a21622af741bd8cabf2a38 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/BlockExtensionRenderer.java` | b0272b20b79f1d5418372f232ec74c76dd7c3b7f | e4904ff17cca9ddcb958a081669f17acd14185c0 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/EntityExtensionLayer.java` | 93822ea1b5cd142ff8543523412d3cdeea06eb03 | e19aadff35aee21d93813b474f697178d44eb177 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/GeoBlockExtensionLayer.java` | f313e2835fcd60ea7f9988ce52de0c89fe1993ec | 1ad91db3760dc715a9e945cca9ef7df710cd9c82 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/GeoEntityExtensionLayer.java` | 5b93d7f0135b3e286d7964fbacfb76e0f0337270 | 720667c08491a27a161a8379e565c074c74bc031 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/RenderBufferPool.java` | - | 3332b5f9efeb8fd21c2f6a2de5b4a128f8e0ea28 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/ShaderMaskRenderQueue.java` | 7d8b13f06cbf9a7b59c69100db84a95fcaec1ebf | ec355e8bf08e2dec85d992c3128689cf532bddab | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/SpriteBatchingVertexConsumer.java` | cff60c55ab825eab8aa51cf5a79ef8858a19526d | 01051ef3b81f5f7f48d0eb7661e68e609f9a1bb6 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | a329f3b9f2df5f6ca44d2c97883be9b53fbf01f9 | fe625cb2b10de8335050afa8f39dbbbccd91ec8f | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/config/EcaConfiguration.java` | 4f66797f5a6df5f4189b1e1a7faf436ea434130e | a2d384d8479da85c997cc11b4aaaa1c8580cbcab | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/coremod/LoadingScreenTransformer.java` | 3caf743f483f18e39d0a8758f9b3d3379759dab1 | 0c153f6a855bb2e19bf34255f238dd203cbe3222 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/coremod/TransformerWhitelist.java` | afbb9ebdd6bdae657539c61b14ec2926412488a6 | f272887db3a9fcc64e1b54880983407cc8e158f3 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/mixin/FallingBlockRendererMixin.java` | a6bd3a5b0578470b4217de19c02de729fc03be92 | 3af176e8bc8780e7c4749123440c7c11c82f46cf | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/mixin/GameRendererPostLevelMixin.java` | 943d0877bd3a16124c53f5ccd86d9222e0833664 | 938efdf728cc8c2297db6cd0e6be035bae9f2c7c | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `src/main/java/net/eca/mixin/ItemRendererMixin.java` | 2c23b707a097deb08ccacd0a272dad00dbe3b1c0 | 5152b7d062b068ec2534bb6a68121e538d97bbf1 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-29 |
| `.port/1.21.4/REMERGE.json` | - | c6a8c4f1c863c7aed676e1b76e72aca511256747 | ok | jivinstev/EpicCoreAPI 1.21.4, round 2026-10-29 |
| `gradle.properties` | b56f25187eccc81bd8b76f9dcdcf5509b8764f8d | 1798633b7c0a3f4cc19911decff29d1749ae7c6b | ok | jivinstev/EpicCoreAPI 1.21.4, round 2026-10-29 |
| `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | 27617000e9bf1be112d658200a016b643192c4f0 | c6f253ae2b8f3b960a06ecc483d3fbe630891ff3 | ok | jivinstev/EpicCoreAPI 1.21.4, round 2026-10-29 |
| `src/main/java/net/eca/mixin/GameRendererPostLevelMixin.java` | e96ef425f132bf120b72c3b67a5c187a4973bb64 | fd4f73027dd2c032c0558c0297dbfdd56c627f45 | ok | jivinstev/EpicCoreAPI 1.21.4, round 2026-10-29 |
| `src/main/java/net/eca/mixin/ItemRendererMixin.java` | e4c8052ea08f0554e13a3e295efed8c5ff221ee7 | f930f1ebacda1d4ee68939cb81cea2508010e1b3 | ok | jivinstev/EpicCoreAPI 1.21.4, round 2026-10-29 |
| `.port/branch.gradle` | fc09a2c581e0f087da919d71fafffcfd108189a8 | 565e5b0b09d7ef8d188e0e89b9345b910dcfd8f4 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-30 |
