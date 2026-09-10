package dev.systemlink.spigot.diagnostics;

import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.event.server.LagSpikeTriggerEvent;
import org.slf4j.Logger;

/** Records and rate-limits warnings for slow server ticks and synchronous chunk waits. */
public final class LagSpikeDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ENABLED = new AtomicBoolean();
    private static final AtomicLong LAST_WARNING_NANOS = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong LAST_SYNCHRONOUS_CHUNK_WARNING_NANOS = new AtomicLong(Long.MIN_VALUE);
    private static final AtomicLong SUPPRESSED_SYNCHRONOUS_CHUNK_WARNINGS = new AtomicLong();

    private static volatile long thresholdNanos = TimeUnit.MILLISECONDS.toNanos(100);
    private static volatile long cooldownNanos = TimeUnit.SECONDS.toNanos(5);
    private static volatile boolean synchronousChunkDetectionEnabled = true;
    private static volatile long synchronousChunkThresholdNanos = TimeUnit.MILLISECONDS.toNanos(25);

    private LagSpikeDetector() {
    }

    public static void configure(final MSpigotConfig.LagSpikeSettings settings) {
        thresholdNanos = (long) (settings.thresholdMs() * 1_000_000.0);
        cooldownNanos = TimeUnit.SECONDS.toNanos(settings.logCooldownSeconds());
        synchronousChunkDetectionEnabled = settings.synchronousChunks().enabled();
        synchronousChunkThresholdNanos = (long) (settings.synchronousChunks().thresholdMs() * 1_000_000.0);
        ENABLED.set(settings.enabled());
        resetWarningState();
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static void setEnabled(final boolean enabled) {
        ENABLED.set(enabled);
        resetWarningState();
    }

    public static double thresholdMillis() {
        return thresholdNanos / 1_000_000.0;
    }

    public static boolean synchronousChunkDetectionEnabled() {
        return ENABLED.get() && synchronousChunkDetectionEnabled;
    }

    public static double synchronousChunkThresholdMillis() {
        return synchronousChunkThresholdNanos / 1_000_000.0;
    }

    /**
     * Starts timing an actual synchronous chunk wait.
     *
     * @return a monotonic timestamp, or {@code 0} when sampling is disabled
     */
    public static long startSynchronousChunkWait() {
        return synchronousChunkDetectionEnabled() ? System.nanoTime() : 0L;
    }

    /** Reports a completed synchronous chunk wait if it exceeded the configured threshold. */
    public static void recordSynchronousChunkWait(
        final ServerLevel level,
        final int chunkX,
        final int chunkZ,
        final ChunkStatus targetStatus,
        final long startedNanos
    ) {
        if (startedNanos == 0L || !synchronousChunkDetectionEnabled()) {
            return;
        }

        final long durationNanos = System.nanoTime() - startedNanos;
        if (durationNanos < synchronousChunkThresholdNanos) {
            return;
        }

        final long now = System.nanoTime();
        if (!claimWarning(LAST_SYNCHRONOUS_CHUNK_WARNING_NANOS, now)) {
            SUPPRESSED_SYNCHRONOUS_CHUNK_WARNINGS.incrementAndGet();
            return;
        }

        final long suppressed = SUPPRESSED_SYNCHRONOUS_CHUNK_WARNINGS.getAndSet(0L);
        final String summary = "Synchronous chunk load/generation in world '" + WorldUtil.getWorldName(level)
            + "' at [" + chunkX + ", " + chunkZ + "] to status '" + targetStatus
            + "' blocked thread '" + Thread.currentThread().getName() + "' for " + formatMillis(durationNanos)
            + " ms (threshold: " + formatMillis(synchronousChunkThresholdNanos) + " ms)"
            + (suppressed == 0L ? "" : "; " + suppressed + " similar warning(s) were suppressed");
        final Throwable callSite = new Throwable("Synchronous chunk access call site (diagnostic stack trace, not a crash)");
        LOGGER.warn("[mSpigot LagSpike] " + summary, callSite);

        if (level.getServer().isSameThread()) {
            new LagSpikeTriggerEvent(System.currentTimeMillis(), durationNanos, java.util.List.of(summary)).callEvent();
        }
    }

    public static void recordTick(final int tick, final long durationNanos) {
        if (!ENABLED.get() || durationNanos < thresholdNanos) {
            return;
        }

        if (!claimWarning(LAST_WARNING_NANOS, System.nanoTime())) {
            return;
        }

        LOGGER.warn("[mSpigot LagSpike] Tick {} took {} ms (threshold: {} ms)",
            tick,
            formatMillis(durationNanos),
            formatMillis(thresholdNanos));
        new LagSpikeTriggerEvent(
            System.currentTimeMillis(),
            durationNanos,
            java.util.List.of("Tick " + tick + " took " + formatMillis(durationNanos) + " ms")
        ).callEvent();
    }

    private static boolean claimWarning(final AtomicLong warningClock, final long now) {
        while (true) {
            final long previous = warningClock.get();
            if (previous != Long.MIN_VALUE && now - previous < cooldownNanos) {
                return false;
            }
            if (warningClock.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private static void resetWarningState() {
        LAST_WARNING_NANOS.set(Long.MIN_VALUE);
        LAST_SYNCHRONOUS_CHUNK_WARNING_NANOS.set(Long.MIN_VALUE);
        SUPPRESSED_SYNCHRONOUS_CHUNK_WARNINGS.set(0L);
    }

    private static String formatMillis(final long durationNanos) {
        return String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0);
    }
}
