package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an existing potion effect is extended or replaced.
 */
public class PotionEffectExtendEvent extends PotionEffectAddEvent {

    private final org.bukkit.potion.PotionEffect oldEffect;

    @ApiStatus.Internal
    public PotionEffectExtendEvent(
        @NotNull final LivingEntity entity,
        @NotNull final org.bukkit.potion.PotionEffect oldEffect,
        @NotNull final org.bukkit.potion.PotionEffect effect,
        @NotNull final EffectCause cause
    ) {
        super(entity, effect, cause);
        this.oldEffect = oldEffect;
    }

    /**
     * Gets the potion effect already present on the entity.
     *
     * @return the old potion effect
     */
    @NotNull
    public org.bukkit.potion.PotionEffect getOldEffect() {
        return this.oldEffect;
    }
}
