// OVERLAY-ONLY: shared fork-layer helper from mc-mod-version-upgrade templates/fork-layer/shared (not the authors' code).
package net.eca.mv;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 1.21.2 made every Block and Item carry its registry id in its Properties (CATALOG R25): `new Block(props)` and
 * `new Item(props)` throw "Block id not set" / "Item id not set" without it. A 1.21.1 mod builds its Properties where
 * the registry name is often not in scope (an Item subclass's own constructor), so the name is passed the other way:
 * the shared rows turn {@code REG.register("name", () -> new X(...))} into
 * {@code REG.register("name", () -> RegIds.enter("name") ? null : new X(...))} -- the supplier records the name it is
 * about to build -- and wrap every {@code new Item.Properties()} / {@code BlockBehaviour.Properties.of()} in
 * {@link #stamp}, which sets the id from that name. Overloading picks the item or block form by static type.
 */
public final class RegIds {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RegIds() {
    }

    /** Called first in a registration supplier; always false, so {@code enter(n) ? null : new X()} builds X. */
    public static boolean enter(String name) {
        CURRENT.set(name);
        return false;
    }

    public static Item.Properties stamp(Item.Properties props) {
        String name = CURRENT.get();
        if (name == null) {
            return props;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("eca", name);
        // a block item kept its block's translation key in 1.21.1; in 1.21.2 that is opt-in
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            props = props.useBlockDescriptionPrefix();
        }
        return props.setId(ResourceKey.create(Registries.ITEM, id));
    }

    public static BlockBehaviour.Properties stamp(BlockBehaviour.Properties props) {
        String name = CURRENT.get();
        return name == null ? props
                : props.setId(ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("eca", name)));
    }
}
