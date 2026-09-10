package org.bukkit.command.defaults;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true) // Paper
public class PluginsCommand extends BukkitCommand {
    public PluginsCommand(@NotNull String name) {
        super(name);
        this.description = "Gets a list of plugins running on the server";
        this.usageMessage = "/plugins";
        this.setPermission("bukkit.command.plugins");
        this.setAliases(Arrays.asList("pl"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String currentAlias, @NotNull String[] args) {
        if (!testPermission(sender)) return true;

        sender.sendMessage(Component.text("The server has ", NamedTextColor.WHITE)
            .append(Component.text(Bukkit.getPluginManager().getPlugins().length, NamedTextColor.GREEN))
            .append(Component.text(Bukkit.getPluginManager().getPlugins().length == 1 ? " plugin:" : " plugins:", NamedTextColor.WHITE)));
        for (final Plugin plugin : getSortedPlugins()) {
            sender.sendMessage(formatPlugin(plugin));
        }
        return true;
    }

    @NotNull
    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        return Collections.emptyList();
    }

    private static List<Plugin> getSortedPlugins() {
        final TreeMap<String, Plugin> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            sorted.put(plugin.getName(), plugin);
        }
        return List.copyOf(sorted.values());
    }

    private static Component formatPlugin(final Plugin plugin) {
        final String version = plugin.getDescription().getVersion();
        final Component hover = Component.text()
            .append(Component.text(plugin.getName() + " ", plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.text("(v" + version + ")", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text(plugin.getDescription().getDescription() == null ? "No description." : plugin.getDescription().getDescription(), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Authors: ", NamedTextColor.GRAY))
            .append(Component.text(plugin.getDescription().getAuthors().isEmpty() ? "Unknown" : String.join(", ", plugin.getDescription().getAuthors()), NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("Status: ", NamedTextColor.GRAY))
            .append(Component.text(plugin.isEnabled() ? "Enabled" : "Disabled", plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
            .build();
        return Component.text("✔ ", plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)
            .append(Component.text(plugin.getName(), plugin.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED))
            .append(Component.text(" (v" + version + ")", NamedTextColor.WHITE))
            .hoverEvent(hover)
            .clickEvent(ClickEvent.runCommand("/version " + plugin.getName()));
    }
}
