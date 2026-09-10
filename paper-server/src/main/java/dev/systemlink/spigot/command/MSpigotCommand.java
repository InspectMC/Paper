package dev.systemlink.spigot.command;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import dev.systemlink.spigot.diagnostics.LagSpikeDetector;
import dev.systemlink.spigot.entity.MobAIController;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Root command for mSpigot runtime and configuration controls. */
public final class MSpigotCommand extends Command {
    public MSpigotCommand() {
        super("mspigot");
        this.description = "View and reload mSpigot configuration";
        this.usageMessage = "/mspigot <status|reload|world>";
        this.setPermission("mspigot.command.mspigot");
        this.setAliases(List.of("msp"));
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String label, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            return this.status(sender);
        }
        if (args[0].equalsIgnoreCase("reload") && args.length == 1) {
            if (!sender.hasPermission("mspigot.command.mspigot.reload")) {
                sender.sendMessage(text("You do not have permission to reload mSpigot.", NamedTextColor.RED));
                return true;
            }
            try {
                MSpigotConfig.reload();
                sender.sendMessage(text("Reloaded mSpigot.yml and knockback.yml.", NamedTextColor.GREEN));
            } catch (final IllegalStateException exception) {
                sender.sendMessage(text("Could not reload mSpigot configuration: " + exception.getMessage(), NamedTextColor.RED));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("world") && args.length <= 2) {
            final World world;
            if (args.length == 2) {
                world = Bukkit.getWorld(args[1]);
            } else if (sender instanceof Player player) {
                world = player.getWorld();
            } else {
                sender.sendMessage(text("Usage: /mspigot world <world>", NamedTextColor.RED));
                return true;
            }
            if (world == null) {
                sender.sendMessage(text("Unknown loaded world.", NamedTextColor.RED));
                return true;
            }
            return this.world(sender, world);
        }

        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        return true;
    }

    private boolean status(final CommandSender sender) {
        sender.sendMessage(text("mSpigot status", NamedTextColor.AQUA));
        sender.sendMessage(text("Lag-spike detector: ", NamedTextColor.YELLOW)
            .append(text(LagSpikeDetector.enabled() ? "enabled" : "disabled", LagSpikeDetector.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(text("Mob AI: ", NamedTextColor.YELLOW)
            .append(text(MobAIController.enabled() ? "enabled" : "disabled", MobAIController.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        sender.sendMessage(text("Knockback profiles: ", NamedTextColor.YELLOW)
            .append(text(String.valueOf(MSpigotConfig.knockbackProfileNames().size()), NamedTextColor.GRAY)));
        return true;
    }

    private boolean world(final CommandSender sender, final World world) {
        final MSpigotConfig.WorldSettings settings = MSpigotConfig.worldSettings(world.getName());
        sender.sendMessage(text("World configuration: ", NamedTextColor.YELLOW).append(text(world.getName(), NamedTextColor.AQUA)));
        sender.sendMessage(text("Knockback profile: ", NamedTextColor.YELLOW)
            .append(text(MSpigotConfig.knockbackProfileName(world.getName()), NamedTextColor.GRAY)));
        sender.sendMessage(text("Sugar cane: ", NamedTextColor.YELLOW)
            .append(text(settings.sugarCane().enabled() ? "enabled" : "disabled", NamedTextColor.GRAY))
            .append(text(" (x" + settings.sugarCane().growthMultiplier() + ")", NamedTextColor.GRAY)));
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
        final @NotNull CommandSender sender,
        final @NotNull String alias,
        final @NotNull String[] args
    ) throws IllegalArgumentException {
        if (!this.testPermissionSilent(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return complete(args[0], List.of("status", "reload", "world"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
            return complete(args[1], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return List.of();
    }

    private static List<String> complete(final String input, final List<String> values) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
