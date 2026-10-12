# Residue: loop

Stopped at **loop**: 21 compile errors.

## Remaining compile errors (compile-summary.py, grouped by CATALOG entry / family)

```
21 errors in 7 files
Not in the catalogue (21 errors), by family:
      4  method does not override or implement a method from a supertype
      4  cannot find symbol: variable renderer
      2  wrong number of type arguments; required 3
      2  cannot find symbol: class GeoRenderer
      2  incompatible types: incompatible parameter types in lambda expression
      1  wrong number of type arguments; required 2
      1  cannot find symbol: method getModelOffset
      1  no suitable method found for addT(T)
  ... 4 more families, 4 errors
Files with the most errors (`--file <path>` lists one file's errors):
      8  net/eca/client/render/GeoEntityExtensionLayer.java
      6  net/eca/client/render/GeoBlockExtensionLayer.java
      3  net/eca/compat/GeckoLibCompat.java
      1  net/eca/mv/LegacyGl.java
      1  net/eca/mv/LegacyRenderType.java
      1  net/eca/mv/LegacyTargets.java
      1  net/eca/mv/Tickets.java
```

## The loop's record

- spent $3.3104
- still failing at the top tier: net/eca/client/render/GeoBlockExtensionLayer.java, net/eca/client/render/GeoEntityExtensionLayer.java
- rows kept: 1
- row tried and reverted: `level().isClientSide	level().isClientSide()` (compile 103 >= 103: reverted)
- row tried and reverted: `event.getPartialTick()	event.getDeltaTracker()` (compile 95 >= 95: reverted)
- row tried and reverted: `player.getGameProfile().getName()	player.getGameProfile().name()` (compile 95 >= 95: reverted)
- row tried and reverted: `.isClientSide	.isClientSide()` (compile None >= 95: reverted)
- row tried and reverted: `.isClientSide	.isClientSide()` (compile None >= 95: reverted)
- row tried and reverted: `FMLEnvironment.dist	FMLLoader.getDist()` (compile 95 >= 95: reverted)
- row tried and reverted: `FMLEnvironment.dist	FMLEnvironment.getDist()` (compile 95 >= 95: reverted)
- row tried and reverted: `FMLEnvironment.dist	FMLEnvironment.getDist()` (compile 95 >= 95: reverted)
- row tried and reverted: `FMLEnvironment.dist	FMLEnvironment.getDist()` (compile 95 >= 95: reverted)
- row tried and reverted: `player.walkDist	player.walkAnimation.position()` (compile 95 >= 95: reverted)

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.10 --from-stage loop --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
