package dev.systemlink.spigot.command;

import dev.systemlink.spigot.diagnostics.LagSpikeDetector;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Controls the runtime lag-spike detector. */
public final class LagSpikeCommand extends Command {
    public LagSpikeCommand() {
        super("lagspike");
        this.description = "Control the mSpigot lag-spike detector";
        this.usageMessage = "/lagspike <enable|disable|status>";
        this.setPermission("mspigot.command.lagspike");
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String label, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            this.status(sender);
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return true;
        }
        if (args[0].equalsIgnoreCase("enable")) {
            LagSpikeDetector.setEnabled(true);
        } else if (args[0].equalsIgnoreCase("disable")) {
            LagSpikeDetector.setEnabled(false);
        } else {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return true;
        }
        this.status(sender);
        return true;
    }

    private void status(final CommandSender sender) {
        final boolean enabled = LagSpikeDetector.enabled();
        sender.sendMessage(text("Lag-spike detector is ", NamedTextColor.YELLOW)
            .append(text(enabled ? "enabled" : "disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(text(" (tick threshold " + LagSpikeDetector.thresholdMillis() + " ms; synchronous chunks "
                + (LagSpikeDetector.synchronousChunkDetectionEnabled()
                    ? "at " + LagSpikeDetector.synchronousChunkThresholdMillis() + " ms"
                    : "disabled")
                + ").", NamedTextColor.GRAY)));
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
        return List.of("enable", "disable", "status").stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
