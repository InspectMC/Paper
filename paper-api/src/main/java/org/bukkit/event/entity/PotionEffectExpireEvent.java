package org.bukkit.event.entity;

import com.google.common.base.Preconditions;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a potion effect expires on a living entity.
 */
public class PotionEffectExpireEvent extends PotionEffectRemoveEvent {

    private int duration;

    @ApiStatus.Internal
    public PotionEffectExpireEvent(
        @NotNull final LivingEntity entity,
        @NotNull final org.bukkit.potion.PotionEffect effect,
        final int duration
    ) {
        super(entity, effect);
        this.duration = duration;
    }

    /**
     * Gets the remaining duration for the expiring effect, in ticks.
     *
     * @return the remaining duration
     */
    public int getDuration() {
        return this.duration;
    }

    /**
     * Sets the remaining duration for the effect, in ticks.
     * <p>
     * Values greater than {@code 0} keep the effect active.
     *
     * @param duration the remaining duration
     */
    public void setDuration(final int duration) {
        Preconditions.checkArgument(duration >= 0, "duration cannot be negative");
        this.duration = duration;
    }
}
