# Review: Minecraft 1.21.10

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
| overlay | .multiversion/targets/1.21.10/cf/java/net/eca/coremod/EcaTransformationService.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/agent/AgentLoader.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/blender/client/render/BlenderModelLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/BossShowKeyBindings.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowEditorHomeScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowEditorScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowEditorSettingsScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowPathGeneratorScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowRangeTransformScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowShortcutHelpScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/BossShowTimelineWidget.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderAiAssistantScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderAiSettingsScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderAiTranscriptWidget.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderColorPickerScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderEffectDetailsScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderGeneratorScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderImportScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderLayerDetailsScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderPercentEditWidget.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderProjectDetailsScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderSliderWidget.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/gui/ShaderSourceEditorScreen.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/BlockExtensionRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/BossShowAnchorRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/EntityExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/FrustumGuard.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/GeoBlockExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/GeoEntityExtensionLayer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/preset/ShaderPresetRegistry.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/shader/FilterRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/compat/GeckoLibCompat.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/coremod/EcaCoreTransformer.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/coremod/EcaTransformationService.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/event/BossShowEditorClientEvents.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/mixin/FallingBlockRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/mixin/ItemRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/mixin/LivingEntityRendererMixin.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/network/BossShowStartPacket.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/util/EntityUtil.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/util/ResurrectionManager.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/util/bossshow/BossShowEditorSessionManager.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/java/net/eca/util/bossshow/BossShowManager.java | ? | ? |
| overlay | .multiversion/targets/1.21.10/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java | ? | ? |
| renames | .multiversion/targets/1.21.10/renames.tsv @68a702c30bc8 | ? | ? |
| resources | .multiversion/targets/1.21.10/resources/META-INF/accesstransformer.cfg @5ace9471d103 | ? | ? |
| resources | .multiversion/targets/1.21.10/resources/META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessorProvider @138dd0f67cef | ? | ? |
| stub | .multiversion/targets/1.21.10/cf/java/net/eca/coremod/EcaTransformationService.java | ? | ? |
| stub | .multiversion/targets/1.21.10/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java | ? | ? |
| stub | .multiversion/targets/1.21.10/java/net/eca/coremod/EcaCoreTransformer.java | ? | ? |
| stub | .multiversion/targets/1.21.10/java/net/eca/coremod/EcaTransformationService.java | ? | ? |
| stub | .multiversion/targets/1.21.10/java/net/eca/mixin/LevelRendererMixin.java | ? | ? |
| stub | .multiversion/targets/1.21.10/pro/java/net/eca/pro/EcaProImmediateWindowProvider.java | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/ArcaneRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/AuroraRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/BlackHoleRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/BlockExtensionRenderer.java#event:addListener RenderLevelStageEvent | ? | ? |
| item | main:net/eca/client/render/BossShowAnchorRenderer.java#event:@SubscribeEvent RenderLevelStageEvent | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/CosmosRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/DreamSakuraRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/EcaBossBarRenderer.java#render-use:GameRenderer.getPositionColorShader | ? | ? |
| item | main:net/eca/client/render/EcaBossBarRenderer.java#render-use:GameRenderer.getPositionTexShader | ? | ? |
| item | main:net/eca/client/render/EcaBossBarRenderer.java#render-use:RenderSystem.setShader | ? | ? |
| item | main:net/eca/client/render/EcaBossBarRenderer.java#render-use:RenderSystem.setShaderColor | ? | ? |
| item | main:net/eca/client/render/EcaBossBarRenderer.java#render-use:RenderSystem.setShaderTexture | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/ForestRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/HackerRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/OceanRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/StarlightRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/StormRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/TheLastEndRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/VolcanoRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.CULL | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.POLYGON_OFFSET_LAYERING | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/preset/PresetRenderTypes.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/client/render/shader/FilterRenderer.java#event:@SubscribeEvent RenderLevelStageEvent | ? | ? |
| item | main:net/eca/client/render/shader/FilterRenderer.java#render-use:RenderSystem.setShader | ? | ? |
| item | main:net/eca/client/render/shader/FilterRenderer.java#render-use:RenderSystem.setShaderTexture | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderStateShard.ShaderStateShard | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.COLOR_DEPTH_WRITE | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.COLOR_WRITE | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.CompositeState | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.LEQUAL_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.NO_CULL | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.NO_DEPTH_TEST | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.TRANSLUCENT_TRANSPARENCY | ? | ? |
| item | main:net/eca/client/render/shader_generator/GeneratedShaderPreview.java#render-use:RenderType.create | ? | ? |
| item | main:net/eca/compat/GeckoLibCompat.java#event:addListener GeoRenderEvent.Block.CompileRenderLayers | ? | ? |
| item | main:net/eca/compat/GeckoLibCompat.java#event:addListener GeoRenderEvent.Entity.CompileRenderLayers | ? | ? |
| item | main:net/eca/compat/GeckoLibCompat.java#event:addListener GeoRenderEvent.ReplacedEntity.CompileRenderLayers | ? | ? |
| item | main:net/eca/event/BossShowEditorClientEvents.java#event:@SubscribeEvent RenderLevelStageEvent | ? | ? |
| item | main:net/eca/mixin/EntityMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/EntityMixin.java#injection:@Inject method=changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity; @At(value="RETURN") | ? | ? |
| item | main:net/eca/mixin/FallingBlockRendererMixin.java#injection:@Inject method=render @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupColor @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#injection:@Inject method=setupFog @At(value="TAIL") | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogEnd | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogShape | ? | ? |
| item | main:net/eca/mixin/FogRendererMixin.java#render-use:RenderSystem.setShaderFogStart | ? | ? |
| item | main:net/eca/mixin/ItemRendererMixin.java#injection:@Inject method=render @At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",shift=At.Shift.BEFORE) | ? | ? |
| item | main:net/eca/mixin/ItemRendererMixin.java#mixin:@Mixin ItemRenderer | ? | ? |
| item | main:net/eca/mixin/LivingEntityMixin.java#injection:@Inject method=hurt @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/LivingEntityMixin.java#injection:@Inject method=hurt @At(value="RETURN") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Inject method=render @At(value="HEAD") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V") | ? | ? |
| item | main:net/eca/mixin/LivingEntityRendererMixin.java#injection:@Redirect method=render @At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V") | ? | ? |
| item | main:net/eca/mixin/PlayerMixin.java#injection:@Inject method=setItemSlot @At(value="HEAD") | ? | ? |
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
| item | pro:net/eca/pro/EcaProIntroRenderer.java#render-use:RenderType.TEXTURE | ? | ? |
