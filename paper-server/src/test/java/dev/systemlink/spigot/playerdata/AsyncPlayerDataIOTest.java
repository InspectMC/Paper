package dev.systemlink.spigot.playerdata;

import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Normal
class AsyncPlayerDataIOTest {
    @Test
    void preservesSaveAndLoadOrder(@TempDir final Path temporaryDirectory) {
        final AsyncPlayerDataIO io = new AsyncPlayerDataIO(temporaryDirectory);
        final CompoundTag first = new CompoundTag();
        first.putString("value", "first");
        final CompoundTag second = new CompoundTag();
        second.putString("value", "second");

        io.saveAsync("player", "Player", first);
        io.saveAsync("player", "Player", second);

        final CompoundTag loaded = io.loadAsync(() -> {
            try {
                return NbtIo.readCompressed(temporaryDirectory.resolve("player.dat"), NbtAccounter.unlimitedHeap());
            } catch (final Exception exception) {
                throw new IllegalStateException(exception);
            }
        }).join();

        assertEquals("second", loaded.getString("value").orElseThrow());
    }
}
