// OVERLAY-ONLY: shared fork-layer helper from mc-mod-version-upgrade templates/fork-layer/shared (not the authors' code).
package net.eca.mv;

import net.minecraft.client.gui.components.EditBox;

import java.util.function.Predicate;

/**
 * 1.21's {@code EditBox.setFilter(Predicate<String>)}: NeoForge added it back through 26.1.2 and 26.2 has it no more
 * (EditBox keeps only {@code setResponder}). This stands in with a responder that puts back the last accepted value when an
 * edit produces text the filter rejects -- the text the user sees is what 1.21's filter let through. It takes the box's
 * responder slot: a responder the mod sets AFTER this replaces it (as {@code setResponder} replaced any responder in 1.21),
 * so a box that needs both should be given its responder first and the filter through {@link #setFilter(EditBox, Predicate,
 * java.util.function.Consumer)}.
 */
public final class EditBoxes {
    private EditBoxes() {
    }

    public static void setFilter(EditBox box, Predicate<String> filter) {
        setFilter(box, filter, null);
    }

    public static void setFilter(EditBox box, Predicate<String> filter, java.util.function.Consumer<String> responder) {
        String[] last = {filter.test(box.getValue()) ? box.getValue() : ""};
        box.setResponder(value -> {
            if (filter.test(value)) {
                last[0] = value;
                if (responder != null) responder.accept(value);
            } else if (!value.equals(last[0])) {
                box.setValue(last[0]);
            }
        });
    }
}
