# Review: Minecraft 1.21.5

An independent review by a FRESH agent that was given only this target's files (STUBS.json, its overlays,
the authors' files they replace, its resource overlays and renames.tsv). One row per STUBS.json entry,
per whole-file overlay, per resource group (`resources`: a directory of the target's resources/, with the
digest of its files -- an edit makes it a new row) and for renames.tsv (`renames`, with its digest);
verdict `ok` (the change keeps the authors' behaviour, or the difference is right and the note says why)
or `not-ok` (it does not: the target is not done). A hook item of the behaviour inventory (a mixin target,
an injection, an event listener, a shader uniform/sampler) is accounted for only by a row whose note names
its id, or by an `item` row for the id. `reviewer` names the reviewing agent, distinct from the fixer
(e.g. `fresh agent <id>, not the fixer`). `commit` is the commit reviewed, a hex id of 7-40 digits
(`git rev-parse --short HEAD`). tools/fork-port.py adds a `?` row for every item it finds unreviewed
(an `item` row for every unaccounted inventory item); it never changes a verdict.

- reviewer: ?
- date: ?
- commit: ?

| kind | file | verdict | note |
|---|---|---|---|
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/BlockExtensionRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/GeoBlockExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/GeoEntityExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/shader/FilterRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/shader_generator/AnimatedPreviewAtlas.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/shader_generator/AnimatedPreviewTexture.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/compat/GeckoLibCompat.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/mixin/FallingBlockRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/mixin/ItemRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/java/net/eca/mixin/PlayerMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.5/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java | ? | ? |
| renames | .multiversion/targets/1.21.5/renames.tsv @bddce9da7be9 | ? | ? |
| resources | .multiversion/targets/1.21.5/resources/META-INF/accesstransformer.cfg @65a2f0e2d0ee | ? | ? |
| stub | .multiversion/targets/1.21.5/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| stub | .multiversion/targets/1.21.5/java/net/eca/mixin/PlayerMixin.java | ? | ? |
| stub | .multiversion/targets/1.21.5/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java | ? | ? |
| item | main:net/eca/client/render/shader/FilterRenderer.java#render-use:RenderSystem.setShader | ? | ? |
| item | main:net/eca/client/render/shader/FilterRenderer.java#render-use:RenderSystem.setShaderTexture | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupColor @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupFog @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogEnd | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogShape | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogStart | ? | ? |
| item | main:net/eca/mixin/ItemRendererMixin.java#mixin:@Mixin ItemRenderer | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Inject method=render @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="RETURN") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V @At(value="RETURN") | ? | ? |
| item | main:resources/assets/eca/shaders/core/arcane.json#shader-uniform:eca:core/arcane Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/arcane_block.json#shader-uniform:eca:core/arcane_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/arcane_block.json#shader:eca:core/arcane_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/arcane_entity.json#shader-uniform:eca:core/arcane_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/arcane_entity.json#shader:eca:core/arcane_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/aurora.json#shader-uniform:eca:core/aurora Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/aurora_block.json#shader-uniform:eca:core/aurora_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/aurora_block.json#shader:eca:core/aurora_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/aurora_entity.json#shader-uniform:eca:core/aurora_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/aurora_entity.json#shader:eca:core/aurora_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/black_hole.json#shader-uniform:eca:core/black_hole Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/black_hole_block.json#shader-uniform:eca:core/black_hole_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/black_hole_block.json#shader:eca:core/black_hole_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/black_hole_entity.json#shader-uniform:eca:core/black_hole_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/black_hole_entity.json#shader:eca:core/black_hole_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/cosmos.json#shader-uniform:eca:core/cosmos Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/cosmos_block.json#shader-uniform:eca:core/cosmos_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/cosmos_block.json#shader:eca:core/cosmos_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/cosmos_entity.json#shader-uniform:eca:core/cosmos_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/cosmos_entity.json#shader:eca:core/cosmos_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/dream_sakura_block.json#shader:eca:core/dream_sakura_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/dream_sakura_entity.json#shader:eca:core/dream_sakura_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/filters/cosmos.json#shader-sampler:eca:core/filters/cosmos Sampler3 | ? | ? |
| item | main:resources/assets/eca/shaders/core/forest.json#shader-uniform:eca:core/forest Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/forest_block.json#shader-uniform:eca:core/forest_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/forest_block.json#shader:eca:core/forest_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/forest_entity.json#shader-uniform:eca:core/forest_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/forest_entity.json#shader:eca:core/forest_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/hacker.json#shader-uniform:eca:core/hacker Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/hacker_block.json#shader-uniform:eca:core/hacker_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/hacker_block.json#shader:eca:core/hacker_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/hacker_entity.json#shader-uniform:eca:core/hacker_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/hacker_entity.json#shader:eca:core/hacker_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/ocean_block.json#shader:eca:core/ocean_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/ocean_entity.json#shader:eca:core/ocean_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/starlight.json#shader-uniform:eca:core/starlight Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/starlight_block.json#shader-uniform:eca:core/starlight_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/starlight_block.json#shader:eca:core/starlight_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/starlight_entity.json#shader-uniform:eca:core/starlight_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/starlight_entity.json#shader:eca:core/starlight_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/storm_block.json#shader:eca:core/storm_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/storm_entity.json#shader:eca:core/storm_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/the_last_end.json#shader-uniform:eca:core/the_last_end Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/the_last_end_block.json#shader-uniform:eca:core/the_last_end_block Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/the_last_end_block.json#shader:eca:core/the_last_end_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/the_last_end_entity.json#shader-uniform:eca:core/the_last_end_entity Glow | ? | ? |
| item | main:resources/assets/eca/shaders/core/the_last_end_entity.json#shader:eca:core/the_last_end_entity.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/volcano_block.json#shader:eca:core/volcano_block.json | ? | ? |
| item | main:resources/assets/eca/shaders/core/volcano_entity.json#shader:eca:core/volcano_entity.json | ? | ? |
