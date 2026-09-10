package dev.systemlink.spigot.diagnostics;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Normal
class LagSpikeDetectorTest {
    @AfterEach
    void disableDetector() {
        LagSpikeDetector.setEnabled(false);
    }

    @Test
    void samplesSynchronousChunkWaitsOnlyWhenBothSettingsAreEnabled() {
        LagSpikeDetector.configure(settings(false, true));
        assertFalse(LagSpikeDetector.synchronousChunkDetectionEnabled());
        assertEquals(0L, LagSpikeDetector.startSynchronousChunkWait());

        LagSpikeDetector.configure(settings(true, false));
        assertFalse(LagSpikeDetector.synchronousChunkDetectionEnabled());
        assertEquals(0L, LagSpikeDetector.startSynchronousChunkWait());

        LagSpikeDetector.configure(settings(true, true));
        assertTrue(LagSpikeDetector.synchronousChunkDetectionEnabled());
        assertNotEquals(0L, LagSpikeDetector.startSynchronousChunkWait());
        assertEquals(25.0, LagSpikeDetector.synchronousChunkThresholdMillis());
    }

    private static MSpigotConfig.LagSpikeSettings settings(final boolean enabled, final boolean synchronousChunks) {
        return new MSpigotConfig.LagSpikeSettings(
            enabled,
            100.0,
            5,
            new MSpigotConfig.SynchronousChunkSettings(synchronousChunks, 25.0)
        );
    }
}
