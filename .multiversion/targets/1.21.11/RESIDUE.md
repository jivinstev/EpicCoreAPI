# Residue: loop

Stopped at **loop**: 32 compile errors.

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
32 errors in 9 files
By CATALOG.md entry (10 errors) -- read only these entries: V4
     10  §V4    e.g. net/eca/mv/ReloadIds.java:4: cannot find symbol: class ResourceLocation
Not in the catalogue (22 errors), by family:
      4  cannot find symbol: class SkullModelBase
      4  method does not override or implement a method from a supertype
      3  cannot find symbol: method setHidden
      3  cannot find symbol: method getChildBones
      2  cannot find symbol: method renderBone
      2  cannot find symbol: method getName
      1  cannot find symbol: class Context
      1  cannot find symbol: method setChildrenHidden
  ... 2 more families, 2 errors
Files with the most errors (`--file <path>` lists one file's errors):
     10  net/eca/client/render/GeoBoneVisibilityController.java
      4  net/eca/client/render/GeoEntityExtensionLayer.java
      4  net/eca/mv/Skulls.java
      3  net/eca/client/render/GeoBlockExtensionLayer.java
      3  net/eca/mv/ReloadIds.java
      3  net/eca/mv/Textures.java
      2  net/eca/mv/EntityOf.java
      2  net/eca/mv/ShaderReloader.java
  ... 1 more files
```

## The loop's record

- spent $6.7602
- still failing at the top tier: net/eca/client/render/GeoBlockExtensionLayer.java, net/eca/client/render/GeoBoneVisibilityController.java, net/eca/client/render/GeoEntityExtensionLayer.java
- rows kept: 0
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `net.minecraft.resources.ResourceLocation	net.minecraft.resources.Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)
- row tried and reverted: `ResourceLocation	Identifier` (compile 312 >= 312: reverted)

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.11 --from-stage loop --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
