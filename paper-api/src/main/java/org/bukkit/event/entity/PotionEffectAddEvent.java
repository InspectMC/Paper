package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a potion effect is added to a living entity.
 */
public class PotionEffectAddEvent extends PotionEffectEvent {

    private final EffectCause cause;

    @ApiStatus.Internal
    public PotionEffectAddEvent(
        @NotNull final LivingEntity entity,
        @NotNull final org.bukkit.potion.PotionEffect effect,
        @NotNull final EffectCause cause
    ) {
        super(entity, effect);
        this.cause = cause;
    }

    /**
     * Gets the reason the potion effect is being added.
     *
     * @return the add cause
     */
    @NotNull
    public EffectCause getCause() {
        return this.cause;
    }

    /**
     * Represents the source of a potion effect addition.
     */
    public enum EffectCause {
        /**
         * A splash potion applied the effect.
         */
        POTION_SPLASH,
        /**
         * A beacon applied the effect.
         */
        BEACON,
        /**
         * A wither skeleton applied the effect.
         */
        WITHER_SKELETON,
        /**
         * A wither skull applied the effect.
         */
        WITHER_SKULL,
        /**
         * A plugin applied the effect.
         */
        PLUGIN,
        /**
         * The effect source is unknown.
         */
        UNKNOWN,
        /**
         * A cave spider applied the effect.
         */
        CAVE_SPIDER,
        /**
         * A villager trade applied the effect.
         */
        VILLAGE_TRADE,
        /**
         * A zombie villager started converting.
         */
        ZOMBIE_CONVERTING,
        /**
         * A zombie villager finished converting.
         */
        ZOMBIE_CONVERTED
    }
}
