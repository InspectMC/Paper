package dev.systemlink.spigot.internal;

import dev.systemlink.spigot.movement.MovementHandler;
import dev.systemlink.spigot.movement.MovementType;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Normal
class MovementServiceImplTest {

    @Test
    void movementHandlersCanCancelLocationUpdates() {
        final MovementServiceImpl service = new MovementServiceImpl();
        final Plugin plugin = enabledPlugin();
        final Player player = mock(Player.class);
        final World world = mock(World.class);
        final Object packet = new Object();
        final AtomicBoolean called = new AtomicBoolean();

        service.registerMovementHandler(plugin, new MovementHandler() {
            @Override
            public void onUpdateLocation(final Player player, final Location from, final Location to, final dev.systemlink.spigot.movement.MovementPacketWrapper movementPacketWrapper) {
                called.set(true);
                from.setX(100.0);
                movementPacketWrapper.cancel();
            }
        });

        final Location from = new Location(world, 1.0, 2.0, 3.0);
        final Location to = new Location(world, 4.0, 5.0, 6.0);
        assertFalse(service.callMovement(player, MovementType.LOCATION, from, to, packet));
        assertTrue(called.get());
        assertEquals(1.0, from.getX());
    }

    @Test
    void disabledPluginsAreSkipped() {
        final MovementServiceImpl service = new MovementServiceImpl();
        final Plugin plugin = mock(Plugin.class);
        final AtomicBoolean called = new AtomicBoolean();

        service.registerMovementHandler(plugin, new MovementHandler() {
            @Override
            public void onUpdateRotation(final Player player, final Location from, final Location to, final dev.systemlink.spigot.movement.MovementPacketWrapper movementPacketWrapper) {
                called.set(true);
            }
        });

        assertTrue(service.callMovement(mock(Player.class), MovementType.ROTATION, new Location(mock(World.class), 0, 0, 0), new Location(mock(World.class), 0, 0, 0), new Object()));
        assertFalse(called.get());
    }

    private static Plugin enabledPlugin() {
        final Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}
