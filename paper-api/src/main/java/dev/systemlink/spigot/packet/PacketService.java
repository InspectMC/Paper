package dev.systemlink.spigot.packet;

import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Registry and dispatcher for {@link PacketHandler packet handlers}.
 */
public interface PacketService {

    /**
     * Registers a handler owned by a plugin. Registering the same handler
     * instance for the same plugin more than once has no effect.
     *
     * @param plugin        owner of the handler
     * @param packetHandler handler to register
     * @throws IllegalArgumentException if the handler is already owned by another plugin
     */
    void registerPacketHandler(Plugin plugin, PacketHandler packetHandler);

    /**
     * Unregisters a handler instance.
     *
     * @param packetHandler handler to unregister
     */
    void unregisterPacketHandler(PacketHandler packetHandler);

    /**
     * Gets an immutable snapshot of the handlers owned by a plugin.
     *
     * @param plugin plugin whose handlers to retrieve
     * @return registered handlers in invocation order
     */
    List<PacketHandler> getPacketHandlersFor(Plugin plugin);

    /**
     * Gets an immutable snapshot of all registered handlers.
     *
     * @return registered handlers in invocation order
     */
    List<PacketHandler> getPacketHandlers();

    /**
     * Unregisters all handlers owned by a plugin.
     *
     * @param plugin plugin whose handlers to unregister
     */
    void unregisterPacketHandlerFor(Plugin plugin);

    /**
     * Invokes the registered handlers for a packet.
     *
     * <p>This is an advanced API. Calling it does not itself send or process
     * the packet.</p>
     *
     * @param player    player associated with the packet
     * @param packet    packet object
     * @param direction packet direction
     * @return {@code false} if a handler cancelled the packet
     */
    boolean callPacket(Player player, Object packet, PacketDirection direction);
}
