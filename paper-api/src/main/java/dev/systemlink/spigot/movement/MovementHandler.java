package dev.systemlink.spigot.movement;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Receives decoded player location and rotation updates before vanilla movement
 * processing and before {@code PlayerMoveEvent}.
 *
 * <p>These callbacks run on the server thread. Call
 * {@link MovementPacketWrapper#cancel()} to reject the current update.</p>
 */
public interface MovementHandler {

    /**
     * Called when a movement packet changes a player's position.
     *
     * @param player                moving player
     * @param from                  current player location
     * @param to                    requested player location
     * @param movementPacketWrapper packet details and cancellation control
     */
    default void onUpdateLocation(
        final Player player,
        final Location from,
        final Location to,
        final MovementPacketWrapper movementPacketWrapper
    ) {
    }

    /**
     * Called when a movement packet changes a player's rotation.
     *
     * @param player                moving player
     * @param from                  current player location
     * @param to                    requested player location
     * @param movementPacketWrapper packet details and cancellation control
     */
    default void onUpdateRotation(
        final Player player,
        final Location from,
        final Location to,
        final MovementPacketWrapper movementPacketWrapper
    ) {
    }
}
