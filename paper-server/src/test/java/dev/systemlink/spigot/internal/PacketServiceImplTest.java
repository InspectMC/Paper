package dev.systemlink.spigot.internal;

import dev.systemlink.spigot.packet.PacketDirection;
import dev.systemlink.spigot.packet.PacketHandler;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Normal
class PacketServiceImplTest {

    @Test
    void packetHandlersRunInOrderUntilCancelled() {
        final PacketServiceImpl service = new PacketServiceImpl();
        final Plugin plugin = enabledPlugin();
        final Player player = mock(Player.class);
        final Object packet = new Object();
        final List<String> calls = new ArrayList<>();

        service.registerPacketHandler(plugin, new PacketHandler() {
            @Override
            public boolean onSent(final Player player, final Object packet) {
                calls.add("first");
                return true;
            }
        });
        service.registerPacketHandler(plugin, new PacketHandler() {
            @Override
            public boolean onSent(final Player player, final Object packet) {
                calls.add("second");
                return false;
            }
        });
        service.registerPacketHandler(plugin, new PacketHandler() {
            @Override
            public boolean onSent(final Player player, final Object packet) {
                calls.add("third");
                return true;
            }
        });

        assertFalse(service.callPacket(player, packet, PacketDirection.SENT));
        assertEquals(List.of("first", "second"), calls);
    }

    @Test
    void duplicateRegistrationBySamePluginIsIgnored() {
        final PacketServiceImpl service = new PacketServiceImpl();
        final Plugin plugin = enabledPlugin();
        final PacketHandler handler = new PacketHandler() {
        };

        service.registerPacketHandler(plugin, handler);
        service.registerPacketHandler(plugin, handler);

        assertEquals(1, service.getPacketHandlers().size());
        assertSame(handler, service.getPacketHandlersFor(plugin).getFirst());
        assertTrue(service.callPacket(mock(Player.class), new Object(), PacketDirection.RECEIVED));
    }

    private static Plugin enabledPlugin() {
        final Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        return plugin;
    }
}
