package org.bukkit.event.server;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows plugins to compute tab completion results asynchronously.
 * <p>
 * If this event provides completions, then the standard synchronous process
 * will not be fired to populate the results. The synchronous
 * {@link TabCompleteEvent} will still fire with the async results.
 */
public class AsyncTabCompleteEvent extends com.destroystokyo.paper.event.server.AsyncTabCompleteEvent {

    @ApiStatus.Internal
    public AsyncTabCompleteEvent(
        @NotNull final CommandSender sender,
        @NotNull final String buffer,
        final boolean isCommand,
        @Nullable final Location location
    ) {
        super(sender, buffer, isCommand, location);
    }

    @Deprecated
    @ApiStatus.Internal
    public AsyncTabCompleteEvent(
        @NotNull final CommandSender sender,
        @NotNull final List<String> completions,
        @NotNull final String buffer,
        final boolean isCommand,
        @Nullable final Location location
    ) {
        super(sender, completions, buffer, isCommand, location);
    }
}
