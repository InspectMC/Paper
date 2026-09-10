package dev.systemlink.spigot.configuration;

import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.command.KnockbackCommand;
import dev.systemlink.spigot.command.LagSpikeCommand;
import dev.systemlink.spigot.command.MSpigotCommand;
import dev.systemlink.spigot.command.MobAICommand;
import dev.systemlink.spigot.command.PingCommand;
import dev.systemlink.spigot.command.PotSpeedCommand;
import dev.systemlink.spigot.command.SetMaxSlotCommand;
import dev.systemlink.spigot.diagnostics.LagSpikeDetector;
import dev.systemlink.spigot.entity.MobAIController;
import dev.systemlink.spigot.knockback.AdvancedKnockbackConfiguration;
import io.papermc.paper.configuration.type.number.DoubleOr;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * Loads and owns the standalone {@code mSpigot.yml} configuration and the companion {@code knockback.yml} profiles file.
 *
 * <p>The immutable snapshot is replaced atomically on reload, so hot-path
 * feature checks do not touch Bukkit's mutable YAML representation.</p>
 */
public final class MSpigotConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern PROFILE_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Object LOCK = new Object();
    private static final AdvancedKnockbackConfiguration FALLBACK_PROFILE = new AdvancedKnockbackConfiguration();
    private static final String SPARK_EXTRA_CONFIGS_PROPERTY = "spark.serverconfigs.extra";

    private static volatile Snapshot snapshot;
    private static File configFile;
    private static File knockbackConfigFile;
    private static YamlConfiguration config;
    private static YamlConfiguration knockbackConfig;

    private MSpigotConfig() {
    }

    public static void init(final File file) {
        synchronized (LOCK) {
            configFile = file;
            knockbackConfigFile = siblingFile(file, "knockback.yml");
            configureSparkServerConfigs(file);
            loadFromDisk();
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            ensureInitialized();
            loadFromDisk();
        }
    }

    public static void registerCommands() {
        MinecraftServer.getServer().server.getCommandMap().register("knockback", "mSpigot", new KnockbackCommand());
        MinecraftServer.getServer().server.getCommandMap().register("mspigot", "mSpigot", new MSpigotCommand());
        MinecraftServer.getServer().server.getCommandMap().register("lagspike", "mSpigot", new LagSpikeCommand());
        MinecraftServer.getServer().server.getCommandMap().register("setmaxslot", "mSpigot", new SetMaxSlotCommand());
        MinecraftServer.getServer().server.getCommandMap().register("ping", "mSpigot", new PingCommand());
        MinecraftServer.getServer().server.getCommandMap().register("mobai", "mSpigot", new MobAICommand());
        MinecraftServer.getServer().server.getCommandMap().register("potspeed", "mSpigot", new PotSpeedCommand());
    }

    public static Snapshot get() {
        final Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("mSpigot.yml has not been initialized");
        }
        return current;
    }

    public static Snapshot current() {
        return snapshot;
    }

    public static boolean parallelRecipeLoadingEnabled(final int recipeCount) {
        final Snapshot current = snapshot;
        return current != null
            && current.startup().parallelRecipeLoading().enabled()
            && recipeCount >= current.startup().parallelRecipeLoading().minimumRecipes()
            && parallelRecipeLoadingThreads() > 1;
    }

    public static int parallelRecipeLoadingThreads() {
        final Snapshot current = snapshot;
        if (current == null) {
            return 1;
        }
        final int configured = current.startup().parallelRecipeLoading().threads();
        if (configured > 0) {
            return configured;
        }
        return Math.max(2, Runtime.getRuntime().availableProcessors());
    }

    public static boolean asyncPlayerDataEnabled() {
        final Snapshot current = snapshot;
        return current != null && current.optimizations().asyncPlayerData().enabled();
    }

    public static boolean asyncSpawningEnabled() {
        final Snapshot current = snapshot;
        return current != null && current.optimizations().asyncSpawning().enabled();
    }

    public static boolean shouldRunNaturalSpawning(final long gameTime) {
        final Snapshot current = snapshot;
        if (current == null || !current.optimizations().asyncSpawning().enabled()) {
            return true;
        }
        return Math.floorMod(gameTime, current.optimizations().asyncSpawning().chunkScanIntervalTicks()) == 0L;
    }

    public static boolean shouldRunSpawningChunkScan(final long gameTime) {
        return shouldRunNaturalSpawning(gameTime);
    }

    public static boolean readNbtFromPlacedBlocks() {
        final Snapshot current = snapshot;
        return current == null || current.security().nbtProtection().readNbtFromPlacedBlocks();
    }

    public static boolean chunkTickingEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.ticking().chunkTicking();
    }

    public static boolean weatherTickingEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.ticking().weatherTicking();
    }

    public static boolean villagerTickingEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.ticking().villagerTicking();
    }

    public static boolean playerMoveEventEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.general().playerMoveEvent();
    }

    public static boolean asyncCommandPacketBuildEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.networking().asyncCommandPacketBuild();
    }

    public static boolean styledPluginListEnabled() {
        final Snapshot current = snapshot;
        return current == null || current.general().styledPluginList();
    }

    public static boolean optimizedHitDetectionEnabled() {
        final Snapshot current = snapshot;
        return current != null && current.general().optimizedHitDetection();
    }

    public static float potionShootPower() {
        final Snapshot current = snapshot;
        if (current == null || !current.gameplay().potions().fastPots()) {
            return 0.5F;
        }
        return (float) current.gameplay().potions().speed();
    }

    public static void setPotionSpeed(final double speed) {
        synchronized (LOCK) {
            ensureInitialized();
            if (!Double.isFinite(speed) || speed <= 0.0 || speed > 5.0) {
                throw new IllegalArgumentException("Potion speed must be greater than 0 and at most 5");
            }
            config.set("gameplay.potions.fast-pots.enabled", true);
            config.set("gameplay.potions.fast-pots.speed", speed);
            saveAndRefresh();
        }
    }

    public static void setFastPotsEnabled(final boolean enabled) {
        synchronized (LOCK) {
            ensureInitialized();
            config.set("gameplay.potions.fast-pots.enabled", enabled);
            saveAndRefresh();
        }
    }

    public static void setAsyncPotsEnabled(final boolean enabled) {
        synchronized (LOCK) {
            ensureInitialized();
            config.set("gameplay.potions.async-pots.enabled", enabled);
            saveAndRefresh();
        }
    }

    public static AdvancedKnockbackConfiguration knockbackProfile(final String worldName) {
        final Snapshot current = snapshot;
        if (current == null) {
            return FALLBACK_PROFILE;
        }
        final KnockbackSettings settings = current.gameplay().knockback();
        final String selected = settings.worldProfiles().getOrDefault(worldName.toLowerCase(Locale.ROOT), settings.defaultProfile());
        return settings.profiles().getOrDefault(selected, settings.profiles().getOrDefault(settings.defaultProfile(), FALLBACK_PROFILE));
    }

    public static WorldSettings worldSettings(final String worldName) {
        final Snapshot current = get();
        return current.worldConfiguration().worlds().getOrDefault(worldName.toLowerCase(Locale.ROOT), current.worldConfiguration().defaults());
    }

    public static String knockbackProfileName(final String worldName) {
        final KnockbackSettings settings = get().gameplay().knockback();
        return settings.worldProfiles().getOrDefault(worldName.toLowerCase(Locale.ROOT), settings.defaultProfile());
    }

    public static Set<String> knockbackProfileNames() {
        return get().gameplay().knockback().profiles().keySet();
    }

    public static void createKnockbackProfile(final String requestedName, final String requestedSource) {
        synchronized (LOCK) {
            final String name = normalizeProfileName(requestedName);
            final String source = normalizeProfileName(requestedSource);
            final KnockbackSettings settings = get().gameplay().knockback();
            if (settings.profiles().containsKey(name)) {
                throw new IllegalArgumentException("A knockback profile named '" + name + "' already exists");
            }
            final AdvancedKnockbackConfiguration sourceProfile = settings.profiles().get(source);
            if (sourceProfile == null) {
                throw new IllegalArgumentException("Unknown source profile '" + source + "'");
            }
            writeProfile(name, sourceProfile);
            saveAndRefresh();
        }
    }

    public static void deleteKnockbackProfile(final String requestedName) {
        synchronized (LOCK) {
            final String name = normalizeProfileName(requestedName);
            final KnockbackSettings settings = get().gameplay().knockback();
            if (!settings.profiles().containsKey(name)) {
                throw new IllegalArgumentException("Unknown knockback profile '" + name + "'");
            }
            if (settings.defaultProfile().equals(name)) {
                throw new IllegalArgumentException("The default knockback profile cannot be deleted");
            }
            knockbackConfig.set(profilePath(name), null);
            final Map<String, String> assignments = new LinkedHashMap<>(settings.worldProfiles());
            assignments.values().removeIf(name::equals);
            config.set("gameplay.knockback.world-profiles", assignments);
            saveAndRefresh();
        }
    }

    public static void setDefaultKnockbackProfile(final String requestedName) {
        synchronized (LOCK) {
            final String name = requireExistingProfile(requestedName);
            config.set("gameplay.knockback.DefaultProfile", name);
            saveAndRefresh();
        }
    }

    public static void assignKnockbackProfile(final String worldName, final String requestedName) {
        synchronized (LOCK) {
            if (worldName.isBlank()) {
                throw new IllegalArgumentException("World name cannot be blank");
            }
            final String name = requireExistingProfile(requestedName);
            final Map<String, String> assignments = new LinkedHashMap<>(get().gameplay().knockback().worldProfiles());
            assignments.put(worldName.toLowerCase(Locale.ROOT), name);
            config.set("gameplay.knockback.world-profiles", assignments);
            saveAndRefresh();
        }
    }

    public static String setKnockbackProfileValue(final String requestedProfile, final String requestedSetting, final String value) {
        synchronized (LOCK) {
            final String profile = requireExistingProfile(requestedProfile);
            final KnockbackSetting setting = KnockbackSetting.parse(requestedSetting);
            final Object parsed = setting.parseValue(value);
            knockbackConfig.set(profilePath(profile) + "." + setting.path(), parsed);
            saveAndRefresh();
            return setting.display(parsed);
        }
    }

    private static String requireExistingProfile(final String requestedName) {
        final String name = normalizeProfileName(requestedName);
        if (!get().gameplay().knockback().profiles().containsKey(name)) {
            throw new IllegalArgumentException("Unknown knockback profile '" + name + "'");
        }
        return name;
    }

    private static String normalizeProfileName(final String requestedName) {
        final String name = requestedName.toLowerCase(Locale.ROOT);
        if (!PROFILE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Profile names may only contain lowercase letters, numbers, '_' and '-'");
        }
        return name;
    }

    private static File siblingFile(final File file, final String name) {
        final File absolute = file.getAbsoluteFile();
        final File parent = absolute.getParentFile();
        return parent == null ? new File(name) : new File(parent, name);
    }

    public static void configureSparkServerConfigs(final File file) {
        final File resolvedConfigFile = file == null ? new File("mSpigot.yml") : file;
        final LinkedHashSet<String> extraConfigs = new LinkedHashSet<>();
        final String existing = System.getProperty(SPARK_EXTRA_CONFIGS_PROPERTY);
        if (existing != null && !existing.isBlank()) {
            for (final String path : existing.split(",")) {
                final String trimmed = path.trim();
                if (!trimmed.isEmpty()) {
                    extraConfigs.add(trimmed);
                }
            }
        }
        extraConfigs.add(resolvedConfigFile.getPath());
        extraConfigs.add(siblingFile(resolvedConfigFile, "knockback.yml").getPath());
        System.setProperty(SPARK_EXTRA_CONFIGS_PROPERTY, String.join(",", extraConfigs));
    }

    private static void loadFromDisk() {
        final YamlConfiguration loaded = loadYaml(configFile);
        final YamlConfiguration loadedKnockback = loadYaml(knockbackConfigFile);
        loaded.options().header("mSpigot configuration\n\nSystemLink features are configured here. Experimental options are disabled by default unless noted.");
        loadedKnockback.options().header("mSpigot knockback profiles\n\nProfile definitions are stored here. Use /knockback reload or /spigot reload after editing this file while the server is running.");
        config = loaded;
        knockbackConfig = loadedKnockback;
        installDefaults();
        migrateLegacyKnockbackProfiles();
        removeConfigNoise();
        writeProfileDefaults("default");
        publishSnapshot(readSnapshot());
        saveFiles();
    }

    private static YamlConfiguration loadYaml(final File file) {
        final YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (final IOException ignored) {
        } catch (final InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load " + file + "; correct its YAML syntax", exception);
        }
        return loaded;
    }

    private static void installDefaults() {
        setting("optimizations.async-spawning.enabled", true,
            "Stage natural mob-spawn preparation across ticks to reduce main-thread spawn spikes. Entity creation remains on the main thread.");
        setting("optimizations.async-spawning.chunk-scan-interval-ticks", 2,
            "Run the spawning-chunk scan every N ticks while async-spawning is enabled. Use 1 for vanilla every-tick scanning.");
        setting("optimizations.async-playerdata.enabled", false, "Save and load player data asynchronously.");

        setting("startup.parallel-recipe-loading.enabled", true,
            "Load datapack recipes on multiple worker threads during startup and reload.");
        setting("startup.parallel-recipe-loading.threads", 0,
            "Recipe-loading worker count; 0 selects all available processors.");
        setting("startup.parallel-recipe-loading.minimum-recipes", 64,
            "Small packs below this recipe count stay on the vanilla serial path.");
        setting("startup.local-pom-cache.enabled", true,
            "Prefer already cached runtime-library POM metadata to avoid unnecessary startup network checks.");
        setting("startup.self-inspection-cache.enabled", true,
            "Cache plugin listener reflection results while registering events.");

        setting("gameplay.fishing-hooks-pull-entities", true, "Allow fishing hooks to pull entities.");

        setting("gameplay.potions.fast-pots.enabled", false,
            "Enable configurable splash/lingering potion throw speed.");
        setting("gameplay.potions.fast-pots.speed", 0.5,
            "Potion throw power. Vanilla is 0.5; common fast-pot values are around 0.7-1.2.");
        setting("gameplay.potions.async-pots.enabled", false,
            "Reserve async-safe potion preprocessing paths. Potion effect mutation remains on the main thread.");
        setting("gameplay.potions.optimized-splash-detection", true,
            "Use lower-allocation splash-potion target collection where available.");

        setting("gameplay.pearls.taliban-pearls.enabled", false,
            "Allow HCF-style ender pearl clipping through configured partial blocks and tight gaps.");
        setting("gameplay.pearls.taliban-pearls.pass-through-distance", 1.15,
            "Distance to push the pearl teleport point through an allowed partial block.");
        setting("gameplay.pearls.taliban-pearls.partial-blocks", true,
            "Allow non-full collision-shape blocks to be passed by HCF pearls.");
        setting("gameplay.pearls.taliban-pearls.fences", true,
            "Allow HCF pearls through fences and fence gates.");
        setting("gameplay.pearls.taliban-pearls.stairs-and-slabs", true,
            "Allow HCF pearls through stairs and slabs.");
        setting("gameplay.pearls.taliban-pearls.trapdoors", true,
            "Allow HCF pearls through trapdoors.");
        setting("gameplay.pearls.taliban-pearls.chests", true,
            "Allow HCF pearls through chest-style blocks.");
        setting("gameplay.pearls.taliban-pearls.tripwire", true,
            "Allow HCF pearls through tripwire and tripwire hooks.");

        setting("gameplay.tnt.recoded-mechanics.enabled", true,
            "Enable mSpigot's configurable TNT mechanics hooks.");
        setting("gameplay.tnt.recoded-mechanics.fuse-ticks", 80,
            "Fuse used by mSpigot-created primed TNT.");
        setting("gameplay.tnt.recoded-mechanics.randomize-explosion-chain-fuse", true,
            "Use vanilla random short fuses when explosions prime nearby TNT.");
        setting("gameplay.tnt.recoded-mechanics.stable-initial-motion", false,
            "Remove random sideways motion from newly primed TNT for more deterministic cannons.");
        setting("gameplay.tnt.keep-chunks-loaded.enabled", false,
            "Keep chunks containing active primed TNT loaded and simulated so cannons can finish away from players.");
        setting("gameplay.tnt.keep-chunks-loaded.radius", 1,
            "Chunk-ticket radius used by active TNT when keep-chunks-loaded is enabled.");
        setting("gameplay.tnt.disable-left-shooting", false,
            "Disable left-click projectile shooting hooks from plugins by suppressing left-click interact callbacks while holding projectiles.");
        setting("gameplay.tnt.efficient-wall-damage", false,
            "Skip TNT block-drop collection for faster cannon wall damage.");
        setting("gameplay.tnt.max-active-per-world", 0,
            "Maximum active primed TNT ticks per world per tick; 0 keeps the Spigot world limit.");

        setting("gameplay.knockback.snowball-knockback-players", false, "Make snowballs knock players back.");
        setting("gameplay.knockback.egg-knockback-players", false, "Make eggs knock players back.");
        setting("gameplay.knockback.DefaultProfile", "default", "Default knockback profile used by worlds without an assignment.");
        setting("gameplay.knockback.world-profiles", Map.of(), "Optional world-name to knockback-profile assignments.");

        setting("gameplay.spawner-settings.enabled", false, "Enable all custom spawner settings below.");
        setting("gameplay.spawner-settings.checks.light-level", false, "Check light level before a spawner creates a mob.");
        setting("gameplay.spawner-settings.checks.spawner-max-nearby", true, "Honor the spawner maximum-nearby-entities check.");
        setting("gameplay.spawner-settings.checks.check-for-nearby-players", true, "Require a nearby player for spawner activity.");
        setting("gameplay.spawner-settings.checks.spawner-block-checks", false, "Check for blocks obstructing mob spawning.");
        setting("gameplay.spawner-settings.checks.water-prevent-spawn", false, "Prevent spawner mobs from spawning in water.");
        setting("gameplay.spawner-settings.ignore-rules", false, "Ignore biome and block requirements for spawner mobs.");
        setting("gameplay.spawner-settings.min-spawn-delay", 200, "Minimum delay in ticks between spawner activations.");
        setting("gameplay.spawner-settings.max-spawn-delay", 800, "Maximum delay in ticks between spawner activations.");

        setting("world-configuration.default.sugar-cane.enabled", true, "Enable sugar-cane growth in this world.");
        setting("world-configuration.default.sugar-cane.growth-multiplier", 1.0, "Multiplier applied to the Spigot sugar-cane growth modifier.");
        setting("world-configuration.default.sugar-cane.max-height", -1, "Maximum sugar-cane height; -1 uses Paper's world setting.");
        setting("world-configuration.worlds", Map.of(), "Per-world overrides for implemented world settings.");

        setting("security.nbt-protection.enabled", true,
            "Block or skip high-risk item data components from client packets.");
        setting("security.nbt-protection.nbt-skip-protector", true,
            "Skip dangerous client-sent data instead of kicking when possible.");
        setting("security.nbt-protection.kick-on-violation", false,
            "Kick players for NBT/data-component violations when skip protector is disabled.");
        setting("security.nbt-protection.read-nbt-from-placed-blocks", false,
            "Allow item block-entity data to be applied to placed blocks.");
        setting("security.nbt-protection.max-item-components", 64,
            "Maximum item data-component count accepted from client-created items.");
        setting("security.nbt-protection.max-book-pages", 100,
            "Maximum pages accepted in book edit packets and book components.");
        setting("security.nbt-protection.max-book-page-length", 1024,
            "Maximum characters accepted per editable book page.");
        setting("security.nbt-protection.max-book-total-length", 32768,
            "Maximum combined character count accepted across a book.");
        setting("security.nbt-protection.max-custom-payload-bytes", 32767,
            "Maximum custom payload size accepted from clients.");
        setting("security.nbt-protection.block-lectern-book-data", true,
            "Block client-sent lectern book block-entity data.");
        setting("security.nbt-protection.block-container-items", true,
            "Block nested container/bundle item components from client-created items.");
        setting("security.block-world-downloader", true,
            "Block known World Downloader custom-payload channels.");
        setting("security.packet-flood.enabled", true,
            "Enable the lightweight mSpigot packet-flood prefilter.");
        setting("security.packet-flood.max-packets-per-second", 500,
            "Sustained packets per second allowed per connection.");
        setting("security.packet-flood.burst", 1000,
            "Extra packets allowed in a one-second flood window.");
        setting("security.packet-flood.kick-on-violation", true,
            "Kick connections that exceed the mSpigot flood limit.");
        setting("security.redstone-abuse-limiter.enabled", false,
            "Enable a per-tick redstone-abuse limiter.");
        setting("security.redstone-abuse-limiter.max-updates-per-tick", 10000,
            "Maximum redstone-related updates per tick when the limiter is enabled.");
        setting("security.russian-crasher-movement-check.enabled", true,
            "Drop excessive movement packets in one tick before expensive player tick calculations.");
        setting("security.russian-crasher-movement-check.max-move-packets-per-tick", 25,
            "Maximum movement packets accepted per player per server tick.");
        setting("security.raytrace-xray.enabled", false,
            "Hide exposed ores from each player unless a real server-side ray trace can see them.");
        setting("security.raytrace-xray.update-radius", 6,
            "Block radius scanned around players and newly mined blocks for dynamic ore reveal/hide updates.");
        setting("security.raytrace-xray.update-interval-ticks", 10,
            "How often online players are rescanned for exposed ore visibility.");
        setting("security.raytrace-xray.max-ray-distance", 48.0,
            "Maximum distance in blocks for raytrace anti-xray visibility checks.");
        setting("security.raytrace-xray.max-ores-per-scan", 128,
            "Maximum exposed ore blocks processed per player scan.");
        setting("security.raytrace-xray.fake-ores.enabled", false,
            "Send client-side fake ores inside unexposed stone near mining players to distract xray clients.");
        setting("security.raytrace-xray.fake-ores.radius", 8,
            "Radius around a mined block where fake ores may be generated.");
        setting("security.raytrace-xray.fake-ores.chance", 0.12,
            "Chance per block break to attempt fake ore generation.");
        setting("security.raytrace-xray.fake-ores.attempts", 24,
            "Candidate blocks checked when trying to generate a fake ore.");
        setting("security.raytrace-xray.fake-ores.max-per-player", 96,
            "Maximum fake ore block changes tracked for one player.");
        setting("security.raytrace-xray.fake-ores.expire-ticks", 1200,
            "Ticks before a fake ore is automatically reverted on the client.");
        setting("security.raytrace-xray.fake-ores.disappear-radius", 2,
            "Fake ores within this distance of a mined block are reverted immediately.");

        setting("networking.flush-consolidation", true,
            "Keep Netty flush consolidation enabled for player channels.");
        setting("networking.tcp-nodelay", true,
            "Enable TCP_NODELAY on Netty sockets.");
        setting("networking.ip-tos", -1,
            "IP_TOS/traffic-class value; -1 leaves the platform default.");
        setting("networking.tcp-fast-open.enabled", false,
            "Attempt to enable Netty TCP_FASTOPEN where the platform supports it.");
        setting("networking.tcp-fast-open.queue-size", 256,
            "TCP_FASTOPEN queue size when enabled.");
        setting("networking.explicit-flush", false,
            "Opt back into Paper's explicit connection flush behavior if compatibility requires it.");
        setting("networking.async-command-packet-build", true,
            "Build the client command packet off the main thread during joins and command refreshes.");

        setting("chunks.prevent-loading-for-hoppers", false,
            "Avoid chunk loads from hopper-style neighbor checks where safe.");
        setting("ticking.chunk-mode", "VANILLA",
            "Chunk ticking mode: VANILLA, OPTIMIZED, or EXTRA_OPTIMIZED.");
        setting("ticking.chunk-ticking", true, "Enable world chunk ticking.");
        setting("ticking.weather-ticking", true, "Enable weather ticking.");
        setting("ticking.villager-ticking", true, "Enable villager AI/brain ticking.");

        setting("entities.search-delay-ticks", 0,
            "Delay between expensive nearby-entity searches; 0 keeps vanilla behavior.");
        setting("entities.ai-goal-selector-throttle-ticks", 0,
            "Throttle AI goal selector work; 0 keeps vanilla behavior.");
        setting("entities.entity-collisions", true, "Enable entity collision checks.");
        setting("entities.mob-ai", true, "Enable mob AI globally.");
        setting("entities.mob-item-pickup", true, "Allow mobs to search for and pick up nearby items.");
        setting("entities.baby-mob-spawn-from-spawners", true, "Allow spawners to create baby mobs when vanilla would.");
        setting("entities.mob-spawning", true, "Enable mob spawning.");

        setting("world.hopper-optimization", true, "Enable mSpigot hopper algorithm shortcuts.");
        setting("world.save-player-data", true, "Persist player data to disk.");
        setting("world.always-day", false, "Force worlds to remain in daytime.");
        setting("world.always-pretty-weather", false, "Force clear weather.");
        setting("world.spawners.spawn-range", 4, "Configured spawner spawn range.");
        setting("world.spawners.min-range", 4, "Minimum custom spawner activation range.");
        setting("world.spawners.max-range", 16, "Maximum custom spawner activation range.");
        setting("world.spawners.required-player-range", 16, "Player distance required for custom spawners.");
        setting("world.spawners.spawn-count", 4, "Number of mobs attempted per custom spawner activation.");
        setting("world.spawners.max-nearby-entities", 6, "Maximum nearby entities allowed for custom spawners.");

        setting("general.footstep-sounds", true, "Enable player footstep sounds.");
        setting("general.player-move-event", true, "Call Bukkit PlayerMoveEvent in addition to mSpigot movement handlers.");
        setting("general.death-screen", true, "Show the vanilla death screen.");
        setting("general.async-catcher", true, "Enable Spigot's AsyncCatcher checks.");
        setting("general.styled-plugin-list", true, "Use mSpigot's compact /plugins layout with hover details.");
        setting("general.optimized-hit-detection", true, "Use cheaper projectile hit-detection margins where safe.");

        setting("diagnostics.lag-spike.enabled", false, "Enable the runtime lag-spike detector when the server starts or configuration reloads.");
        setting("diagnostics.lag-spike.threshold-ms", 100.0, "Minimum tick duration in milliseconds considered a lag spike.");
        setting("diagnostics.lag-spike.log-cooldown-seconds", 5, "Minimum time between lag-spike warnings.");
        setting("diagnostics.tps-graph.enabled", true, "Enable /tps graph.");
        setting("diagnostics.tps-graph.seconds", 48, "Seconds of TPS samples displayed by /tps graph.");
        setting("diagnostics.tps-graph.height", 8, "Text rows used by the live TPS graph.");

    }

    private static void writeProfileDefaults(final String name) {
        final String root = profilePath(name);
        knockbackSetting(root + ".enabled", true, "Whether this profile overrides vanilla/Paper knockback.");
        knockbackSetting(root + ".base-knockback", 0.4, "Base horizontal knockback.");
        knockbackSetting(root + ".knockback-resistance-modifier", 1.0, "Multiplier for knockback resistance.");
        knockbackSetting(root + ".knockback-vertical", "default", "Vertical knockback, or 'default' to use adjusted power.");
        knockbackSetting(root + ".knockback-vertical-limit", 0.4, "Maximum upward velocity from knockback.");
        knockbackSetting(root + ".shield-hit-knockback", 0.5, "Knockback when an attack is blocked by a shield.");
        knockbackSetting(root + ".extra-knockback", 0.5, "Additional sprint-attack knockback.");
        knockbackSetting(root + ".require-full-attack", true, "Require a fully charged attack for sprint knockback.");
        knockbackSetting(root + ".sweeping-edge-knockback", 0.4, "Knockback applied by sweep attacks.");
        knockbackSetting(root + ".vertical-knockback-require-ground", true, "Only change vertical velocity for grounded targets.");
    }

    private static void writeProfile(final String name, final AdvancedKnockbackConfiguration profile) {
        final String root = profilePath(name);
        knockbackConfig.set(root + ".enabled", profile.enabled);
        knockbackConfig.set(root + ".base-knockback", profile.baseKnockback);
        knockbackConfig.set(root + ".knockback-resistance-modifier", profile.knockbackResistanceModifier);
        knockbackConfig.set(root + ".knockback-vertical", profile.knockbackVertical.value().isPresent() ? profile.knockbackVertical.doubleValue() : "default");
        knockbackConfig.set(root + ".knockback-vertical-limit", profile.knockbackVerticalLimit);
        knockbackConfig.set(root + ".shield-hit-knockback", profile.shieldHitKnockback);
        knockbackConfig.set(root + ".extra-knockback", profile.extraKnockback);
        knockbackConfig.set(root + ".require-full-attack", profile.requireFullAttack);
        knockbackConfig.set(root + ".sweeping-edge-knockback", profile.sweepingEdgeKnockback);
        knockbackConfig.set(root + ".vertical-knockback-require-ground", profile.verticalKnockbackRequireGround);
    }

    private static Snapshot readSnapshot() {
        final OptimizationSettings optimizations = new OptimizationSettings(
            config.getBoolean("optimizations.async-switch-state"),
            toggle("optimizations.async-chunks"),
            new AsyncSpawningSettings(
                config.getBoolean("optimizations.async-spawning.enabled"),
                Math.max(1, nonNegativeInt("optimizations.async-spawning.chunk-scan-interval-ticks", 2))
            ),
            new AsyncPathfindingSettings(
                config.getBoolean("optimizations.async-pathfinding.enabled"),
                config.getInt("optimizations.async-pathfinding.max-threads"),
                config.getInt("optimizations.async-pathfinding.keepalive"),
                config.getInt("optimizations.async-pathfinding.queue-size")
            ),
            toggle("optimizations.async-playerdata"),
            new ThreadedToggle(config.getBoolean("optimizations.async-tracker.enabled"), config.getInt("optimizations.async-tracker.threads")),
            new ThreadedToggle(config.getBoolean("optimizations.async-worlds.enabled"), config.getInt("optimizations.async-worlds.threads")),
            config.getBoolean("optimizations.chat-message-signature"),
            toggle("optimizations.TPSCatchup"),
            config.getBoolean("optimizations.optimize-blocks-entities"),
            config.getBoolean("optimizations.optimize-mob-despawning"),
            config.getBoolean("optimizations.only-tick-items-in-hand"),
            config.getBoolean("optimizations.optimize-player-move-event"),
            config.getBoolean("optimizations.optimize-random-tick"),
            config.getBoolean("optimizations.optimize-waypoint"),
            config.getBoolean("optimizations.optimized-rails"),
            config.getBoolean("optimizations.reduce-packets.reduce-move-packets"),
            config.getBoolean("optimizations.disable-ai-for-idle-mobs")
        );

        final StartupSettings startup = new StartupSettings(
            new ParallelRecipeLoadingSettings(
                config.getBoolean("startup.parallel-recipe-loading.enabled"),
                nonNegativeInt("startup.parallel-recipe-loading.threads", 0),
                nonNegativeInt("startup.parallel-recipe-loading.minimum-recipes", 64)
            ),
            toggle("startup.local-pom-cache"),
            toggle("startup.self-inspection-cache")
        );

        final CombatSettings combat = new CombatSettings(
            config.getBoolean("gameplay.combat.block-with-swords"),
            config.getBoolean("gameplay.combat.allow-sweep-attacks"),
            config.getBoolean("gameplay.combat.fast-health-regen"),
            config.getBoolean("gameplay.combat.legacy-combat-mechanics"),
            config.getInt("gameplay.combat.max-armour-damage"),
            config.getDouble("gameplay.combat.max-damage"),
            config.getBoolean("gameplay.combat.old-enchanted-golden-apple"),
            config.getBoolean("gameplay.combat.old-potion-effects"),
            config.getBoolean("gameplay.combat.old-sounds-and-particle-effects"),
            config.getBoolean("gameplay.combat.shield-damage-reduction"),
            readDamageOverrides()
        );

        final KnockbackSettings knockback = readKnockbackSettings();
        final SpawnerSettings spawner = new SpawnerSettings(
            config.getBoolean("gameplay.spawner-settings.enabled"),
            new SpawnerChecks(
                config.getBoolean("gameplay.spawner-settings.checks.light-level"),
                config.getBoolean("gameplay.spawner-settings.checks.spawner-max-nearby"),
                config.getBoolean("gameplay.spawner-settings.checks.check-for-nearby-players"),
                config.getBoolean("gameplay.spawner-settings.checks.spawner-block-checks"),
                config.getBoolean("gameplay.spawner-settings.checks.water-prevent-spawn")
            ),
            config.getBoolean("gameplay.spawner-settings.ignore-rules"),
            config.getInt("gameplay.spawner-settings.min-spawn-delay"),
            config.getInt("gameplay.spawner-settings.max-spawn-delay")
        );
        final GameplaySettings gameplay = new GameplaySettings(
            combat,
            config.getBoolean("gameplay.fishing-hooks-pull-entities"),
            readPotionSettings(),
            readPearlSettings(),
            readTntSettings(),
            knockback,
            spawner
        );
        final ThemeSettings theme = new ThemeSettings(
            config.getString("theme.brand-name", "LightSpigot"),
            config.getString("theme.gui-name", "LightSpigot Console"),
            config.getString("theme.main-color", "&b"),
            config.getString("theme.secondary-color", "&7"),
            config.getString("theme.title-color", "&e")
        );
        return new Snapshot(
            optimizations,
            startup,
            gameplay,
            readWorldConfiguration(),
            readSecuritySettings(),
            readNetworkingSettings(),
            readChunkSettings(),
            readTickingSettings(),
            readEntitySettings(),
            readWorldRuntimeSettings(),
            readGeneralSettings(),
            new DiagnosticsSettings(new LagSpikeSettings(
                config.getBoolean("diagnostics.lag-spike.enabled"),
                positive("diagnostics.lag-spike.threshold-ms", 100.0),
                nonNegativeInt("diagnostics.lag-spike.log-cooldown-seconds", 5)
            ), new TpsGraphSettings(
                config.getBoolean("diagnostics.tps-graph.enabled"),
                Math.max(1, Math.min(300, nonNegativeInt("diagnostics.tps-graph.seconds", 48))),
                Math.max(1, Math.min(16, nonNegativeInt("diagnostics.tps-graph.height", 8)))
            )),
            new MiscellaneousSettings(toggle("miscellaneous.secure-seed")),
            theme,
            config.getString("unknown-command", "default")
        );
    }

    private static PotionSettings readPotionSettings() {
        return new PotionSettings(
            config.getBoolean("gameplay.potions.fast-pots.enabled"),
            Math.min(5.0, positive("gameplay.potions.fast-pots.speed", 0.5)),
            toggle("gameplay.potions.async-pots"),
            config.getBoolean("gameplay.potions.optimized-splash-detection")
        );
    }

    private static PearlSettings readPearlSettings() {
        return new PearlSettings(
            config.getBoolean("gameplay.pearls.taliban-pearls.enabled"),
            Math.min(3.0, positive("gameplay.pearls.taliban-pearls.pass-through-distance", 1.15)),
            config.getBoolean("gameplay.pearls.taliban-pearls.partial-blocks"),
            config.getBoolean("gameplay.pearls.taliban-pearls.fences"),
            config.getBoolean("gameplay.pearls.taliban-pearls.stairs-and-slabs"),
            config.getBoolean("gameplay.pearls.taliban-pearls.trapdoors"),
            config.getBoolean("gameplay.pearls.taliban-pearls.chests"),
            config.getBoolean("gameplay.pearls.taliban-pearls.tripwire")
        );
    }

    private static TntSettings readTntSettings() {
        return new TntSettings(
            toggle("gameplay.tnt.recoded-mechanics"),
            Math.max(1, Math.min(72000, nonNegativeInt("gameplay.tnt.recoded-mechanics.fuse-ticks", 80))),
            config.getBoolean("gameplay.tnt.recoded-mechanics.randomize-explosion-chain-fuse"),
            config.getBoolean("gameplay.tnt.recoded-mechanics.stable-initial-motion"),
            toggle("gameplay.tnt.keep-chunks-loaded"),
            Math.max(0, Math.min(8, nonNegativeInt("gameplay.tnt.keep-chunks-loaded.radius", 1))),
            config.getBoolean("gameplay.tnt.disable-left-shooting"),
            config.getBoolean("gameplay.tnt.efficient-wall-damage"),
            nonNegativeInt("gameplay.tnt.max-active-per-world", 0)
        );
    }

    private static SecuritySettings readSecuritySettings() {
        return new SecuritySettings(
            new NbtProtectionSettings(
                config.getBoolean("security.nbt-protection.enabled"),
                config.getBoolean("security.nbt-protection.nbt-skip-protector"),
                config.getBoolean("security.nbt-protection.kick-on-violation"),
                config.getBoolean("security.nbt-protection.read-nbt-from-placed-blocks"),
                nonNegativeInt("security.nbt-protection.max-item-components", 64),
                nonNegativeInt("security.nbt-protection.max-book-pages", 100),
                nonNegativeInt("security.nbt-protection.max-book-page-length", 1024),
                nonNegativeInt("security.nbt-protection.max-book-total-length", 32768),
                nonNegativeInt("security.nbt-protection.max-custom-payload-bytes", 32767),
                config.getBoolean("security.nbt-protection.block-lectern-book-data"),
                config.getBoolean("security.nbt-protection.block-container-items")
            ),
            config.getBoolean("security.block-world-downloader"),
            new PacketFloodSettings(
                config.getBoolean("security.packet-flood.enabled"),
                nonNegativeInt("security.packet-flood.max-packets-per-second", 500),
                nonNegativeInt("security.packet-flood.burst", 1000),
                config.getBoolean("security.packet-flood.kick-on-violation")
            ),
            new RedstoneAbuseLimiterSettings(
                config.getBoolean("security.redstone-abuse-limiter.enabled"),
                nonNegativeInt("security.redstone-abuse-limiter.max-updates-per-tick", 10000)
            ),
            new MovementCheckSettings(
                config.getBoolean("security.russian-crasher-movement-check.enabled"),
                nonNegativeInt("security.russian-crasher-movement-check.max-move-packets-per-tick", 25)
            ),
            readRaytraceXraySettings()
        );
    }

    private static RaytraceXraySettings readRaytraceXraySettings() {
        return new RaytraceXraySettings(
            config.getBoolean("security.raytrace-xray.enabled"),
            Math.max(1, Math.min(24, nonNegativeInt("security.raytrace-xray.update-radius", 6))),
            Math.max(1, Math.min(100, nonNegativeInt("security.raytrace-xray.update-interval-ticks", 10))),
            Math.min(128.0, positive("security.raytrace-xray.max-ray-distance", 48.0)),
            Math.max(1, Math.min(4096, nonNegativeInt("security.raytrace-xray.max-ores-per-scan", 128))),
            new FakeOreSettings(
                config.getBoolean("security.raytrace-xray.fake-ores.enabled"),
                Math.max(1, Math.min(24, nonNegativeInt("security.raytrace-xray.fake-ores.radius", 8))),
                Math.min(1.0, nonNegative("security.raytrace-xray.fake-ores.chance", 0.12)),
                Math.max(1, Math.min(256, nonNegativeInt("security.raytrace-xray.fake-ores.attempts", 24))),
                Math.max(1, Math.min(2048, nonNegativeInt("security.raytrace-xray.fake-ores.max-per-player", 96))),
                Math.max(1, Math.min(72000, nonNegativeInt("security.raytrace-xray.fake-ores.expire-ticks", 1200))),
                Math.max(1, Math.min(12, nonNegativeInt("security.raytrace-xray.fake-ores.disappear-radius", 2)))
            )
        );
    }

    private static NetworkingSettings readNetworkingSettings() {
        return new NetworkingSettings(
            config.getBoolean("networking.flush-consolidation"),
            config.getBoolean("networking.tcp-nodelay"),
            inheritedInt("networking.ip-tos", -1, -1),
            new TcpFastOpenSettings(
                config.getBoolean("networking.tcp-fast-open.enabled"),
                nonNegativeInt("networking.tcp-fast-open.queue-size", 256)
            ),
            config.getBoolean("networking.explicit-flush"),
            config.getBoolean("networking.async-command-packet-build")
        );
    }

    private static ChunkSettings readChunkSettings() {
        return new ChunkSettings(
            config.getBoolean("chunks.prevent-loading-for-hoppers"),
            config.getBoolean("chunks.prevent-loading-for-lights"),
            config.getBoolean("chunks.prevent-loading-for-furnaces"),
            config.getBoolean("chunks.optimized-entity-lookups"),
            config.getBoolean("chunks.reduce-hashing"),
            config.getBoolean("chunks.optimize-unloading"),
            config.getBoolean("chunks.optimize-calculations")
        );
    }

    private static TickingSettings readTickingSettings() {
        return new TickingSettings(
            tickingMode("ticking.chunk-mode"),
            config.getBoolean("ticking.chunk-ticking"),
            config.getBoolean("ticking.weather-ticking"),
            config.getBoolean("ticking.villager-ticking")
        );
    }

    private static EntitySettings readEntitySettings() {
        return new EntitySettings(
            new MobSpawnerRuntimeSettings(config.getBoolean("entities.mob-spawners.partial-async")),
            nonNegativeInt("entities.search-delay-ticks", 0),
            nonNegativeInt("entities.ai-goal-selector-throttle-ticks", 0),
            config.getBoolean("entities.mob-tracker"),
            config.getBoolean("entities.entity-collisions"),
            config.getBoolean("entities.mob-ai"),
            config.getBoolean("entities.mob-item-pickup"),
            config.getBoolean("entities.baby-mob-spawn-from-spawners"),
            config.getBoolean("entities.mob-spawning")
        );
    }

    private static WorldRuntimeSettings readWorldRuntimeSettings() {
        return new WorldRuntimeSettings(
            config.getBoolean("world.hopper-optimization"),
            config.getBoolean("world.save-player-data"),
            config.getBoolean("world.always-day"),
            config.getBoolean("world.always-pretty-weather"),
            new SpawnerRangeSettings(
                nonNegativeInt("world.spawners.spawn-range", 4),
                nonNegativeInt("world.spawners.min-range", 4),
                nonNegativeInt("world.spawners.max-range", 16),
                nonNegativeInt("world.spawners.required-player-range", 16),
                nonNegativeInt("world.spawners.spawn-count", 4),
                nonNegativeInt("world.spawners.max-nearby-entities", 6)
            )
        );
    }

    private static GeneralSettings readGeneralSettings() {
        return new GeneralSettings(
            config.getBoolean("general.footstep-sounds"),
            config.getBoolean("general.player-move-event"),
            config.getBoolean("general.death-screen"),
            config.getBoolean("general.async-catcher"),
            config.getBoolean("general.styled-plugin-list"),
            config.getBoolean("general.optimized-hit-detection")
        );
    }

    private static WorldConfigurationSettings readWorldConfiguration() {
        final WorldSettings defaults = readWorldSettings("world-configuration.default", null);
        final Map<String, WorldSettings> worlds = new LinkedHashMap<>();
        final ConfigurationSection section = config.getConfigurationSection("world-configuration.worlds");
        if (section != null) {
            for (final String world : section.getKeys(false)) {
                worlds.put(world.toLowerCase(Locale.ROOT), readWorldSettings("world-configuration.worlds." + world, defaults));
            }
        }
        return new WorldConfigurationSettings(defaults, Map.copyOf(worlds));
    }

    private static WorldSettings readWorldSettings(final String root, final WorldSettings fallback) {
        final SugarCaneSettings fallbackSugarCane = fallback == null ? new SugarCaneSettings(true, 1.0, -1) : fallback.sugarCane();
        final OreSettings fallbackOres = fallback == null ? new OreSettings(true, 1.0, Map.of()) : fallback.ores();
        final CaveSettings fallbackCaves = fallback == null ? new CaveSettings(true, Map.of()) : fallback.caves();
        final BiomeSettings fallbackBiomes = fallback == null ? new BiomeSettings(Map.of(), Map.of()) : fallback.biomes();
        final CustomBlockRuleSettings fallbackBlockRules = fallback == null ? new CustomBlockRuleSettings(false, Map.of()) : fallback.customBlockRules();

        return new WorldSettings(
            new SugarCaneSettings(
                inheritedBoolean(root + ".sugar-cane.enabled", fallbackSugarCane.enabled()),
                inheritedNonNegativeDouble(root + ".sugar-cane.growth-multiplier", fallbackSugarCane.growthMultiplier()),
                inheritedInt(root + ".sugar-cane.max-height", fallbackSugarCane.maxHeight(), -1)
            ),
            new OreSettings(
                inheritedBoolean(root + ".ores.enabled", fallbackOres.enabled()),
                inheritedNonNegativeDouble(root + ".ores.generation-multiplier", fallbackOres.generationMultiplier()),
                inheritedStringMap(root + ".ores.replacements", fallbackOres.replacements())
            ),
            new CaveSettings(
                inheritedBoolean(root + ".caves.enabled", fallbackCaves.enabled()),
                inheritedStringMap(root + ".caves.modifications", fallbackCaves.modifications())
            ),
            new BiomeSettings(
                inheritedStringMap(root + ".biomes.swaps", fallbackBiomes.swaps()),
                inheritedStringMap(root + ".biomes.regional-swaps", fallbackBiomes.regionalSwaps())
            ),
            new CustomBlockRuleSettings(
                inheritedBoolean(root + ".custom-block-rules.enabled", fallbackBlockRules.enabled()),
                inheritedStringMap(root + ".custom-block-rules.rules", fallbackBlockRules.rules())
            )
        );
    }

    private static boolean inheritedBoolean(final String path, final boolean fallback) {
        return config.contains(path) ? config.getBoolean(path) : fallback;
    }

    private static int inheritedInt(final String path, final int fallback, final int minimum) {
        if (!config.contains(path)) {
            return fallback;
        }
        final int value = config.getInt(path);
        if (value < minimum) {
            LOGGER.warn("Invalid value for {}; using {}", path, fallback);
            return fallback;
        }
        return value;
    }

    private static double inheritedNonNegativeDouble(final String path, final double fallback) {
        if (!config.contains(path)) {
            return fallback;
        }
        final double value = config.getDouble(path);
        if (!Double.isFinite(value) || value < 0.0) {
            LOGGER.warn("Invalid value for {}; using {}", path, fallback);
            return fallback;
        }
        return value;
    }

    private static Map<String, String> inheritedStringMap(final String path, final Map<String, String> fallback) {
        if (!config.contains(path)) {
            return fallback;
        }
        final ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        final Map<String, String> values = new LinkedHashMap<>();
        for (final String key : section.getKeys(false)) {
            values.put(key, section.getString(key, ""));
        }
        return Map.copyOf(values);
    }

    private static KnockbackSettings readKnockbackSettings() {
        final Map<String, AdvancedKnockbackConfiguration> profiles = new LinkedHashMap<>();
        final ConfigurationSection profileSection = knockbackConfig.getConfigurationSection("profiles");
        if (profileSection != null) {
            for (final String rawName : profileSection.getKeys(false)) {
                final String name = rawName.toLowerCase(Locale.ROOT);
                if (!PROFILE_NAME.matcher(name).matches()) {
                    LOGGER.warn("Ignoring invalid knockback profile name '{}' in {}", rawName, knockbackConfigFile);
                    continue;
                }
                profiles.put(name, readProfile(name));
            }
        }
        profiles.putIfAbsent("default", readProfile("default"));

        String defaultProfile = config.getString("gameplay.knockback.DefaultProfile", "default").toLowerCase(Locale.ROOT);
        if (!profiles.containsKey(defaultProfile)) {
            LOGGER.warn("Unknown default knockback profile '{}'; using 'default'", defaultProfile);
            defaultProfile = "default";
            config.set("gameplay.knockback.DefaultProfile", defaultProfile);
        }

        final Map<String, String> assignments = new LinkedHashMap<>();
        final ConfigurationSection worldSection = config.getConfigurationSection("gameplay.knockback.world-profiles");
        if (worldSection != null) {
            for (final String world : worldSection.getKeys(false)) {
                final String profile = worldSection.getString(world, defaultProfile).toLowerCase(Locale.ROOT);
                if (profiles.containsKey(profile)) {
                    assignments.put(world.toLowerCase(Locale.ROOT), profile);
                } else {
                    LOGGER.warn("Ignoring unknown knockback profile '{}' assigned to world '{}'", profile, world);
                }
            }
        }
        return new KnockbackSettings(
            config.getBoolean("gameplay.knockback.snowball-knockback-players"),
            config.getBoolean("gameplay.knockback.egg-knockback-players"),
            defaultProfile,
            Map.copyOf(assignments),
            Map.copyOf(profiles)
        );
    }

    private static AdvancedKnockbackConfiguration readProfile(final String name) {
        final String root = profilePath(name);
        final AdvancedKnockbackConfiguration profile = new AdvancedKnockbackConfiguration();
        profile.enabled = knockbackConfig.getBoolean(root + ".enabled", true);
        profile.baseKnockback = nonNegative(knockbackConfig, root + ".base-knockback", 0.4);
        profile.knockbackResistanceModifier = nonNegative(knockbackConfig, root + ".knockback-resistance-modifier", 1.0);
        profile.knockbackVertical = vertical(knockbackConfig, root + ".knockback-vertical");
        profile.knockbackVerticalLimit = nonNegative(knockbackConfig, root + ".knockback-vertical-limit", 0.4);
        profile.shieldHitKnockback = nonNegative(knockbackConfig, root + ".shield-hit-knockback", 0.5);
        profile.extraKnockback = nonNegative(knockbackConfig, root + ".extra-knockback", 0.5);
        profile.requireFullAttack = knockbackConfig.getBoolean(root + ".require-full-attack", true);
        profile.sweepingEdgeKnockback = nonNegative(knockbackConfig, root + ".sweeping-edge-knockback", 0.4);
        profile.verticalKnockbackRequireGround = knockbackConfig.getBoolean(root + ".vertical-knockback-require-ground", true);
        return profile;
    }

    private static Map<String, Double> readDamageOverrides() {
        final Map<String, Double> overrides = new LinkedHashMap<>();
        final ConfigurationSection section = config.getConfigurationSection("gameplay.combat.item-attack-damage-override");
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final double value = section.getDouble(key, Double.NaN);
                if (Double.isFinite(value)) {
                    overrides.put(key, value);
                } else {
                    LOGGER.warn("Ignoring invalid attack-damage override '{}'", key);
                }
            }
        }
        return Map.copyOf(overrides);
    }

    private static DoubleOr.Default vertical(final String path) {
        return vertical(config, path);
    }

    private static DoubleOr.Default vertical(final YamlConfiguration source, final String path) {
        final Object raw = source.get(path, "default");
        if (raw instanceof String string && string.equalsIgnoreCase("default")) {
            return DoubleOr.Default.USE_DEFAULT;
        }
        final double value;
        try {
            value = raw instanceof Number number ? number.doubleValue() : parseDouble(String.valueOf(raw), path);
        } catch (final IllegalArgumentException ignored) {
            LOGGER.warn("Invalid value for {}; using 'default'", path);
            source.set(path, "default");
            return DoubleOr.Default.USE_DEFAULT;
        }
        if (!Double.isFinite(value) || value < 0.0) {
            LOGGER.warn("Invalid value for {}; using 'default'", path);
            source.set(path, "default");
            return DoubleOr.Default.USE_DEFAULT;
        }
        return new DoubleOr.Default(OptionalDouble.of(value));
    }

    private static double nonNegative(final String path, final double fallback) {
        return nonNegative(config, path, fallback);
    }

    private static double nonNegative(final YamlConfiguration source, final String path, final double fallback) {
        final double value = source.getDouble(path, fallback);
        if (!Double.isFinite(value) || value < 0.0) {
            LOGGER.warn("Invalid value for {}; using {}", path, fallback);
            source.set(path, fallback);
            return fallback;
        }
        return value;
    }

    private static double positive(final String path, final double fallback) {
        final double value = config.getDouble(path, fallback);
        if (!Double.isFinite(value) || value <= 0.0) {
            LOGGER.warn("Invalid value for {}; using {}", path, fallback);
            config.set(path, fallback);
            return fallback;
        }
        return value;
    }

    private static int nonNegativeInt(final String path, final int fallback) {
        final int value = config.getInt(path, fallback);
        if (value < 0) {
            LOGGER.warn("Invalid value for {}; using {}", path, fallback);
            config.set(path, fallback);
            return fallback;
        }
        return value;
    }

    private static double parseDouble(final String value, final String setting) {
        try {
            return Double.parseDouble(value);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException(setting + " requires a number", exception);
        }
    }

    private static Toggle toggle(final String root) {
        return new Toggle(config.getBoolean(root + ".enabled"));
    }

    private static TickingMode tickingMode(final String path) {
        final String raw = config.getString(path, "VANILLA").toUpperCase(Locale.ROOT);
        try {
            return TickingMode.valueOf(raw);
        } catch (final IllegalArgumentException exception) {
            LOGGER.warn("Invalid value for {}; using VANILLA", path);
            config.set(path, "VANILLA");
            return TickingMode.VANILLA;
        }
    }

    private static String profilePath(final String name) {
        return "profiles." + name;
    }

    private static void migrateLegacyKnockbackProfiles() {
        final ConfigurationSection legacyProfiles = config.getConfigurationSection("gameplay.knockback.profiles");
        if (legacyProfiles == null) {
            return;
        }

        int migratedProfiles = 0;
        for (final String rawName : legacyProfiles.getKeys(false)) {
            final String name = rawName.toLowerCase(Locale.ROOT);
            if (!PROFILE_NAME.matcher(name).matches()) {
                LOGGER.warn("Ignoring invalid legacy knockback profile name '{}' in {}", rawName, configFile);
                continue;
            }
            final String targetRoot = profilePath(name);
            if (knockbackConfig.contains(targetRoot)) {
                continue;
            }
            final ConfigurationSection legacyProfile = legacyProfiles.getConfigurationSection(rawName);
            if (legacyProfile == null) {
                knockbackConfig.set(targetRoot, legacyProfiles.get(rawName));
            } else {
                copySection(legacyProfile, knockbackConfig, targetRoot);
            }
            migratedProfiles++;
        }

        config.set("gameplay.knockback.profiles", null);
        if (migratedProfiles > 0) {
            LOGGER.info("Migrated {} knockback profile(s) from {} to {}", migratedProfiles, configFile.getName(), knockbackConfigFile.getName());
        }
    }

    private static void removeConfigNoise() {
        for (final String path : List.of(
            "optimizations.async-switch-state",
            "optimizations.async-chunks",
            "optimizations.async-pathfinding",
            "optimizations.async-tracker",
            "optimizations.async-worlds",
            "optimizations.chat-message-signature",
            "optimizations.TPSCatchup",
            "optimizations.optimize-blocks-entities",
            "optimizations.optimize-mob-despawning",
            "optimizations.only-tick-items-in-hand",
            "optimizations.optimize-player-move-event",
            "optimizations.optimize-random-tick",
            "optimizations.optimize-waypoint",
            "optimizations.optimized-rails",
            "optimizations.reduce-packets",
            "optimizations.disable-ai-for-idle-mobs",
            "gameplay.combat",
            "world-configuration.default.ores",
            "world-configuration.default.caves",
            "world-configuration.default.biomes",
            "world-configuration.default.custom-block-rules",
            "chunks.prevent-loading-for-lights",
            "chunks.prevent-loading-for-furnaces",
            "chunks.optimized-entity-lookups",
            "chunks.reduce-hashing",
            "chunks.optimize-unloading",
            "chunks.optimize-calculations",
            "entities.mob-spawners",
            "entities.mob-tracker",
            "miscellaneous",
            "theme",
            "unknown-command"
        )) {
            if (config.contains(path)) {
                config.set(path, null);
            }
        }

        final ConfigurationSection worlds = config.getConfigurationSection("world-configuration.worlds");
        if (worlds != null) {
            for (final String world : worlds.getKeys(false)) {
                for (final String child : List.of("ores", "caves", "biomes", "custom-block-rules")) {
                    final String path = "world-configuration.worlds." + world + "." + child;
                    if (config.contains(path)) {
                        config.set(path, null);
                    }
                }
            }
        }
    }

    private static void copySection(final ConfigurationSection source, final YamlConfiguration target, final String targetRoot) {
        for (final String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                target.set(targetRoot + "." + key, source.get(key));
            }
        }
    }

    private static void setting(final String path, final Object value, final String... comments) {
        setting(config, path, value, comments);
    }

    private static void knockbackSetting(final String path, final Object value, final String... comments) {
        setting(knockbackConfig, path, value, comments);
    }

    private static void setting(final YamlConfiguration target, final String path, final Object value, final String... comments) {
        if (!target.contains(path)) {
            target.set(path, value);
        }
        if (target.getComments(path).isEmpty()) {
            target.setComments(path, List.of(comments));
        }
    }

    private static void saveAndRefresh() {
        publishSnapshot(readSnapshot());
        saveFiles();
    }

    private static void publishSnapshot(final Snapshot updated) {
        snapshot = updated;
        LagSpikeDetector.configure(updated.diagnostics().lagSpike());
        MobAIController.setEnabled(updated.entities().mobAi());
        org.spigotmc.AsyncCatcher.enabled = updated.general().asyncCatcher();
        System.setProperty("mspigot.networking.explicit-flush", Boolean.toString(updated.networking().explicitFlush()));
        System.setProperty("mspigot.startup.local-pom-cache.enabled", Boolean.toString(updated.startup().localPomCache().enabled()));
        System.setProperty("mspigot.startup.self-inspection-cache.enabled", Boolean.toString(updated.startup().selfInspectionCache().enabled()));
    }

    private static void saveFiles() {
        saveFile(configFile, config);
        saveFile(knockbackConfigFile, knockbackConfig);
    }

    private static void saveFile(final File file, final YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not save " + file, exception);
        }
    }

    private static void ensureInitialized() {
        if (configFile == null || knockbackConfigFile == null || config == null || knockbackConfig == null) {
            throw new IllegalStateException("mSpigot.yml has not been initialized");
        }
    }

    public enum KnockbackSetting {
        ENABLED("enabled", ValueType.BOOLEAN),
        BASE_KNOCKBACK("base-knockback", ValueType.NON_NEGATIVE_DOUBLE),
        KNOCKBACK_RESISTANCE_MODIFIER("knockback-resistance-modifier", ValueType.NON_NEGATIVE_DOUBLE),
        KNOCKBACK_VERTICAL("knockback-vertical", ValueType.VERTICAL),
        KNOCKBACK_VERTICAL_LIMIT("knockback-vertical-limit", ValueType.NON_NEGATIVE_DOUBLE),
        SHIELD_HIT_KNOCKBACK("shield-hit-knockback", ValueType.NON_NEGATIVE_DOUBLE),
        EXTRA_KNOCKBACK("extra-knockback", ValueType.NON_NEGATIVE_DOUBLE),
        REQUIRE_FULL_ATTACK("require-full-attack", ValueType.BOOLEAN),
        SWEEPING_EDGE_KNOCKBACK("sweeping-edge-knockback", ValueType.NON_NEGATIVE_DOUBLE),
        VERTICAL_KNOCKBACK_REQUIRE_GROUND("vertical-knockback-require-ground", ValueType.BOOLEAN);

        private final String path;
        private final ValueType type;

        KnockbackSetting(final String path, final ValueType type) {
            this.path = path;
            this.type = type;
        }

        public String path() {
            return this.path;
        }

        public static KnockbackSetting parse(final String input) {
            final String normalized = input.toLowerCase(Locale.ROOT).replace('_', '-');
            for (final KnockbackSetting setting : values()) {
                if (setting.path.equals(normalized)) {
                    return setting;
                }
            }
            throw new IllegalArgumentException("Unknown knockback setting '" + input + "'");
        }

        private Object parseValue(final String value) {
            return switch (this.type) {
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException(this.path + " requires true or false");
                    }
                    yield Boolean.parseBoolean(value);
                }
                case NON_NEGATIVE_DOUBLE -> {
                    final double parsed = parseDouble(value, this.path);
                    if (!Double.isFinite(parsed) || parsed < 0.0) {
                        throw new IllegalArgumentException(this.path + " requires a finite, non-negative number");
                    }
                    yield parsed;
                }
                case VERTICAL -> {
                    if (value.equalsIgnoreCase("default")) {
                        yield "default";
                    }
                    final double parsed = parseDouble(value, this.path);
                    if (!Double.isFinite(parsed) || parsed < 0.0) {
                        throw new IllegalArgumentException(this.path + " requires 'default' or a finite, non-negative number");
                    }
                    yield parsed;
                }
            };
        }

        private String display(final Object value) {
            return String.valueOf(value);
        }
    }

    private enum ValueType {
        BOOLEAN,
        NON_NEGATIVE_DOUBLE,
        VERTICAL
    }

    public record Snapshot(
        OptimizationSettings optimizations,
        StartupSettings startup,
        GameplaySettings gameplay,
        WorldConfigurationSettings worldConfiguration,
        SecuritySettings security,
        NetworkingSettings networking,
        ChunkSettings chunks,
        TickingSettings ticking,
        EntitySettings entities,
        WorldRuntimeSettings worldRuntime,
        GeneralSettings general,
        DiagnosticsSettings diagnostics,
        MiscellaneousSettings miscellaneous,
        ThemeSettings theme,
        String unknownCommand
    ) {
    }

    public record OptimizationSettings(
        boolean asyncSwitchState,
        Toggle asyncChunks,
        AsyncSpawningSettings asyncSpawning,
        AsyncPathfindingSettings asyncPathfinding,
        Toggle asyncPlayerData,
        ThreadedToggle asyncTracker,
        ThreadedToggle asyncWorlds,
        boolean chatMessageSignature,
        Toggle tpsCatchup,
        boolean optimizeBlocksEntities,
        boolean optimizeMobDespawning,
        boolean onlyTickItemsInHand,
        boolean optimizePlayerMoveEvent,
        boolean optimizeRandomTick,
        boolean optimizeWaypoint,
        boolean optimizedRails,
        boolean reduceMovePackets,
        boolean disableAiForIdleMobs
    ) {
    }

    public record Toggle(boolean enabled) {
    }

    public record ThreadedToggle(boolean enabled, int threads) {
    }

    public record AsyncSpawningSettings(boolean enabled, int chunkScanIntervalTicks) {
    }

    public record AsyncPathfindingSettings(boolean enabled, int maxThreads, int keepalive, int queueSize) {
    }

    public record StartupSettings(
        ParallelRecipeLoadingSettings parallelRecipeLoading,
        Toggle localPomCache,
        Toggle selfInspectionCache
    ) {
    }

    public record ParallelRecipeLoadingSettings(boolean enabled, int threads, int minimumRecipes) {
    }

    public record GameplaySettings(
        CombatSettings combat,
        boolean fishingHooksPullEntities,
        PotionSettings potions,
        PearlSettings pearls,
        TntSettings tnt,
        KnockbackSettings knockback,
        SpawnerSettings spawnerSettings
    ) {
    }

    public record CombatSettings(
        boolean blockWithSwords,
        boolean allowSweepAttacks,
        boolean fastHealthRegen,
        boolean legacyCombatMechanics,
        int maxArmourDamage,
        double maxDamage,
        boolean oldEnchantedGoldenApple,
        boolean oldPotionEffects,
        boolean oldSoundsAndParticleEffects,
        boolean shieldDamageReduction,
        Map<String, Double> itemAttackDamageOverride
    ) {
    }

    public record KnockbackSettings(
        boolean snowballKnockbackPlayers,
        boolean eggKnockbackPlayers,
        String defaultProfile,
        Map<String, String> worldProfiles,
        Map<String, AdvancedKnockbackConfiguration> profiles
    ) {
    }

    public record PotionSettings(boolean fastPots, double speed, Toggle asyncPots, boolean optimizedSplashDetection) {
    }

    public record PearlSettings(
        boolean talibanPearls,
        double passThroughDistance,
        boolean partialBlocks,
        boolean fences,
        boolean stairsAndSlabs,
        boolean trapdoors,
        boolean chests,
        boolean tripwire
    ) {
    }

    public record TntSettings(
        Toggle recodedMechanics,
        int fuseTicks,
        boolean randomizeExplosionChainFuse,
        boolean stableInitialMotion,
        Toggle keepChunksLoaded,
        int keepChunksLoadedRadius,
        boolean disableLeftShooting,
        boolean efficientWallDamage,
        int maxActivePerWorld
    ) {
    }

    public record SpawnerSettings(boolean enabled, SpawnerChecks checks, boolean ignoreRules, int minSpawnDelay, int maxSpawnDelay) {
    }

    public record SpawnerChecks(
        boolean lightLevel,
        boolean spawnerMaxNearby,
        boolean checkForNearbyPlayers,
        boolean spawnerBlockChecks,
        boolean waterPreventSpawn
    ) {
    }

    public record WorldConfigurationSettings(WorldSettings defaults, Map<String, WorldSettings> worlds) {
    }

    public record WorldSettings(
        SugarCaneSettings sugarCane,
        OreSettings ores,
        CaveSettings caves,
        BiomeSettings biomes,
        CustomBlockRuleSettings customBlockRules
    ) {
    }

    public record SugarCaneSettings(boolean enabled, double growthMultiplier, int maxHeight) {
    }

    public record OreSettings(boolean enabled, double generationMultiplier, Map<String, String> replacements) {
    }

    public record CaveSettings(boolean enabled, Map<String, String> modifications) {
    }

    public record BiomeSettings(Map<String, String> swaps, Map<String, String> regionalSwaps) {
    }

    public record CustomBlockRuleSettings(boolean enabled, Map<String, String> rules) {
    }

    public record SecuritySettings(
        NbtProtectionSettings nbtProtection,
        boolean blockWorldDownloader,
        PacketFloodSettings packetFlood,
        RedstoneAbuseLimiterSettings redstoneAbuseLimiter,
        MovementCheckSettings russianCrasherMovementCheck,
        RaytraceXraySettings raytraceXray
    ) {
    }

    public record NbtProtectionSettings(
        boolean enabled,
        boolean nbtSkipProtector,
        boolean kickOnViolation,
        boolean readNbtFromPlacedBlocks,
        int maxItemComponents,
        int maxBookPages,
        int maxBookPageLength,
        int maxBookTotalLength,
        int maxCustomPayloadBytes,
        boolean blockLecternBookData,
        boolean blockContainerItems
    ) {
    }

    public record PacketFloodSettings(boolean enabled, int maxPacketsPerSecond, int burst, boolean kickOnViolation) {
    }

    public record RedstoneAbuseLimiterSettings(boolean enabled, int maxUpdatesPerTick) {
    }

    public record MovementCheckSettings(boolean enabled, int maxMovePacketsPerTick) {
    }

    public record RaytraceXraySettings(
        boolean enabled,
        int updateRadius,
        int updateIntervalTicks,
        double maxRayDistance,
        int maxOresPerScan,
        FakeOreSettings fakeOres
    ) {
    }

    public record FakeOreSettings(
        boolean enabled,
        int radius,
        double chance,
        int attempts,
        int maxPerPlayer,
        int expireTicks,
        int disappearRadius
    ) {
    }

    public record NetworkingSettings(
        boolean flushConsolidation,
        boolean tcpNoDelay,
        int ipTos,
        TcpFastOpenSettings tcpFastOpen,
        boolean explicitFlush,
        boolean asyncCommandPacketBuild
    ) {
    }

    public record TcpFastOpenSettings(boolean enabled, int queueSize) {
    }

    public record ChunkSettings(
        boolean preventLoadingForHoppers,
        boolean preventLoadingForLights,
        boolean preventLoadingForFurnaces,
        boolean optimizedEntityLookups,
        boolean reduceHashing,
        boolean optimizeUnloading,
        boolean optimizeCalculations
    ) {
    }

    public record TickingSettings(TickingMode chunkMode, boolean chunkTicking, boolean weatherTicking, boolean villagerTicking) {
    }

    public enum TickingMode {
        VANILLA,
        OPTIMIZED,
        EXTRA_OPTIMIZED
    }

    public record EntitySettings(
        MobSpawnerRuntimeSettings mobSpawners,
        int searchDelayTicks,
        int aiGoalSelectorThrottleTicks,
        boolean mobTracker,
        boolean entityCollisions,
        boolean mobAi,
        boolean mobItemPickup,
        boolean babyMobSpawnFromSpawners,
        boolean mobSpawning
    ) {
    }

    public record MobSpawnerRuntimeSettings(boolean partialAsync) {
    }

    public record WorldRuntimeSettings(
        boolean hopperOptimization,
        boolean savePlayerData,
        boolean alwaysDay,
        boolean alwaysPrettyWeather,
        SpawnerRangeSettings spawners
    ) {
    }

    public record SpawnerRangeSettings(int spawnRange, int minRange, int maxRange, int requiredPlayerRange, int spawnCount, int maxNearbyEntities) {
    }

    public record GeneralSettings(
        boolean footstepSounds,
        boolean playerMoveEvent,
        boolean deathScreen,
        boolean asyncCatcher,
        boolean styledPluginList,
        boolean optimizedHitDetection
    ) {
    }

    public record DiagnosticsSettings(LagSpikeSettings lagSpike, TpsGraphSettings tpsGraph) {
    }

    public record LagSpikeSettings(boolean enabled, double thresholdMs, int logCooldownSeconds) {
    }

    public record TpsGraphSettings(boolean enabled, int seconds, int height) {
    }

    public record MiscellaneousSettings(Toggle secureSeed) {
    }

    public record ThemeSettings(String brandName, String guiName, String mainColor, String secondaryColor, String titleColor) {
    }
}
