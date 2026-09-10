package dev.systemlink.spigot.autosave;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

/**
 * Stages full-save metadata work across ticks.
 *
 * <p>Chunk data remains owned by Paper's incremental Moonrise autosaver. This
 * coordinator spreads global data and one world's metadata/event work per tick
 * instead of collecting every world into the periodic full-save tick.</p>
 */
@NullMarked
public final class AutoSave {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MinecraftServer server;
    private final Queue<ResourceKey<Level>> levels = new ArrayDeque<>();
    private AutoSaveStep step = AutoSaveStep.IDLE;
    private long startNanos;
    private int savedWorlds;

    public AutoSave(final MinecraftServer server) {
        this.server = server;
    }

    public void start(final Iterable<ServerLevel> worlds) {
        if (this.isActive()) {
            return;
        }
        this.levels.clear();
        for (final ServerLevel level : worlds) {
            if (level.paperConfig().chunks.autoSaveInterval.value() <= 0) {
                continue;
            }
            this.levels.add(level.dimension());
            level.getAutoSaveWorldData().setLastAutosaveTimeStamp();
        }
        this.savedWorlds = 0;
        this.startNanos = System.nanoTime();
        this.step = AutoSaveStep.SAVE_GLOBAL_DATA;
    }

    /** Runs at most one global or per-world save unit. */
    public boolean execute() {
        switch (this.step) {
            case IDLE -> {
                return false;
            }
            case SAVE_GLOBAL_DATA -> {
                this.server.saveGlobalData(false);
                this.step = this.levels.isEmpty() ? AutoSaveStep.FINISHED : AutoSaveStep.SAVE_LEVEL;
            }
            case SAVE_LEVEL -> {
                final ResourceKey<Level> dimension = this.levels.poll();
                if (dimension != null) {
                    final ServerLevel level = this.server.getLevel(dimension);
                    if (level != null) {
                        final long started = System.nanoTime();
                        level.saveIncrementally(true);
                        level.getAutoSaveWorldData().complete(System.nanoTime() - started);
                        this.savedWorlds++;
                    }
                }
                if (this.levels.isEmpty()) {
                    this.step = AutoSaveStep.FINISHED;
                }
            }
            case FINISHED -> {
                if (shouldLogCompletion()) {
                    LOGGER.info("[Autosave] Saved metadata for {} world(s) in {} ms", this.savedWorlds, formatMillis(System.nanoTime() - this.startNanos));
                }
                this.reset();
                return true;
            }
        }
        return false;
    }

    public boolean isActive() {
        return this.step != AutoSaveStep.IDLE;
    }

    public AutoSaveStep step() {
        return this.step;
    }

    public void reset() {
        this.levels.clear();
        this.step = AutoSaveStep.IDLE;
        this.startNanos = 0L;
        this.savedWorlds = 0;
    }

    private static boolean shouldLogCompletion() {
        final dev.systemlink.spigot.configuration.MSpigotConfig.Snapshot snapshot =
            dev.systemlink.spigot.configuration.MSpigotConfig.current();
        return snapshot != null && snapshot.autosave().logCompletion();
    }

    private static String formatMillis(final long durationNanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0);
    }

    public enum AutoSaveStep {
        IDLE,
        SAVE_GLOBAL_DATA,
        SAVE_LEVEL,
        FINISHED
    }
}
