package dev.systemlink.spigot.diagnostics;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import static net.kyori.adventure.text.Component.text;

/** Lightweight live TPS graph rendered in chat. */
public final class MSpigotTpsGraph {
    private static final int MAX_SECONDS = 300;
    private static final double[] SAMPLES = new double[MAX_SECONDS];
    private static int writeIndex;
    private static int sampleCount;
    private static long accumulatedNanos;
    private static int accumulatedTicks;

    private MSpigotTpsGraph() {
    }

    public static synchronized void recordTick(final int tick, final long tickNanos) {
        final MSpigotConfig.Snapshot current = MSpigotConfig.current();
        if (current != null && !current.diagnostics().tpsGraph().enabled()) {
            return;
        }
        accumulatedNanos += Math.max(1L, tickNanos);
        accumulatedTicks++;
        if (accumulatedTicks < 20 && tick % 20 != 0) {
            return;
        }

        final double tps = Math.min(20.0, accumulatedTicks * 1_000_000_000.0 / accumulatedNanos);
        SAMPLES[writeIndex] = tps;
        writeIndex = (writeIndex + 1) % SAMPLES.length;
        sampleCount = Math.min(sampleCount + 1, SAMPLES.length);
        accumulatedNanos = 0L;
        accumulatedTicks = 0;
    }

    public static void sendGraph(final CommandSender sender) {
        final MSpigotConfig.TpsGraphSettings settings = MSpigotConfig.get().diagnostics().tpsGraph();
        if (!settings.enabled()) {
            sender.sendMessage(text("The mSpigot TPS graph is disabled in mSpigot.yml.", NamedTextColor.RED));
            return;
        }

        final int seconds = Math.min(settings.seconds(), MAX_SECONDS);
        final int height = settings.height();
        final double[] samples = snapshot(seconds);
        sender.sendMessage(text("TPS Graph (" + seconds + " Seconds)", NamedTextColor.GREEN));
        if (samples.length == 0) {
            sender.sendMessage(text("Collecting TPS samples...", NamedTextColor.GRAY));
            return;
        }

        final int padding = seconds - samples.length;
        for (int row = height; row >= 1; row--) {
            final double threshold = 20.0 * row / height;
            final TextComponent.Builder line = text();
            for (int i = 0; i < seconds; i++) {
                if (i < padding) {
                    line.append(text('|', NamedTextColor.DARK_GRAY));
                    continue;
                }
                final double tps = samples[i - padding];
                line.append(text(tps >= threshold ? '|' : ' ', color(tps)));
            }
            sender.sendMessage(line.build());
        }

        final double latest = samples[samples.length - 1];
        sender.sendMessage(text("Server Status: ", NamedTextColor.WHITE)
            .append(text(status(latest), color(latest))));
    }

    private static synchronized double[] snapshot(final int seconds) {
        final int length = Math.min(Math.min(seconds, sampleCount), SAMPLES.length);
        final double[] values = new double[length];
        for (int i = 0; i < length; i++) {
            final int index = Math.floorMod(writeIndex - length + i, SAMPLES.length);
            values[i] = SAMPLES[index];
        }
        return values;
    }

    private static NamedTextColor color(final double tps) {
        if (tps >= 18.0) {
            return NamedTextColor.GREEN;
        }
        if (tps >= 15.0) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.RED;
    }

    private static String status(final double tps) {
        if (tps >= 18.0) {
            return "STABLE";
        }
        if (tps >= 15.0) {
            return "WARNING";
        }
        return "LAGGING";
    }
}
