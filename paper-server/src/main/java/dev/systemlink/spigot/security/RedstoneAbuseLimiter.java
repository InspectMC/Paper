package dev.systemlink.spigot.security;

import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/** Cheap per-tick limiter for pathological redstone update storms. */
public final class RedstoneAbuseLimiter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int tick = -1;
    private static int updates;
    private static boolean logged;

    private RedstoneAbuseLimiter() {
    }

    public static boolean tryAcquire(final BlockPos pos) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null || !snapshot.security().redstoneAbuseLimiter().enabled()) {
            return true;
        }
        final int limit = snapshot.security().redstoneAbuseLimiter().maxUpdatesPerTick();
        if (limit <= 0) {
            return true;
        }
        final int currentTick = MinecraftServer.currentTick;
        synchronized (RedstoneAbuseLimiter.class) {
            if (tick != currentTick) {
                tick = currentTick;
                updates = 0;
                logged = false;
            }
            if (++updates <= limit) {
                return true;
            }
            if (!logged) {
                LOGGER.warn("mSpigot redstone abuse limiter suppressed wire updates after {} updates this tick near {}", limit, pos.toShortString());
                logged = true;
            }
            return false;
        }
    }
}
