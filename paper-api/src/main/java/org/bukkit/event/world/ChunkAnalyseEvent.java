package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called for mSpigot chunk analysis operations.
 */
public class ChunkAnalyseEvent extends ChunkEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    @ApiStatus.Internal
    public ChunkAnalyseEvent(@NotNull final Chunk chunk) {
        super(chunk);
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
