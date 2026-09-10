package dev.systemlink.spigot.command;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Reports the measured connection latency of the sender or a target player. */
public final class PingCommand extends Command {
    public PingCommand() {
        super("ping");
        this.description = "Check a player's connection latency";
        this.usageMessage = "/ping [player]";
        this.setPermission("mspigot.command.ping");
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String label, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return true;
        }

        final Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(text("Console must specify a player: /ping <player>", NamedTextColor.RED));
                return true;
            }
            target = player;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null || sender instanceof Player player && !player.canSee(target)) {
                sender.sendMessage(text("Player not found.", NamedTextColor.RED));
                return true;
            }
        }

        if (sender == target) {
            sender.sendMessage(text("Your ping is ", NamedTextColor.YELLOW)
                .append(text(target.getPing() + " ms", color(target.getPing()))).append(text(".", NamedTextColor.YELLOW)));
        } else {
            sender.sendMessage(text(target.getName() + "'s ping is ", NamedTextColor.YELLOW)
                .append(text(target.getPing() + " ms", color(target.getPing()))).append(text(".", NamedTextColor.YELLOW)));
        }
        return true;
    }

    private static NamedTextColor color(final int ping) {
        return ping < 100 ? NamedTextColor.GREEN : ping < 200 ? NamedTextColor.YELLOW : NamedTextColor.RED;
    }

    @Override
    public @NotNull List<String> tabComplete(
        final @NotNull CommandSender sender,
        final @NotNull String alias,
        final @NotNull String[] args
    ) throws IllegalArgumentException {
        if (!this.testPermissionSilent(sender) || args.length != 1) {
            return List.of();
        }
        final String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
            .filter(target -> !(sender instanceof Player player) || player.canSee(target))
            .map(Player::getName)
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .toList();
    }
}
