package dev.systemlink.spigot.command;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import dev.systemlink.spigot.knockback.AdvancedKnockbackConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import static net.kyori.adventure.text.Component.text;

/** Manages the knockback profiles stored in {@code knockback.yml}. */
public final class KnockbackCommand extends Command {
    private static final String EDIT_PERMISSION = "mspigot.command.knockback.edit";
    private static final List<String> SUBCOMMANDS = List.of("list", "view", "create", "delete", "set", "default", "assign", "reload");

    public KnockbackCommand() {
        super("knockback");
        this.description = "Manage mSpigot knockback profiles";
        this.usageMessage = "/knockback <list|view|create|delete|set|default|assign|reload>";
        this.setPermission("mspigot.command.knockback");
        this.setAliases(List.of("kb"));
    }

    @Override
    public boolean execute(final @NotNull CommandSender sender, final @NotNull String commandLabel, final @NotNull String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0) {
            this.sendUsage(sender, commandLabel);
            return true;
        }

        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> this.list(sender);
                case "view" -> this.view(sender, args);
                case "create" -> this.create(sender, args);
                case "delete" -> this.delete(sender, args);
                case "set" -> this.set(sender, args);
                case "default" -> this.setDefault(sender, args);
                case "assign" -> this.assign(sender, args);
                case "reload" -> this.reload(sender, args);
                default -> {
                    this.sendUsage(sender, commandLabel);
                    yield true;
                }
            };
        } catch (final IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage(text(exception.getMessage(), NamedTextColor.RED));
            return true;
        }
    }

    private boolean list(final CommandSender sender) {
        final MSpigotConfig.KnockbackSettings settings = MSpigotConfig.get().gameplay().knockback();
        sender.sendMessage(text("Knockback profiles: ", NamedTextColor.YELLOW)
            .append(text(String.join(", ", settings.profiles().keySet()), NamedTextColor.GRAY)));
        sender.sendMessage(text("Default profile: ", NamedTextColor.YELLOW)
            .append(text(settings.defaultProfile(), NamedTextColor.AQUA)));
        return true;
    }

    private boolean view(final CommandSender sender, final String[] args) {
        if (args.length > 2) {
            throw new IllegalArgumentException("Usage: /knockback view [profile]");
        }
        final MSpigotConfig.KnockbackSettings settings = MSpigotConfig.get().gameplay().knockback();
        final String name = args.length == 2 ? args[1].toLowerCase(Locale.ROOT) : settings.defaultProfile();
        final AdvancedKnockbackConfiguration profile = settings.profiles().get(name);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown knockback profile '" + name + "'");
        }

        sender.sendMessage(text("Viewing profile: ", NamedTextColor.YELLOW).append(text(name, NamedTextColor.AQUA)));
        this.value(sender, "Enabled", profile.enabled);
        this.value(sender, "BaseKnockback", profile.baseKnockback);
        this.value(sender, "KnockbackResistanceModifier", profile.knockbackResistanceModifier);
        this.value(sender, "KnockbackVertical", profile.knockbackVertical.value().isPresent() ? profile.knockbackVertical.doubleValue() : "default");
        this.value(sender, "KnockbackVerticalLimit", profile.knockbackVerticalLimit);
        this.value(sender, "ShieldHitKnockback", profile.shieldHitKnockback);
        this.value(sender, "ExtraKnockback", profile.extraKnockback);
        this.value(sender, "RequireFullAttack", profile.requireFullAttack);
        this.value(sender, "SweepingEdgeKnockback", profile.sweepingEdgeKnockback);
        this.value(sender, "VerticalKnockbackRequireGround", profile.verticalKnockbackRequireGround);
        return true;
    }

    private boolean create(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: /knockback create <profile> [source-profile]");
        }
        final String source = args.length == 3 ? args[2] : MSpigotConfig.get().gameplay().knockback().defaultProfile();
        MSpigotConfig.createKnockbackProfile(args[1], source);
        sender.sendMessage(text("Created knockback profile '" + args[1].toLowerCase(Locale.ROOT) + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean delete(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: /knockback delete <profile>");
        }
        MSpigotConfig.deleteKnockbackProfile(args[1]);
        sender.sendMessage(text("Deleted knockback profile '" + args[1].toLowerCase(Locale.ROOT) + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean set(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: /knockback set <profile> <setting> <value>");
        }
        final String value = MSpigotConfig.setKnockbackProfileValue(args[1], args[2], args[3]);
        sender.sendMessage(text("Set " + args[1].toLowerCase(Locale.ROOT) + "." + args[2].toLowerCase(Locale.ROOT) + " to " + value + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean setDefault(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: /knockback default <profile>");
        }
        MSpigotConfig.setDefaultKnockbackProfile(args[1]);
        sender.sendMessage(text("Default knockback profile is now '" + args[1].toLowerCase(Locale.ROOT) + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean assign(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: /knockback assign <world> <profile>");
        }
        if (Bukkit.getWorld(args[1]) == null) {
            throw new IllegalArgumentException("Unknown loaded world '" + args[1] + "'");
        }
        MSpigotConfig.assignKnockbackProfile(args[1], args[2]);
        sender.sendMessage(text("World '" + args[1] + "' now uses knockback profile '" + args[2].toLowerCase(Locale.ROOT) + "'.", NamedTextColor.GREEN));
        return true;
    }

    private boolean reload(final CommandSender sender, final String[] args) {
        this.requireEditPermission(sender);
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: /knockback reload");
        }
        MSpigotConfig.reload();
        sender.sendMessage(text("Reloaded mSpigot.yml and knockback.yml.", NamedTextColor.GREEN));
        return true;
    }

    private void requireEditPermission(final CommandSender sender) {
        if (!sender.hasPermission(EDIT_PERMISSION)) {
            throw new IllegalArgumentException("You do not have permission to edit knockback profiles.");
        }
    }

    private void value(final CommandSender sender, final String label, final Object value) {
        sender.sendMessage(text(label + ": ", NamedTextColor.YELLOW).append(text(String.valueOf(value), NamedTextColor.GRAY)));
    }

    private void sendUsage(final CommandSender sender, final String label) {
        sender.sendMessage(text("Knockback commands", NamedTextColor.YELLOW));
        sender.sendMessage(text("/" + label + " list", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " view [profile]", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " create <profile> [source-profile]", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " delete <profile>", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " set <profile> <setting> <value>", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " default <profile>", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " assign <world> <profile>", NamedTextColor.GRAY));
        sender.sendMessage(text("/" + label + " reload", NamedTextColor.GRAY));
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
            return matches(args[0], SUBCOMMANDS);
        }

        final String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (subcommand) {
                case "view", "delete", "set", "default" -> matches(args[1], MSpigotConfig.knockbackProfileNames());
                case "assign" -> matches(args[1], Bukkit.getWorlds().stream().map(World::getName).toList());
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (subcommand) {
                case "create", "assign" -> matches(args[2], MSpigotConfig.knockbackProfileNames());
                case "set" -> matches(args[2], Arrays.stream(MSpigotConfig.KnockbackSetting.values()).map(MSpigotConfig.KnockbackSetting::path).toList());
                default -> List.of();
            };
        }
        if (args.length == 4 && subcommand.equals("set")) {
            try {
                final MSpigotConfig.KnockbackSetting setting = MSpigotConfig.KnockbackSetting.parse(args[2]);
                return switch (setting) {
                    case ENABLED, REQUIRE_FULL_ATTACK, VERTICAL_KNOCKBACK_REQUIRE_GROUND -> matches(args[3], List.of("true", "false"));
                    case KNOCKBACK_VERTICAL -> matches(args[3], List.of("default"));
                    default -> List.of();
                };
            } catch (final IllegalArgumentException ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private static List<String> matches(final String input, final Iterable<String> values) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }
}
