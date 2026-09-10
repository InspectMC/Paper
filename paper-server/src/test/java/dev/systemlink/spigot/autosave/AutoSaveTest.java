package dev.systemlink.spigot.autosave;

import io.papermc.paper.configuration.WorldConfiguration;
import io.papermc.paper.configuration.type.fallback.AutosavePeriod;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Normal
class AutoSaveTest {
    @Test
    void savesGlobalDataThenOneWorldPerTick() {
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel first = level(Level.OVERWORLD);
        final ServerLevel second = level(Level.NETHER);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(first);
        when(server.getLevel(Level.NETHER)).thenReturn(second);
        final AutoSave autoSave = new AutoSave(server);

        autoSave.start(List.of(first, second));
        assertEquals(AutoSave.AutoSaveStep.SAVE_GLOBAL_DATA, autoSave.step());

        assertFalse(autoSave.execute());
        verify(server).saveGlobalData(false);
        assertEquals(AutoSave.AutoSaveStep.SAVE_LEVEL, autoSave.step());

        assertFalse(autoSave.execute());
        verify(first).saveIncrementally(true);
        verify(first.getAutoSaveWorldData()).complete(anyLong());

        assertFalse(autoSave.execute());
        verify(second).saveIncrementally(true);
        assertEquals(AutoSave.AutoSaveStep.FINISHED, autoSave.step());

        assertTrue(autoSave.execute());
        assertFalse(autoSave.isActive());
    }

    private static ServerLevel level(final ResourceKey<Level> dimension) {
        final ServerLevel level = mock(ServerLevel.class);
        final WorldConfiguration configuration = mock(WorldConfiguration.class);
        configuration.chunks = mock(WorldConfiguration.Chunks.class);
        configuration.chunks.autoSaveInterval = mock(AutosavePeriod.class);
        when(configuration.chunks.autoSaveInterval.value()).thenReturn(6000);
        when(level.dimension()).thenReturn(dimension);
        when(level.paperConfig()).thenReturn(configuration);
        when(level.getAutoSaveWorldData()).thenReturn(mock(AutoSaveWorldData.class));
        return level;
    }
}
