# Cascade decisions (append-only; tools/fork-branches.py cascade)

| date | decision | file | version | choice | by | reasoning |
|---|---|---|---|---|---|---|
| 2026-10-30 | 857a3dbf02d6 | `.github/port-publish/description.md` | 1.21.4 | patch | default | the head's project page text (one page for every version) taken as is: every version's copy equals the head's (id from the re-plan at the merged head) |
| 2026-10-30 | 4120ff0ffe6a | `src/main/java/net/eca/mixin/ItemRendererMixin.java` | 1.21.4 | patch | default | the authors' try-with-resources around each version's own SpriteBatchingVertexConsumer use (both sites on 26.3) (id from the re-plan at the merged head) |
| 2026-10-30 | 857a3dbf02d6 | `.github/port-publish/description.md` | 1.21.5 | patch | default | the authors' publish-description change (NeoForge title, Known issues section) taken around ours at 1.21.5+. Not on the frozen plan: the re-run plan after the head merge raises it |
| 2026-10-30 | 25f93a0e1f69 | `src/main/java/net/eca/client/render/BlockExtensionRenderer.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 46f2900aadaa | `src/main/java/net/eca/client/render/GeoBlockExtensionLayer.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 7e2e520e23ae | `src/main/java/net/eca/client/render/GeoEntityExtensionLayer.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 3f0d22c54dd2 | `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 3097937b8d04 | `src/main/java/net/eca/mixin/FallingBlockRendererMixin.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 4120ff0ffe6a | `src/main/java/net/eca/mixin/ItemRendererMixin.java` | 1.21.5 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 857a3dbf02d6 | `.github/port-publish/description.md` | 1.21.8 | patch | default | the authors' publish-description change (NeoForge title, Known issues section) taken around ours at 1.21.5+. Not on the frozen plan: the re-run plan after the head merge raises it |
| 2026-10-30 | 3f0d22c54dd2 | `src/main/java/net/eca/client/render/shader_generator/ShaderPreviewRenderer.java` | 1.21.8 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
| 2026-10-30 | 12e3075730c9 | `src/main/java/net/eca/coremod/LoadingScreenTransformer.java` | 1.21.8 | patch | default | the authors' leak fix (RenderBufferPool, release on failure, pops in finally) ported per version |
