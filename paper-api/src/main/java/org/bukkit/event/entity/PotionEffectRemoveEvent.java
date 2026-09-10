package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a potion effect is removed from a living entity.
 */
public class PotionEffectRemoveEvent extends PotionEffectEvent {

    @ApiStatus.Internal
    public PotionEffectRemoveEvent(@NotNull final LivingEntity entity, @NotNull final org.bukkit.potion.PotionEffect effect) {
        super(entity, effect);
    }
}
