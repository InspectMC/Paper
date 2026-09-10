package dev.systemlink.spigot;

import dev.systemlink.spigot.movement.MovementHandler;
import dev.systemlink.spigot.movement.MovementService;
import dev.systemlink.spigot.packet.PacketHandler;
import dev.systemlink.spigot.packet.PacketService;
import org.bukkit.plugin.Plugin;

/**
 * Entry point for SystemLink-specific server APIs.
 */
public interface MSpigot {

    /**
     * Gets the packet handler service.
     *
     * @return the packet service
     */
    PacketService getPacketService();

    /**
     * Gets the movement handler service.
     *
     * @return the movement service
     */
    MovementService getMovementService();

    /**
     * Registers a packet handler owned by a plugin.
     *
     * @param plugin        owner of the handler
     * @param packetHandler handler to register
     */
    default void registerPacketHandler(final Plugin plugin, final PacketHandler packetHandler) {
        this.getPacketService().registerPacketHandler(plugin, packetHandler);
    }

    /**
     * Unregisters a packet handler.
     *
     * @param packetHandler handler to unregister
     */
    default void unregisterPacketHandler(final PacketHandler packetHandler) {
        this.getPacketService().unregisterPacketHandler(packetHandler);
    }

    /**
     * Registers a movement handler owned by a plugin.
     *
     * @param plugin          owner of the handler
     * @param movementHandler handler to register
     */
    default void registerMovementHandler(final Plugin plugin, final MovementHandler movementHandler) {
        this.getMovementService().registerMovementHandler(plugin, movementHandler);
    }

    /**
     * Unregisters a movement handler.
     *
     * @param movementHandler handler to unregister
     */
    default void unregisterMovementHandler(final MovementHandler movementHandler) {
        this.getMovementService().unregisterMovementHandler(movementHandler);
    }
}
