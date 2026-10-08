package net.eca.mixin;

import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.function.Predicate;

@Mixin(EntitySelector.class)
public interface EntitySelectorAccessor {

    @Accessor("contextFreePredicates")
    List<Predicate<Entity>> eca$getContextFreePredicates();
}
