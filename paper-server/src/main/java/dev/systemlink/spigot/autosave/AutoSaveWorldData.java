package dev.systemlink.spigot.autosave;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NullMarked;

/** Tracks the most recent staged autosave for a world. */
@NullMarked
public final class AutoSaveWorldData {
    private final ServerLevel level;
    private long lastAutoSaveTimeStamp;
    private int autoSaveChunkCount;
    private long lastSaveDurationNanos;

    public AutoSaveWorldData(final ServerLevel level) {
        this.level = level;
    }

    public void setLastAutosaveTimeStamp() {
        this.lastAutoSaveTimeStamp = this.level.getGameTime();
        this.autoSaveChunkCount = 0;
    }

    public long getLastAutosaveTimeStamp() {
        return this.lastAutoSaveTimeStamp;
    }

    public void addAutoSaveChunkCount(final int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Autosave chunk count cannot be negative");
        }
        this.autoSaveChunkCount += count;
    }

    public int getAutoSaveChunkCount() {
        return this.autoSaveChunkCount;
    }

    public long getLastSaveDurationNanos() {
        return this.lastSaveDurationNanos;
    }

    void complete(final long durationNanos) {
        this.lastSaveDurationNanos = Math.max(0L, durationNanos);
    }
}
