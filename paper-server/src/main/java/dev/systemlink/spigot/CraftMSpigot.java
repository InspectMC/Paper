package dev.systemlink.spigot;

import dev.systemlink.spigot.internal.MovementServiceImpl;
import dev.systemlink.spigot.internal.PacketServiceImpl;
import dev.systemlink.spigot.movement.MovementService;
import dev.systemlink.spigot.packet.PacketService;
import org.jspecify.annotations.NullMarked;

/** Server implementation of the SystemLink API entry point. */
@NullMarked
public final class CraftMSpigot implements MSpigot {
    private final PacketService packetService = new PacketServiceImpl();
    private final MovementService movementService = new MovementServiceImpl();

    @Override
    public PacketService getPacketService() {
        return this.packetService;
    }

    @Override
    public MovementService getMovementService() {
        return this.movementService;
    }
}
