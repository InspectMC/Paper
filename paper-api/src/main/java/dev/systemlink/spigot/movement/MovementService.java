package dev.systemlink.spigot.movement;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Registry and dispatcher for {@link MovementHandler movement handlers}.
 */
public interface MovementService {

    /**
     * Registers a handler owned by a plugin. Registering the same handler
     * instance for the same plugin more than once has no effect.
     *
     * @param plugin          owner of the handler
     * @param movementHandler handler to register
     * @throws IllegalArgumentException if the handler is already owned by another plugin
     */
    void registerMovementHandler(Plugin plugin, MovementHandler movementHandler);

    /**
     * Unregisters a handler instance.
     *
     * @param movementHandler handler to unregister
     */
    void unregisterMovementHandler(MovementHandler movementHandler);

    /**
     * Gets an immutable snapshot of handlers owned by a plugin.
     *
     * @param plugin plugin whose handlers to retrieve
     * @return registered handlers in invocation order
     */
    List<MovementHandler> getMovementHandlersFor(Plugin plugin);

    /**
     * Gets an immutable snapshot of all registered handlers.
     *
     * @return registered handlers in invocation order
     */
    List<MovementHandler> getMovementHandlers();

    /**
     * Unregisters all handlers owned by a plugin.
     *
     * @param plugin plugin whose handlers to unregister
     */
    void unregisterMovementHandlerFor(Plugin plugin);

    /**
     * Invokes handlers for one component of a movement packet.
     *
     * <p>This is an advanced API. Calling it does not itself move the player.</p>
     *
     * @param player             moving player
     * @param type               movement component to dispatch
     * @param from               current player location
     * @param to                 requested player location
     * @param packetPlayInFlying underlying version-specific movement packet
     * @return {@code false} if a handler cancelled the movement
     */
    boolean callMovement(Player player, MovementType type, Location from, Location to, Object packetPlayInFlying);
}
