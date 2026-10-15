# Residue: loop

Stopped at **loop**: 30 compile errors.

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
30 errors in 8 files
By CATALOG.md entry (3 errors) -- read only these entries: V99
      3  §V99   e.g. net/eca/mv/ShaderInstance.java:218: cannot find symbol: method withSampler(String)
Not in the catalogue (27 errors), by family:
      6  cannot find symbol: class OutputTarget
      3  cannot find symbol: method precompilePipeline
      2  cannot find symbol: class GpuBuffer
      2  cannot find symbol: class RenderPass
      2  cannot find symbol: class PrimitiveTopology
      2  cannot find symbol: method bindTexture
      2  cannot find symbol: variable outputColorTextureOverride
      2  cannot find symbol: variable useDepth
  ... 5 more families, 6 errors
Files with the most errors (`--file <path>` lists one file's errors):
     12  net/eca/mv/LegacyGl.java
      6  net/eca/mv/LegacyRenderType.java
      4  net/eca/mv/Uniform.java
      3  net/eca/mv/Tesselator.java
      2  net/eca/mv/ShaderInstance.java
      1  net/eca/mv/ItemModels.java
      1  net/eca/mv/LegacyTargets.java
      1  net/eca/mv/MultiBufferSource.java
```

## The loop's record

- spent $3.1541
- still failing at the top tier: none
- rows kept: 1
- row tried and reverted: `com.mojang.blaze3d.opengl.GlStateManager	com.mojang.renderpearl.backend.opengl.GlStateManager` (compile None >= 541: reverted)
- row tried and reverted: `com.mojang.blaze3d.platform.BlendFactor	com.mojang.renderpearl.api.pipeline.BlendFactor` (compile None >= 541: reverted)
- row tried and reverted: `com.mojang.blaze3d.buffers	com.mojang.renderpearl.api.buffers` (compile 541 >= 541: reverted)
- row tried and reverted: `com.mojang.blaze3d.PrimitiveTopology	com.mojang.renderpearl.api.pipeline.PrimitiveTopology` (compile None >= 541: reverted)
- row tried and reverted: `com.mojang.blaze3d.vertex.VertexFormat	com.mojang.renderpearl.api.vertex.VertexFormat` (compile None >= 541: reverted)
- row tried and reverted: `com.mojang.blaze3d.buffers.GpuBufferSlice	com.mojang.renderpearl.api.buffers.GpuBufferSlice` (compile None >= 541: reverted)
- row tried and reverted: `GLFW.GLFW_KEY_LEFT_SHIFT	InputConstants.KEY_LSHIFT` (compile 541 >= 541: reverted)
- row tried and reverted: `AbortableIterationConsumer.Continuation	Continuation` (compile 541 >= 541: reverted)
- row tried and reverted: `re:com\.mojang\.blaze3d\.textures\.	com.mojang.renderpearl.api.textures.` (compile 212 >= 212: reverted)

## To resume

`python3 tools/fork-port.py --repo /home/user/eca-layer --target 26.3 --from-stage loop --budget 10.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
