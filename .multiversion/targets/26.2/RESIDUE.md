# Residue: loop

Stopped at **loop**: 118 compile errors.

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
118 errors in 22 files
By CATALOG.md entry (19 errors) -- read only these entries: V92#4 V6
     14  §V92#4 e.g. net/eca/blender/client/runtime/BlendMaterialProgram.java:14: cannot find symbol: class MultiBufferSource
      5  §V6    e.g. net/eca/blender/client/runtime/BlendMaterialProgram.java:48: package MultiBufferSource does not exist
Not in the catalogue (99 errors), by family:
     17  cannot find symbol: variable Mode
     14  cannot find symbol: method main
      7  cannot find symbol: class SourceFactor
      7  cannot find symbol: class Mode
      6  cannot find symbol: class VertexFormatMode
      5  cannot find symbol: class DestFactor
      5  cannot find symbol: variable SourceFactor
      5  cannot find symbol: variable VertexFormatMode
  ... 25 more families, 33 errors
Files with the most errors (`--file <path>` lists one file's errors):
     20  net/eca/client/render/shader/FilterRenderer.java
     18  net/eca/mv/LegacyGl.java
     13  net/eca/mv/ShaderInstance.java
     11  net/eca/mv/LegacyRenderType.java
      6  net/eca/client/render/ForestRenderTypes.java
      6  net/eca/client/render/preset/PresetRenderTypes.java
      5  net/eca/blender/client/render/BlenderModelRenderer.java
      5  net/eca/client/render/BlackHoleRenderTypes.java
  ... 14 more files
```

## The loop's record

- spent $11.9669
- still failing at the top tier: net/eca/blender/client/entity/BlenderEntityRenderer.java, net/eca/blender/client/render/BlenderModelLayer.java, net/eca/blender/client/render/BlenderModelRenderer.java, net/eca/blender/client/runtime/BlendMaterialProgram.java, net/eca/client/render/BossShowAnchorRenderer.java, net/eca/client/render/EntityExtensionLayer.java, net/eca/client/render/blender/BlenderModelRenderer.java, net/eca/mixin/ItemRendererMixin.java, net/eca/mixin/LivingEntityRendererMixin.java
- rows kept: 5
- row tried and reverted: `net.minecraft.client.renderer.MultiBufferSource	net.eca.util.compat.MultiBufferSource` (compile 382 >= 382: reverted)
- row tried and reverted: `net.minecraft.client.renderer.MultiBufferSource	net.eca.mv.MultiBufferSource` (compile 382 >= 382: reverted)
- row tried and reverted: `net.minecraft.advancements.CriteriaTriggers	net.minecraft.advancements.triggers.CriteriaTriggers` (compile None >= 382: reverted)
- row tried and reverted: `net.minecraft.advancements.criterion.MinMaxBounds	net.minecraft.advancements.predicates.MinMaxBounds` (compile None >= 382: reverted)
- row tried and reverted: `RenderSystem.getModelViewMatrix()	RenderSystem.getModelViewStack()` (compile 382 >= 382: reverted)
- row tried and reverted: `Minecraft.getInstance().setScreen(	Minecraft.getInstance().gui.setScreen(` (compile 325 >= 325: reverted)
- row tried and reverted: `VertexFormat.Mode	VertexFormatMode` (compile 306 >= 306: reverted)
- row tried and reverted: `re:\bminecraft\.setScreen\(	minecraft.gui.setScreen(` (compile 191 >= 191: reverted)
- row tried and reverted: `re:(?:GlStateManager\.SourceFactor|com\.mojang\.blaze3d\.platform\.(?:SourceFactor|DestFactor))\.	BlendFactor.` (compile 191 >= 191: reverted)
- row tried and reverted: `net.eca.mv.VertexFormatMode	com.mojang.blaze3d.PrimitiveTopology` (compile 189 >= 189: reverted)
- STUB WARNING (a feature may have been deleted to clear an error): .multiversion/targets/26.2/java/net/eca/client/render/EcaBossBarRenderer.java

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.2 --from-stage loop --budget 10.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
