package dev.systemlink.spigot.command;

import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Changes the current runtime player-slot limit. */
public final class SetMaxSlotCommand extends Command {
    public SetMaxSlotCommand() {
        super("setmaxslot");
        this.description = "Set the current maximum player count";
        this.usageMessage = "/setmaxslot <number>";
        this.setPermission("mspigot.command.setmaxslot");
        this.setAliases(List.of("setslot", "setslots"));
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String label, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
            return true;
        }
        final int slots;
        try {
            slots = Integer.parseInt(args[0]);
        } catch (final NumberFormatException exception) {
            sender.sendMessage(text("The maximum player count must be a whole number.", NamedTextColor.RED));
            return true;
        }
        if (slots < 0) {
            sender.sendMessage(text("The maximum player count cannot be negative.", NamedTextColor.RED));
            return true;
        }
        Bukkit.getServer().setMaxPlayers(slots);
        sender.sendMessage(text("Maximum player count set to " + slots + " for this runtime.", NamedTextColor.GREEN));
        return true;
    }
}
