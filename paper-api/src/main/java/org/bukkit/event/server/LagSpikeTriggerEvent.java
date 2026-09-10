package org.bukkit.event.server;

import java.util.List;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Called when the mSpigot lag-spike detector triggers an alert.
 */
public class LagSpikeTriggerEvent extends ServerEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final long timestamp;
    private final long fullServerTotal;
    private final List<String> entries;

    @ApiStatus.Internal
    public LagSpikeTriggerEvent(final long timestamp, final long fullServerTotal, @NotNull final List<String> entries) {
        this.timestamp = timestamp;
        this.fullServerTotal = fullServerTotal;
        this.entries = List.copyOf(entries);
    }

    /**
     * Gets the wall-clock timestamp, in milliseconds since the epoch.
     *
     * @return the trigger timestamp
     */
    public long getTimestamp() {
        return this.timestamp;
    }

    /**
     * Gets the duration of the operation that triggered the detector, in nanoseconds.
     * For a tick trigger this is the full server tick duration; for a synchronous
     * chunk trigger this is the time that the calling thread was blocked.
     *
     * @return the triggering operation's duration
     */
    public long getFullServerTotal() {
        return this.fullServerTotal;
    }

    /**
     * Gets the lag-spike entries that were reported.
     *
     * @return the lag-spike entries
     */
    @NotNull
    @Unmodifiable
    public List<String> getEntries() {
        return this.entries;
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
