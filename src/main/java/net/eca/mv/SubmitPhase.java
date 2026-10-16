// OVERLAY-ONLY: fork multi-version layer (26.2), not the authors' code.
package net.eca.mv;

/**
 * 26.2 (#33): whether a submit is the level's. 26.1.2 submitted the level's entities inside the main frame pass (after
 * the clear and the terrain); 26.2's LevelRenderer.render submits every feature (submitFeatures) BEFORE it builds the
 * frame graph, so a draw an extension pass makes at once from a level submit lands before the clear pass and is erased.
 * While {@link #levelSubmit} is up, those passes go to the authors' after-level queue instead
 * (ShaderMaskRenderQueue.enqueue: the model-view and projection captured now, drawn through LegacyGl inside the level's
 * main pass at AfterTranslucentFeatures (#37), or after LevelRenderer.render returns with Oculus shaders).
 */
public final class SubmitPhase {
    private SubmitPhase() {
    }

    /** True while LevelRenderer.render runs submitFeatures (set by the 26.2 LevelRendererMixin overlay). */
    public static boolean levelSubmit;
}
