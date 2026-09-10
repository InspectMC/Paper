package org.bukkit.event.player;

import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an ender pearl launched by a player is about to be refunded.
 */
public class PlayerPearlRefundEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final EnderPearl enderPearl;
    private boolean cancelled;

    @ApiStatus.Internal
    public PlayerPearlRefundEvent(@NotNull final Player player, @NotNull final EnderPearl enderPearl) {
        super(player);
        this.enderPearl = enderPearl;
    }

    /**
     * Gets the ender pearl that is being refunded.
     *
     * @return the refunded ender pearl
     */
    @NotNull
    public EnderPearl getEnderPearl() {
        return this.enderPearl;
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
