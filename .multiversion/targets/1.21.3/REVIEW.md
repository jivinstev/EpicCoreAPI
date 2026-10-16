# Review: Minecraft 1.21.3

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
| overlay | .multiversion/targets/1.21.3/java/net/eca/blender/client/render/BlenderModelLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/client/render/EntityExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/client/render/shader/FilterRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/mixin/FallingBlockRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/mixin/FogRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/mixin/LivingEntityRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.3/java/net/eca/mixin/ServerPlayerMixin.java | ? | ? |
| renames | .multiversion/targets/1.21.3/renames.tsv @1914a6f1a1be | ? | ? |
| resources | .multiversion/targets/1.21.3/resources/META-INF/accesstransformer.cfg @bec16ddefdf7 | ? | ? |
| stub | .multiversion/targets/1.21.3/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupColor @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupFog @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogEnd | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogShape | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogStart | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Inject method=render @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="RETURN") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/ServerPlayerMixin.java#injection:@Inject method=teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V @At(value="RETURN") | ? | ? |
