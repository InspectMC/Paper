package dev.systemlink.spigot.diagnostics;

import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.event.server.LagSpikeTriggerEvent;
import org.slf4j.Logger;

/** Records and rate-limits warnings for slow server ticks. */
public final class LagSpikeDetector {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ENABLED = new AtomicBoolean();
    private static final AtomicLong LAST_WARNING_NANOS = new AtomicLong(Long.MIN_VALUE);

    private static volatile long thresholdNanos = TimeUnit.MILLISECONDS.toNanos(100);
    private static volatile long cooldownNanos = TimeUnit.SECONDS.toNanos(5);

    private LagSpikeDetector() {
    }

    public static void configure(final MSpigotConfig.LagSpikeSettings settings) {
        thresholdNanos = (long) (settings.thresholdMs() * 1_000_000.0);
        cooldownNanos = TimeUnit.SECONDS.toNanos(settings.logCooldownSeconds());
        ENABLED.set(settings.enabled());
        LAST_WARNING_NANOS.set(Long.MIN_VALUE);
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static void setEnabled(final boolean enabled) {
        ENABLED.set(enabled);
        LAST_WARNING_NANOS.set(Long.MIN_VALUE);
    }

    public static double thresholdMillis() {
        return thresholdNanos / 1_000_000.0;
    }

    public static void recordTick(final int tick, final long durationNanos) {
        if (!ENABLED.get() || durationNanos < thresholdNanos) {
            return;
        }

        final long now = System.nanoTime();
        final long previous = LAST_WARNING_NANOS.get();
        if (previous != Long.MIN_VALUE && now - previous < cooldownNanos) {
            return;
        }
        if (!LAST_WARNING_NANOS.compareAndSet(previous, now)) {
            return;
        }

        LOGGER.warn("[mSpigot LagSpike] Tick {} took {} ms (threshold: {} ms)",
            tick,
            String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0),
            String.format(java.util.Locale.ROOT, "%.2f", thresholdMillis()));
        new LagSpikeTriggerEvent(
            System.currentTimeMillis(),
            durationNanos,
            java.util.List.of("Tick " + tick + " took " + String.format(java.util.Locale.ROOT, "%.2f", durationNanos / 1_000_000.0) + " ms")
        ).callEvent();
    }
}
