package dev.systemlink.spigot.internal;

import com.google.common.base.Preconditions;
import dev.systemlink.spigot.packet.PacketDirection;
import dev.systemlink.spigot.packet.PacketHandler;
import dev.systemlink.spigot.packet.PacketService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Thread-safe packet handler registry used by the server. */
public final class PacketServiceImpl implements PacketService {
    private final CopyOnWriteArrayList<Registration> registrations = new CopyOnWriteArrayList<>();

    @Override
    public synchronized void registerPacketHandler(final Plugin plugin, final PacketHandler packetHandler) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        Preconditions.checkArgument(packetHandler != null, "packetHandler cannot be null");

        for (final Registration registration : this.registrations) {
            if (registration.handler() == packetHandler) {
                Preconditions.checkArgument(registration.plugin() == plugin, "packetHandler is already registered by another plugin");
                return;
            }
        }
        this.registrations.add(new Registration(plugin, packetHandler));
    }

    @Override
    public synchronized void unregisterPacketHandler(final PacketHandler packetHandler) {
        Preconditions.checkArgument(packetHandler != null, "packetHandler cannot be null");
        this.registrations.removeIf(registration -> registration.handler() == packetHandler);
    }

    @Override
    public List<PacketHandler> getPacketHandlersFor(final Plugin plugin) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        return this.registrations.stream()
            .filter(registration -> registration.plugin() == plugin)
            .map(Registration::handler)
            .toList();
    }

    @Override
    public List<PacketHandler> getPacketHandlers() {
        return this.registrations.stream().map(Registration::handler).toList();
    }

    @Override
    public synchronized void unregisterPacketHandlerFor(final Plugin plugin) {
        Preconditions.checkArgument(plugin != null, "plugin cannot be null");
        this.registrations.removeIf(registration -> registration.plugin() == plugin);
    }

    @Override
    public boolean callPacket(final Player player, final Object packet, final PacketDirection direction) {
        Preconditions.checkArgument(player != null, "player cannot be null");
        Preconditions.checkArgument(packet != null, "packet cannot be null");
        Preconditions.checkArgument(direction != null, "direction cannot be null");

        for (final Registration registration : this.registrations) {
            if (!registration.plugin().isEnabled()) {
                continue;
            }
            try {
                final boolean accepted = switch (direction) {
                    case SENT -> registration.handler().onSent(player, packet);
                    case RECEIVED -> registration.handler().onReceived(player, packet);
                };
                if (!accepted) {
                    return false;
                }
            } catch (final Throwable throwable) {
                registration.plugin().getLogger().log(Level.SEVERE, "Unhandled exception in mSpigot packet handler", throwable);
            }
        }
        return true;
    }

    private record Registration(Plugin plugin, PacketHandler handler) {
    }
}
