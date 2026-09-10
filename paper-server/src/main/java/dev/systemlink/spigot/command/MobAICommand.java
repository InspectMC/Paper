package dev.systemlink.spigot.command;

import dev.systemlink.spigot.entity.MobAIController;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Suspends or resumes mob AI globally without rewriting entity NBT. */
public final class MobAICommand extends Command {
    public MobAICommand() {
        super("mobai");
        this.description = "Toggle mob AI processing";
        this.usageMessage = "/mobai [enable|disable|toggle|status]";
        this.setPermission("mspigot.command.mobai");
        this.setAliases(List.of("ai", "toggleai", "togglemobai"));
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

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")) {
            MobAIController.toggle();
        } else if (args[0].equalsIgnoreCase("enable")) {
            MobAIController.setEnabled(true);
        } else if (args[0].equalsIgnoreCase("disable")) {
            MobAIController.setEnabled(false);
        } else if (!args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return true;
        }

        final boolean enabled = MobAIController.enabled();
        sender.sendMessage(text("Mob AI is ", NamedTextColor.YELLOW)
            .append(text(enabled ? "enabled" : "disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(text(".", NamedTextColor.YELLOW)));
        return true;
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
        return List.of("enable", "disable", "toggle", "status").stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
