package dev.systemlink.spigot.entity;

import java.util.concurrent.atomic.AtomicBoolean;

/** Runtime switch used to suspend mob AI without modifying persistent entity data. */
public final class MobAIController {
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);

    private MobAIController() {
    }

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static boolean toggle() {
        boolean current;
        boolean updated;
        do {
            current = ENABLED.get();
            updated = !current;
        } while (!ENABLED.compareAndSet(current, updated));
        return updated;
    }

    public static void setEnabled(final boolean enabled) {
        ENABLED.set(enabled);
    }
}
