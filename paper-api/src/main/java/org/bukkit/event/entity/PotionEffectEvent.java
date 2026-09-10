package org.bukkit.event.entity;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a potion effect is changed on a living entity.
 */
public class PotionEffectEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final org.bukkit.potion.PotionEffect effect;
    private boolean cancelled;

    @ApiStatus.Internal
    public PotionEffectEvent(@NotNull final LivingEntity entity, @NotNull final org.bukkit.potion.PotionEffect effect) {
        super(entity);
        this.effect = effect;
    }

    @NotNull
    @Override
    public LivingEntity getEntity() {
        return (LivingEntity) super.getEntity();
    }

    /**
     * Gets the potion effect being changed.
     *
     * @return the potion effect
     */
    @NotNull
    public org.bukkit.potion.PotionEffect getEffect() {
        return this.effect;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
