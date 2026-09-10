package dev.systemlink.spigot.autosave;

import net.minecraft.server.level.ServerLevel;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Normal
class AutoSaveWorldDataTest {
    @Test
    void resetsAndTracksTheCurrentSaveCycle() {
        final ServerLevel level = mock(ServerLevel.class);
        when(level.getGameTime()).thenReturn(42L);
        final AutoSaveWorldData data = new AutoSaveWorldData(level);

        data.addAutoSaveChunkCount(3);
        data.setLastAutosaveTimeStamp();
        assertEquals(42L, data.getLastAutosaveTimeStamp());
        assertEquals(0, data.getAutoSaveChunkCount());

        data.addAutoSaveChunkCount(2);
        data.complete(100L);
        assertEquals(2, data.getAutoSaveChunkCount());
        assertEquals(100L, data.getLastSaveDurationNanos());
        assertThrows(IllegalArgumentException.class, () -> data.addAutoSaveChunkCount(-1));
    }
}
