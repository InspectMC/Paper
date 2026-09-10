package dev.systemlink.spigot;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Normal
class PlayerMapTest {
    @Test
    void usesMoonriseCandidatesForBoundedLookups(final @TempDir Path directory) {
        MSpigotConfig.init(directory.resolve("mSpigot.yml").toFile());
        final ServerLevel level = mock(ServerLevel.class);
        final NearbyPlayers nearbyPlayers = mock(NearbyPlayers.class);
        final ReferenceList<ServerPlayer> candidates = new ReferenceList<>();
        final ServerPlayer player = mock(ServerPlayer.class);
        candidates.add(player);
        when(level.moonrise$getNearbyPlayers()).thenReturn(nearbyPlayers);
        when(nearbyPlayers.getPlayersByBlock(0, 0, NearbyPlayers.NearbyMapType.GENERAL_REALLY_SMALL)).thenReturn(candidates);

        final PlayerMap playerMap = new PlayerMap(level);
        assertSame(candidates, playerMap.candidates(0.0, 0.0, 48.0));
        assertSame(player, playerMap.getNearestPlayer(0.0, 0.0, 0.0, 48.0, ignored -> true));
    }

    @Test
    void fallsBackToTheWorldListForUnboundedLookups() {
        final ServerLevel level = mock(ServerLevel.class);
        final List<ServerPlayer> players = List.of(mock(ServerPlayer.class));
        when(level.players()).thenReturn(players);

        assertSame(players, new PlayerMap(level).candidates(0.0, 0.0, -1.0));
    }
}
