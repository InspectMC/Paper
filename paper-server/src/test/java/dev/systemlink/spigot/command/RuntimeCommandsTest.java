package dev.systemlink.spigot.command;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import dev.systemlink.spigot.diagnostics.LagSpikeDetector;
import dev.systemlink.spigot.entity.MobAIController;
import java.nio.file.Path;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Normal
class RuntimeCommandsTest {
    @Test
    void controlsRuntimeFeatures(final @TempDir Path directory) {
        MSpigotConfig.init(directory.resolve("mSpigot.yml").toFile());
        final CommandSender sender = Mockito.mock(CommandSender.class);
        when(sender.hasPermission(Mockito.anyString())).thenReturn(true);

        final LagSpikeCommand lagSpike = new LagSpikeCommand();
        assertTrue(lagSpike.execute(sender, "lagspike", new String[] {"enable"}));
        assertTrue(LagSpikeDetector.enabled());
        assertTrue(lagSpike.execute(sender, "lagspike", new String[] {"disable"}));
        assertFalse(LagSpikeDetector.enabled());

        MobAIController.setEnabled(true);
        assertTrue(new MobAICommand().execute(sender, "mobai", new String[0]));
        assertFalse(MobAIController.enabled());
        MobAIController.setEnabled(true);

        assertTrue(new SetMaxSlotCommand().execute(sender, "setmaxslot", new String[] {"64"}));
    }

    @Test
    void reportsTheExecutingPlayersPing() {
        final Player player = Mockito.mock(Player.class);
        when(player.hasPermission("mspigot.command.ping")).thenReturn(true);
        when(player.getPing()).thenReturn(42);

        assertTrue(new PingCommand().execute(player, "ping", new String[0]));
        Mockito.verify(player, Mockito.atLeastOnce()).getPing();
    }
}
