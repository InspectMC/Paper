package org.bukkit.event.server;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called after the server finishes processing a tick.
 */
public class ServerTickEndEvent extends ServerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final int tickNumber;
    private final double tickDuration;
    private final long timeEnd;

    @ApiStatus.Internal
    public ServerTickEndEvent(final int tickNumber, final double tickDuration, final long timeRemaining) {
        this.tickNumber = tickNumber;
        this.tickDuration = tickDuration;
        this.timeEnd = System.nanoTime() + timeRemaining;
    }

    /**
     * Gets the tick number that just finished processing.
     *
     * @return the one-based server tick number
     */
    public int getTickNumber() {
        return this.tickNumber;
    }

    /**
     * Gets how long the completed tick took.
     *
     * @return tick duration in milliseconds
     */
    public double getTickDuration() {
        return this.tickDuration;
    }

    /**
     * Gets the continuously updated remaining time before the next tick should
     * begin. Negative values mean the server exceeded the tick budget.
     *
     * @return remaining time in nanoseconds
     */
    public long getTimeRemaining() {
        return this.timeEnd - System.nanoTime();
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
