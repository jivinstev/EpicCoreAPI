// OVERLAY-ONLY: community fork multi-version layer (not the authors' code).
package net.eca.mv;

/** The authors' "a force-loaded entity is behind the clouds this frame" flag, shared by LevelRendererMixin
 *  (which sets it while rendering entities) and CloudRendererMixin (which skips the cloud pass on it). */
public final class CloudGate {
    public static boolean occlude;

    private CloudGate() {
    }
}
