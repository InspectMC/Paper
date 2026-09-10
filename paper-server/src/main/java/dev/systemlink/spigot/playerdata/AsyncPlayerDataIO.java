package dev.systemlink.spigot.playerdata;

import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;
import org.slf4j.Logger;

/**
 * Ordered asynchronous disk access for player data.
 *
 * <p>A single I/O lane preserves save/load ordering while keeping compression and
 * file replacement away from the main thread.</p>
 */
public final class AsyncPlayerDataIO {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
        Thread.ofPlatform().daemon().name("SystemLink Player Data I/O").factory()
    );

    private final Path playerDirectory;

    public AsyncPlayerDataIO(final Path playerDirectory) {
        this.playerDirectory = playerDirectory;
    }

    public void saveAsync(final String uuid, final String playerName, final CompoundTag data) {
        CompletableFuture.runAsync(() -> this.saveSync(uuid, playerName, data), EXECUTOR);
    }

    public void saveSync(final String uuid, final String playerName, final CompoundTag data) {
        try {
            final Path temporaryFile = Files.createTempFile(this.playerDirectory, uuid + "-", ".dat");
            NbtIo.writeCompressed(data, temporaryFile);
            final Path playerFile = this.playerDirectory.resolve(uuid + ".dat");
            final Path oldPlayerFile = this.playerDirectory.resolve(uuid + ".dat_old");
            Util.safeReplaceFile(playerFile, temporaryFile, oldPlayerFile);
        } catch (final Exception exception) {
            LOGGER.warn("Failed to save player data for {}", playerName, exception);
        }
    }

    public <T> CompletableFuture<T> loadAsync(final Supplier<T> loader) {
        return CompletableFuture.supplyAsync(loader, EXECUTOR);
    }

    public void flush() {
        CompletableFuture.runAsync(() -> {
        }, EXECUTOR).join();
    }
}
