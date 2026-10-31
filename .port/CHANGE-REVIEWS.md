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
| `.port/1.21.5/REMERGE.json` | - | c6a8c4f1c863c7aed676e1b76e72aca511256747 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `gradle.properties` | 6318a461f7849a0c03750d2b144a36ca08ef0377 | b0c9d68921bdc94c454981758c08e4fbce927cac | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/blender/client/runtime/BlendMaterialProgram.java` | 44693269a2c1d328bf33373061bde0b76c00e37f | 2f13c3f1e8d8fa52fff40b2cc2121b37e114a2a8 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/BlockExtensionRenderer.java` | d4c9f896aabfd7720ba18c33507c651a83e6c1c8 | 6315bf42999ca8fe0a237615d8bb24590c8ff8d7 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/GeoBlockExtensionLayer.java` | 99d68e341c1db1f40851a97874c5f24d48e80c93 | 27e583c91e95c3278ba451d22799982d8d8c2ba6 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/GeoEntityExtensionLayer.java` | 92507008603abf881d2b442dbe89d0f52b54730f | 951a6b2df5f55466ff3c9c3ab434608a64a1f19d | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/ShaderMaskRenderQueue.java` | d03514de59ad663dc21c7b6a11ed7366fa669f52 | 69928ee9094860e76cea8f254dd0587106dd7186 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/SpriteBatchingVertexConsumer.java` | 0252c155767d8b6cb6a4a7625d48cacba39d2f52 | 31f1846932e8d655246306b6d84a5262fad4713d | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | 6b96c5cba55f8616fa766b8f39d4ef6cafb2237e | ba4172548fc1649dce41afcd8f46a46ae6c8b7f2 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/mixin/FallingBlockRendererMixin.java` | bb51eb7bfafee9927934016fd69e0a0d20174450 | dcaf07e5c9398e9ba3cba1abbb5d435063330789 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/mixin/GameRendererPostLevelMixin.java` | ff9eac1d7fb656d108cd3eaab6d7cbc02194b3aa | 693d90624873b2a8f3932fcdc2c12abc57612cfb | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `src/main/java/net/eca/mixin/ItemRendererMixin.java` | 897f7d63dd910dab10d40de5c9c80c9d51f7ce50 | d5f8627e932351d1d13ada8dec891754e82542d8 | ok | jivinstev/EpicCoreAPI 1.21.5, round 2026-10-30 |
| `.port/1.21.8/REMERGE.json` | - | c6a8c4f1c863c7aed676e1b76e72aca511256747 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `gradle.properties` | d10fa9dab0f4fdf2e61f6211ec476c84023c1f41 | de1c8bc723a070424a41c018e095d690d820ee4d | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/BlockExtensionRenderer.java` | 42d9a453e6d9c26242443331741f7e978dc24aca | d25ca31c6a97e5d7bd93b6b11c4a812976c7fffb | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/EntityExtensionLayer.java` | f96cb6ecce28ba16f989dfdd966676300346838b | affe4ed86d534f24905bfb049c9e7a3d0b206fdf | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/GeoBlockExtensionLayer.java` | 5d2ba6734b1220bb3c4451716a7b2c1c6961427a | 84b58abb5ea4b77ba3b2bcf9cf809a3fb953da83 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/GeoEntityExtensionLayer.java` | d7f12e944ccbe020b69f882e0aff4cb9fab1fd65 | b20d20227890c804b090173293306efda3338584 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/RenderBufferPool.java` | - | 8a0b483581abc48efaf7d05fe5f197b23848f1aa | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/ShaderMaskRenderQueue.java` | 5dbfd0903a62644a1bd861c1c06bef22eccebf46 | 560611073fc8da78b229acd9734ea3cbcadff1d9 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/SpriteBatchingVertexConsumer.java` | bfb915543f7249e0997c2ad89f6f2cf0dcfa2977 | 0013c8ea240b713984d06a52f56bdca4469ec17e | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | b46e075e30bad8ff751afdf884aef59abe3519d6 | 8b0398539a402a5b0d35a791886fba458da03e9a | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/coremod/LoadingScreenTransformer.java` | 6523f4d5912e5eaf6043a56210d5112ad6b40f00 | c7de67c03c6e8c5852fa1526e1c543b5755b3854 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/mixin/GameRendererPostLevelMixin.java` | 0c852a4e1f086f5b19dee22925af556ce74de6ec | 1fbbdb079e58a476f18a98c0b8710434ec0c79a5 | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `src/main/java/net/eca/mixin/ItemRendererMixin.java` | 35f0868c22cb824f2dd685dc3a5051c160f3eab7 | f325a74baf76b8626f2295bbefcf23584d1331dc | ok | jivinstev/EpicCoreAPI 1.21.8, round 2026-10-30 |
| `.port/branch.gradle` | fc09a2c581e0f087da919d71fafffcfd108189a8 | 565e5b0b09d7ef8d188e0e89b9345b910dcfd8f4 | ok | jivinstev/EpicCoreAPI 1.21.3, round 2026-10-30 |
