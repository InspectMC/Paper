package dev.systemlink.spigot.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

@VanillaFeature
class BlockAccessCacheTest {
    @Test
    void resolvesRegisteredAndInvalidIds() {
        final BlockAccessCache cache = new BlockAccessCache();
        final int stoneId = BuiltInRegistries.BLOCK.getId(Blocks.STONE);

        assertSame(Blocks.STONE, cache.getById(stoneId));
        assertSame(Blocks.STONE, Block.byId(stoneId));
        assertSame(Blocks.AIR, cache.getById(-1));
        assertSame(Blocks.AIR, cache.getById(Integer.MAX_VALUE));
    }
}
