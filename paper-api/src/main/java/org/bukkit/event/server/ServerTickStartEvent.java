package org.bukkit.event.server;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called immediately before the server starts processing a tick.
 */
public class ServerTickStartEvent extends ServerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final int tickNumber;

    @ApiStatus.Internal
    public ServerTickStartEvent(final int tickNumber) {
        this.tickNumber = tickNumber;
    }

    /**
     * Gets the tick number that is about to be processed.
     *
     * @return the one-based server tick number
     */
    public int getTickNumber() {
        return this.tickNumber;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
