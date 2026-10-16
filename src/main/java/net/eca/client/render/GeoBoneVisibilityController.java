// Fork multi-version layer: 1.21.11 version of the authors' file (GeckoLib 5.4: a bone no longer carries hidden state of its
// own. setHidden / isHidden / isHidingChildren / getChildBones / getName are gone from GeoBone; "skip this bone" is a flag on the
// BoneSnapshot of the CURRENT render pass, GeoBone.frameSnapshot, which exists only while RenderPassInfo.renderPosed runs).
//
// So this class has two halves, and the caller has to use the right one at the right time:
//  * hideInPass(...)  -- from a RenderPassInfo.BoneUpdater, which is where the pass's snapshots are made. Hides the configured
//    bones for EVERY model task of the pass, GeckoLib's own included.
//  * begin / restrictOverlay / restore -- INSIDE renderPosed(...) (frameSnapshot is live there and null everywhere else), around
//    one overlay draw. They flip the flags for that draw only and put them back, because the snapshots are shared by every task
//    of the pass.
package net.eca.client.render;

import net.eca.util.entity_extension.EntityLayerExtension;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import software.bernie.geckolib.renderer.base.BoneSnapshots;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class GeoBoneVisibilityController {

    private final Map<GeoBone, BoneVisibility> originalVisibility = new IdentityHashMap<>();

    /** BoneUpdater half: hide the configured bones in the pass's own snapshots (so the model's main task honours them too). */
    static void hideInPass(BoneSnapshots snapshots, Set<String> hiddenBones) {
        if (hiddenBones == null) {
            return;
        }
        for (String name : hiddenBones) {
            snapshots.ifPresent(name, snapshot -> snapshot.skipRender(true));
        }
    }

    void begin(BakedGeoModel model, EntityLayerExtension extension) {
        begin(model, extension.hiddenGeoBones());
    }

    /** Inside renderPosed: remember every bone's flags, then hide the configured bones. */
    void begin(BakedGeoModel model, Set<String> hiddenBones) {
        restore();
        for (GeoBone bone : model.topLevelBones()) {
            capture(bone);
        }
        for (GeoBone bone : model.topLevelBones()) {
            hideConfiguredBones(bone, hiddenBones);
        }
    }

    void restrictOverlay(BakedGeoModel model, Set<String> roots) {
        if (roots == null || roots.isEmpty()) {
            return;
        }
        for (GeoBone bone : model.topLevelBones()) {
            hideOutsideOverlayRoots(bone, roots, false);
        }
    }

    /** Put every flag back; a snapshot this class had to make (the bone had none in this pass) is taken away again. */
    void restore() {
        for (Map.Entry<GeoBone, BoneVisibility> entry : originalVisibility.entrySet()) {
            GeoBone bone = entry.getKey();
            BoneVisibility visibility = entry.getValue();
            if (visibility.created()) {
                bone.frameSnapshot = null;
            } else {
                visibility.snapshot().skipRender(visibility.hidden()).skipChildrenRender(visibility.childrenHidden());
            }
        }
        originalVisibility.clear();
    }

    private static BoneSnapshot snapshotOf(GeoBone bone) {
        if (bone.frameSnapshot == null) {
            bone.frameSnapshot = BoneSnapshot.create(bone);
        }
        return bone.frameSnapshot;
    }

    private void capture(GeoBone bone) {
        if (!originalVisibility.containsKey(bone)) {
            boolean created = bone.frameSnapshot == null;
            BoneSnapshot snapshot = snapshotOf(bone);
            originalVisibility.put(bone, new BoneVisibility(snapshot, created, snapshot.isHidden(), snapshot.areChildrenHidden()));
        }
        for (GeoBone child : bone.children()) {
            capture(child);
        }
    }

    private void hideConfiguredBones(GeoBone bone, Set<String> hiddenBones) {
        if (hiddenBones != null && hiddenBones.contains(bone.name())) {
            snapshotOf(bone).skipRender(true);
            return;
        }
        for (GeoBone child : bone.children()) {
            hideConfiguredBones(child, hiddenBones);
        }
    }

    private boolean hideOutsideOverlayRoots(GeoBone bone, Set<String> roots, boolean included) {
        boolean renderThisBranch = included || roots.contains(bone.name());
        boolean hasIncludedDescendant = false;

        for (GeoBone child : bone.children()) {
            hasIncludedDescendant |= hideOutsideOverlayRoots(child, roots, renderThisBranch);
        }

        if (!renderThisBranch && !hasIncludedDescendant) {
            snapshotOf(bone).skipRender(true);
        }
        return renderThisBranch || hasIncludedDescendant;
    }

    private record BoneVisibility(BoneSnapshot snapshot, boolean created, boolean hidden, boolean childrenHidden) {}
}
