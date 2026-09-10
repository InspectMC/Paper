package dev.systemlink.spigot.command;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Normal
class KnockbackCommandTest {
    @Test
    void editsProfilesAndCompletesArguments(final @TempDir Path directory) {
        MSpigotConfig.init(directory.resolve("mSpigot.yml").toFile());
        final CommandSender sender = Mockito.mock(CommandSender.class);
        when(sender.hasPermission("mspigot.command.knockback")).thenReturn(true);
        when(sender.hasPermission("mspigot.command.knockback.edit")).thenReturn(true);
        final KnockbackCommand command = new KnockbackCommand();

        assertEquals(List.of("kb", "riotkb"), command.getAliases());

        assertTrue(command.execute(sender, "knockback", new String[] {"create", "practice"}));
        assertTrue(command.execute(sender, "knockback", new String[] {"set", "practice", "extra-knockback", "0.8"}));
        assertEquals(0.8, MSpigotConfig.get().gameplay().knockback().profiles().get("practice").extraKnockback);
        assertEquals(0.8, YamlConfiguration.loadConfiguration(directory.resolve("knockback.yml").toFile()).getDouble("profiles.practice.extra-knockback"));
        assertFalse(YamlConfiguration.loadConfiguration(directory.resolve("mSpigot.yml").toFile()).contains("gameplay.knockback.profiles.practice"));
        assertEquals(List.of("practice"), command.tabComplete(sender, "knockback", new String[] {"view", "p"}));
        assertTrue(command.execute(sender, "kb", new String[] {"setkb", "practice"}));
        assertEquals("practice", MSpigotConfig.get().gameplay().knockback().defaultProfile());
        assertEquals(List.of("true", "false"), command.tabComplete(sender, "knockback", new String[] {"set", "practice", "enabled", ""}));
    }
}
