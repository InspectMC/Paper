package dev.systemlink.spigot.command;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Runtime controls for mSpigot fast-pot settings. */
public final class PotSpeedCommand extends Command {
    public PotSpeedCommand() {
        super("potspeed");
        this.description = "View and configure mSpigot potion throw speed";
        this.usageMessage = "/potspeed <status|set|reset|fastpots|async>";
        this.setPermission("mspigot.command.potspeed");
        this.setAliases(List.of("pot", "potionspeed", "fastpots"));
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String label, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            return this.status(sender);
        }
        try {
            if (args[0].equalsIgnoreCase("set") && args.length == 2) {
                final double speed = Double.parseDouble(args[1]);
                MSpigotConfig.setPotionSpeed(speed);
                sender.sendMessage(text("Potion throw speed set to " + speed + " and fast pots enabled.", NamedTextColor.GREEN));
                return true;
            }
            if (args[0].equalsIgnoreCase("reset") && args.length == 1) {
                MSpigotConfig.setPotionSpeed(0.5);
                MSpigotConfig.setFastPotsEnabled(false);
                sender.sendMessage(text("Potion throw speed reset to vanilla and fast pots disabled.", NamedTextColor.GREEN));
                return true;
            }
            if (args[0].equalsIgnoreCase("fastpots") && args.length == 2) {
                final boolean enabled = parseBoolean(args[1]);
                MSpigotConfig.setFastPotsEnabled(enabled);
                sender.sendMessage(text("Fast pots " + (enabled ? "enabled." : "disabled."), enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return true;
            }
            if (args[0].equalsIgnoreCase("async") && args.length == 2) {
                final boolean enabled = parseBoolean(args[1]);
                MSpigotConfig.setAsyncPotsEnabled(enabled);
                sender.sendMessage(text("Async pot preprocessing " + (enabled ? "enabled." : "disabled."), enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return true;
            }
        } catch (final NumberFormatException exception) {
            sender.sendMessage(text("Potion speed must be a number.", NamedTextColor.RED));
            return true;
        } catch (final IllegalArgumentException exception) {
            sender.sendMessage(text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        sender.sendMessage(text("Examples: /potspeed set 0.8, /potspeed fastpots on, /potspeed async off", NamedTextColor.GRAY));
        return true;
    }

    private boolean status(final CommandSender sender) {
        final MSpigotConfig.PotionSettings settings = MSpigotConfig.get().gameplay().potions();
        sender.sendMessage(text("Potion speed: ", NamedTextColor.YELLOW)
            .append(text(String.valueOf(settings.speed()), NamedTextColor.AQUA))
            .append(text(settings.fastPots() ? " (fast pots enabled)" : " (vanilla speed active)", NamedTextColor.GRAY)));
        sender.sendMessage(text("Async pot preprocessing: ", NamedTextColor.YELLOW)
            .append(text(settings.asyncPots().enabled() ? "enabled" : "disabled", settings.asyncPots().enabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
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
            return complete(args[0], List.of("status", "set", "reset", "fastpots", "async"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("fastpots") || args[0].equalsIgnoreCase("async"))) {
            return complete(args[1], List.of("on", "off", "true", "false"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return complete(args[1], List.of("0.5", "0.7", "0.8", "1.0", "1.2"));
        }
        return List.of();
    }

    private static boolean parseBoolean(final String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "enable", "enabled" -> true;
            case "false", "off", "no", "disable", "disabled" -> false;
            default -> throw new IllegalArgumentException("Expected on/off or true/false.");
        };
    }

    private static List<String> complete(final String input, final List<String> values) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
