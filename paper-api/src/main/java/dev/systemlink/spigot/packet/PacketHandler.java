package dev.systemlink.spigot.packet;

import org.bukkit.entity.Player;

/**
 * Observes packets exchanged with players and may cancel their processing.
 *
 * <p>Packet callbacks run on the thread handling the packet. Inbound callbacks
 * normally run on a Netty event-loop thread, and outbound callbacks run on the
 * thread that submits the packet. Implementations must be thread-safe and must
 * schedule unsafe Bukkit operations onto the server thread.</p>
 */
public interface PacketHandler {

    /**
     * Called before a packet is sent to a player.
     *
     * @param player target player
     * @param packet version-specific packet object
     * @return {@code true} to send the packet, or {@code false} to cancel it
     */
    default boolean onSent(final Player player, final Object packet) {
        return true;
    }

    /**
     * Called before a packet received from a player is handled by the server.
     *
     * @param player source player
     * @param packet version-specific packet object
     * @return {@code true} to handle the packet, or {@code false} to cancel it
     */
    default boolean onReceived(final Player player, final Object packet) {
        return true;
    }
}
