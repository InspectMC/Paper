package org.bukkit.event.world;

import org.bukkit.Chunk;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an mSpigot chunk analysis operation starts.
 */
public class ChunkAnalyseStartEvent extends ChunkAnalyseEvent {

    @ApiStatus.Internal
    public ChunkAnalyseStartEvent(@NotNull final Chunk chunk) {
        super(chunk);
    }
}
