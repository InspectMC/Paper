package dev.systemlink.spigot.internal;

import com.google.common.base.Preconditions;
import dev.systemlink.spigot.movement.MovementHandler;
import dev.systemlink.spigot.movement.MovementPacketWrapper;
import dev.systemlink.spigot.movement.MovementService;
import dev.systemlink.spigot.movement.MovementType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Thread-safe movement handler registry used by the server. */
public final class MovementServiceImpl implements MovementService {
    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();

    @Override
    public synchronized void registerMovementHandler(final Plugin plugin, final MovementHandler movementHandler) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        Preconditions.checkArgument(movementHandler != null, "movementHandler cannot be null");

        for (final Registration registration : this.registrations) {
            if (registration.handler() == movementHandler) {
                Preconditions.checkArgument(registration.plugin() == plugin, "movementHandler is already registered by another plugin");
                return;
            }
        }
        this.registrations.add(new Registration(plugin, movementHandler));
    }

    @Override
    public synchronized void unregisterMovementHandler(final MovementHandler movementHandler) {
        Preconditions.checkArgument(movementHandler != null, "movementHandler cannot be null");
        this.registrations.removeIf(registration -> registration.handler() == movementHandler);
    }

    @Override
    public List<MovementHandler> getMovementHandlersFor(final Plugin plugin) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        return this.registrations.stream()
            .filter(registration -> registration.plugin() == plugin)
            .map(Registration::handler)
            .toList();
    }

    @Override
    public List<MovementHandler> getMovementHandlers() {
        return this.registrations.stream().map(Registration::handler).toList();
    }

    @Override
    public synchronized void unregisterMovementHandlerFor(final Plugin plugin) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        this.registrations.removeIf(registration -> registration.plugin() == plugin);
    }

    @Override
    public boolean callMovement(
        final Player player,
        final MovementType type,
        final Location from,
        final Location to,
        final Object packetPlayInFlying
    ) {
        Preconditions.checkArgument(player != null, "player cannot be null");
        Preconditions.checkArgument(type != null, "type cannot be null");
        Preconditions.checkArgument(from != null, "from cannot be null");
        Preconditions.checkArgument(to != null, "to cannot be null");
        Preconditions.checkArgument(packetPlayInFlying != null, "packetPlayInFlying cannot be null");

        final MovementPacketWrapper wrapper = this.wrap(packetPlayInFlying);
        for (final Registration registration : this.registrations) {
            if (!registration.plugin().isEnabled()) {
                continue;
            }
            try {
                switch (type) {
                    case LOCATION -> registration.handler().onUpdateLocation(player, from.clone(), to.clone(), wrapper);
                    case ROTATION -> registration.handler().onUpdateRotation(player, from.clone(), to.clone(), wrapper);
                }
                if (wrapper.isCancelled()) {
                    return false;
                }
            } catch (final Throwable throwable) {
                registration.plugin().getLogger().log(Level.SEVERE, "Unhandled exception in mSpigot movement handler", throwable);
            }
        }
        return true;
    }

    private MovementPacketWrapper wrap(final Object packet) {
        if (packet instanceof ServerboundMovePlayerPacket movementPacket) {
            return new MovementPacketWrapper(
                packet,
                movementPacket.hasPosition(),
                movementPacket.hasRotation(),
                movementPacket.isOnGround(),
                movementPacket.horizontalCollision()
            );
        }
        return new MovementPacketWrapper(packet, false, false, false, false);
    }

    private record Registration(Plugin plugin, MovementHandler handler) {
    }
}
