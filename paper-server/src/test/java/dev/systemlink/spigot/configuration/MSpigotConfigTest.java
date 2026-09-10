package dev.systemlink.spigot.configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Normal
class MSpigotConfigTest {
    @Test
    void createsDefaultsAndPersistsKnockbackCommands(final @TempDir Path directory) throws IOException {
        final Path file = directory.resolve("mSpigot.yml");
        final Path knockbackFile = directory.resolve("knockback.yml");
        MSpigotConfig.init(file.toFile());

        final MSpigotConfig.Snapshot defaults = MSpigotConfig.get();
        assertTrue(defaults.optimizations().asyncSpawning().enabled());
        assertEquals(2, defaults.optimizations().asyncSpawning().chunkScanIntervalTicks());
        assertTrue(MSpigotConfig.shouldRunNaturalSpawning(2L));
        assertFalse(MSpigotConfig.shouldRunNaturalSpawning(3L));
        assertFalse(defaults.optimizations().asyncPlayerData().enabled());
        assertTrue(defaults.startup().parallelRecipeLoading().enabled());
        assertTrue(defaults.security().nbtProtection().enabled());
        assertFalse(defaults.security().raytraceXray().enabled());
        assertFalse(defaults.security().raytraceXray().fakeOres().enabled());
        assertTrue(defaults.networking().flushConsolidation());
        assertTrue(defaults.networking().asyncCommandPacketBuild());
        assertEquals(MSpigotConfig.TickingMode.VANILLA, defaults.ticking().chunkMode());
        assertFalse(defaults.gameplay().potions().fastPots());
        assertEquals(0.5, defaults.gameplay().potions().speed());
        assertFalse(defaults.gameplay().pearls().talibanPearls());
        assertTrue(defaults.gameplay().tnt().recodedMechanics().enabled());
        assertTrue(defaults.entities().mobAi());
        assertTrue(defaults.worldRuntime().savePlayerData());
        assertTrue(defaults.autosave().staged());
        assertTrue(defaults.chunks().optimizedEntityLookups());
        assertTrue(defaults.general().asyncCatcher());
        assertTrue(defaults.general().styledPluginList());
        assertTrue(defaults.diagnostics().tpsGraph().enabled());
        assertEquals("default", defaults.gameplay().knockback().defaultProfile());
        assertEquals(0.4, defaults.gameplay().knockback().profiles().get("default").baseKnockback);

        MSpigotConfig.createKnockbackProfile("practice", "default");
        assertEquals("0.72", MSpigotConfig.setKnockbackProfileValue("practice", "base-knockback", "0.72"));
        MSpigotConfig.assignKnockbackProfile("arena", "practice");
        MSpigotConfig.reload();

        assertEquals("practice", MSpigotConfig.knockbackProfileName("ARENA"));
        assertEquals(0.72, MSpigotConfig.knockbackProfile("arena").baseKnockback);
        final String yaml = Files.readString(file);
        final String knockbackYaml = Files.readString(knockbackFile);
        assertTrue(yaml.contains("async-spawning:"));
        assertTrue(yaml.contains("chunk-scan-interval-ticks: 2"));
        assertTrue(yaml.contains("async-playerdata:"));
        assertTrue(yaml.contains("parallel-recipe-loading:"));
        assertTrue(yaml.contains("nbt-protection:"));
        assertTrue(yaml.contains("raytrace-xray:"));
        assertTrue(yaml.contains("fake-ores:"));
        assertTrue(yaml.contains("flush-consolidation:"));
        assertTrue(yaml.contains("async-command-packet-build:"));
        assertTrue(yaml.contains("optimized-entity-lookups:"));
        assertTrue(yaml.contains("autosave:"));
        assertTrue(yaml.contains("fast-pots:"));
        assertTrue(yaml.contains("taliban-pearls:"));
        assertTrue(yaml.contains("recoded-mechanics:"));
        assertTrue(yaml.contains("chunk-mode:"));
        assertTrue(yaml.contains("death-screen:"));
        assertTrue(yaml.contains("synchronous-chunks:"));
        assertTrue(yaml.contains("tps-graph:"));
        assertTrue(yaml.contains("world-profiles:"));
        assertFalse(yaml.contains("combat:"));
        assertFalse(yaml.contains("secure-seed:"));
        assertFalse(yaml.contains("theme:"));
        assertFalse(yaml.contains("base-knockback:"));
        assertTrue(knockbackYaml.contains("profiles:"));
        assertTrue(knockbackYaml.contains("practice:"));
        assertTrue(knockbackYaml.contains("base-knockback: 0.72"));

        final YamlConfiguration edited = YamlConfiguration.loadConfiguration(file.toFile());
        edited.set("world-configuration.worlds.arena.sugar-cane.growth-multiplier", 2.5);
        edited.save(file.toFile());
        MSpigotConfig.reload();

        assertEquals(2.5, MSpigotConfig.worldSettings("arena").sugarCane().growthMultiplier());
        assertTrue(MSpigotConfig.worldSettings("unconfigured-world").ores().enabled());
        assertEquals(100.0, MSpigotConfig.get().diagnostics().lagSpike().thresholdMs());
        assertTrue(MSpigotConfig.get().diagnostics().lagSpike().synchronousChunks().enabled());
        assertEquals(25.0, MSpigotConfig.get().diagnostics().lagSpike().synchronousChunks().thresholdMs());
    }

    @Test
    void exposesMSpigotFilesToSparkServerConfigReports(final @TempDir Path directory) {
        final String property = "spark.serverconfigs.extra";
        final String previous = System.getProperty(property);
        try {
            System.setProperty(property, "paper-global-extra.yml");
            final Path file = directory.resolve("custom-mSpigot.yml");

            MSpigotConfig.configureSparkServerConfigs(file.toFile());

            final String extraConfigs = System.getProperty(property);
            assertTrue(extraConfigs.contains("paper-global-extra.yml"));
            assertTrue(extraConfigs.contains(file.toString()));
            assertTrue(extraConfigs.contains(directory.resolve("knockback.yml").toString()));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void migratesLegacyKnockbackProfilesToKnockbackYml(final @TempDir Path directory) throws IOException {
        final Path file = directory.resolve("mSpigot.yml");
        final YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("gameplay.knockback.DefaultProfile", "legacy");
        legacy.set("gameplay.knockback.profiles.legacy.base-knockback", 0.9);
        legacy.set("gameplay.knockback.profiles.legacy.extra-knockback", 0.7);
        legacy.save(file.toFile());

        MSpigotConfig.init(file.toFile());

        assertEquals("legacy", MSpigotConfig.get().gameplay().knockback().defaultProfile());
        assertEquals(0.9, MSpigotConfig.knockbackProfile("world").baseKnockback);
        assertFalse(YamlConfiguration.loadConfiguration(file.toFile()).contains("gameplay.knockback.profiles"));
        assertTrue(YamlConfiguration.loadConfiguration(directory.resolve("knockback.yml").toFile()).contains("profiles.legacy"));
    }
}
