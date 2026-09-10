package org.spigotmc;

import net.minecraft.server.MinecraftServer;

public class AsyncCatcher {
    public static volatile boolean enabled = true; // mSpigot

    public static void catchOp(String reason) {
        if (!enabled) return; // mSpigot
        if (!ca.spottedleaf.moonrise.common.util.TickThread.isTickThread()) { // Paper - chunk system
            MinecraftServer.LOGGER.error("Thread {} failed main thread check: {}", Thread.currentThread().getName(), reason, new Throwable()); // Paper
            throw new IllegalStateException("Asynchronous " + reason + "!");
        }
    }
}
