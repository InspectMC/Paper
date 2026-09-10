package dev.systemlink.spigot.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.NullMarked;

/**
 * Provides a dense, immutable snapshot for numeric block-registry lookups.
 *
 * <p>Modern Minecraft registries are already array-backed. This class keeps
 * the mSpigot entry point while avoiding the legacy implementation's recursive
 * growth and unbounded allocation for invalid IDs.</p>
 */
@NullMarked
public final class BlockAccessCache {
    private volatile Block[] blocks;

    /**
     * Gets the block registered with the supplied numeric ID.
     *
     * @param id numeric block registry ID
     * @return the registered block, or the registry default for an invalid ID
     */
    public Block getById(final int id) {
        Block[] snapshot = this.blocks;
        final int registrySize = BuiltInRegistries.BLOCK.size();
        if (snapshot == null || snapshot.length != registrySize) {
            snapshot = this.snapshot(registrySize);
        }
        return id >= 0 && id < snapshot.length ? snapshot[id] : BuiltInRegistries.BLOCK.byId(id);
    }

    /**
     * Rebuilds the snapshot after registry bootstrap or a registry mutation.
     */
    public void refresh() {
        synchronized (this) {
            this.blocks = this.createSnapshot();
        }
    }

    private Block[] snapshot(final int registrySize) {
        synchronized (this) {
            Block[] snapshot = this.blocks;
            if (snapshot == null || snapshot.length != registrySize) {
                snapshot = this.createSnapshot();
                this.blocks = snapshot;
            }
            return snapshot;
        }
    }

    private Block[] createSnapshot() {
        final Block[] snapshot = new Block[BuiltInRegistries.BLOCK.size()];
        for (int id = 0; id < snapshot.length; id++) {
            snapshot[id] = BuiltInRegistries.BLOCK.byId(id);
        }
        return snapshot;
    }
}
