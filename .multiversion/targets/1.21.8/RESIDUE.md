# Residue: audit

Stopped at **audit**: 6 mixin target(s) do not match vanilla.

## Mixin audit mismatches

```
  GameRendererPostLevelMixin.java                @At renderLevel           
      mixin:   net/minecraft/client/renderer/LevelRenderer.renderLevel:(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V  (no such call in the target's bytecode)
      vanilla: (no call of that name anywhere in renderLevel)
  LevelRendererMixin.java                        @Inject renderLevel       
      mixin:   (GraphicsResourceAllocator, DeltaTracker, boolean, Camera, GameRenderer, Matrix4f, Matrix4f)
      vanilla: void (GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)
  LevelRendererMixin.java                        @Inject renderLevel       
      mixin:   (GraphicsResourceAllocator, DeltaTracker, boolean, Camera, GameRenderer, Matrix4f, Matrix4f)
      vanilla: void (GraphicsResourceAllocator, DeltaTracker, boolean, Camera, Matrix4f, Matrix4f, GpuBufferSlice, Vector4f, boolean)
  LivingEntityMixin.java                         @Inject addAdditionalSaveData
      mixin:   (CompoundTag)
      vanilla: void (ValueOutput)
  LivingEntityMixin.java                         @Inject readAdditionalSaveData
      mixin:   (CompoundTag)
```

## To resume

`python3 tools/fork-port.py --repo . --target 1.21.8 --from-stage audit --budget 8.0 --gates full`

Fix what is listed here, and put every fix in the shared library (templates/fork-layer/shared/<t>) or a converter, not in one fork.
