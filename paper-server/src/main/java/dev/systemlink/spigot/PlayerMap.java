package dev.systemlink.spigot;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * mSpigot-compatible nearby-player access backed by Moonrise's maintained
 * per-world spatial index.
 *
 * <p>Moonrise owns player add, move, remove, and world-transfer bookkeeping,
 * so this facade cannot drift out of sync with the chunk system.</p>
 */
@NullMarked
public final class PlayerMap {
    private final ServerLevel level;

    public PlayerMap(final ServerLevel level) {
        this.level = level;
    }

    /**
     * Returns a bounded candidate collection for a distance-based lookup.
     * Exact radius and predicate checks remain the caller's responsibility.
     */
    public Iterable<? extends Player> candidates(final double x, final double z, final double distance) {
        if (!enabled() || distance < 0.0 || !Double.isFinite(distance)) {
            return this.level.players();
        }

        final NearbyPlayers.NearbyMapType type = mapType(distance);
        if (type == null) {
            return this.level.players();
        }

        final ReferenceList<ServerPlayer> players = this.level.moonrise$getNearbyPlayers()
            .getPlayersByBlock(Mth.floor(x), Mth.floor(z), type);
        return players == null ? List.of() : players;
    }

    public void forEachNearby(
        final double x,
        final double y,
        final double z,
        final double distance,
        final boolean useRadius,
        final Consumer<ServerPlayer> consumer
    ) {
        final double distanceSquared = distance * distance;
        for (final Player player : this.candidates(x, z, distance)) {
            if (player instanceof ServerPlayer serverPlayer && (!useRadius || serverPlayer.distanceToSqr(x, y, z) < distanceSquared)) {
                consumer.accept(serverPlayer);
            }
        }
    }

    public @Nullable ServerPlayer getNearestPlayer(
        final double x,
        final double y,
        final double z,
        final double distance,
        final @Nullable Predicate<? super ServerPlayer> predicate
    ) {
        final double distanceSquared = distance * distance;
        double bestDistanceSquared = -1.0;
        ServerPlayer nearest = null;
        for (final Player player : this.candidates(x, z, distance)) {
            if (!(player instanceof ServerPlayer serverPlayer) || predicate != null && !predicate.test(serverPlayer)) {
                continue;
            }
            final double playerDistanceSquared = serverPlayer.distanceToSqr(x, y, z);
            if ((distance < 0.0 || playerDistanceSquared < distanceSquared)
                && (bestDistanceSquared < 0.0 || playerDistanceSquared < bestDistanceSquared)) {
                bestDistanceSquared = playerDistanceSquared;
                nearest = serverPlayer;
            }
        }
        return nearest;
    }

    public boolean isPlayerNearby(
        final double x,
        final double y,
        final double z,
        final double distance,
        final Predicate<? super ServerPlayer> predicate
    ) {
        final double distanceSquared = distance * distance;
        for (final Player player : this.candidates(x, z, distance)) {
            if (player instanceof ServerPlayer serverPlayer
                && predicate.test(serverPlayer)
                && (distance < 0.0 || serverPlayer.distanceToSqr(x, y, z) < distanceSquared)) {
                return true;
            }
        }
        return false;
    }

    public void sendPacketNearby(
        final @Nullable ServerPlayer source,
        final double x,
        final double y,
        final double z,
        final double distance,
        final Packet<?> packet
    ) {
        this.forEachNearby(x, y, z, distance, true, player -> {
            if (player != source && (source == null || player.getBukkitEntity().canSee(source.getBukkitEntity()))) {
                player.connection.send(packet);
            }
        });
    }

    private static boolean enabled() {
        final dev.systemlink.spigot.configuration.MSpigotConfig.Snapshot snapshot =
            dev.systemlink.spigot.configuration.MSpigotConfig.current();
        return snapshot != null && snapshot.chunks().optimizedEntityLookups();
    }

    private static NearbyPlayers.@Nullable NearbyMapType mapType(final double distance) {
        if (distance <= NearbyPlayers.GENERAL_REALLY_SMALL_AREA_VIEW_DISTANCE_BLOCKS) {
            return NearbyPlayers.NearbyMapType.GENERAL_REALLY_SMALL;
        }
        if (distance <= NearbyPlayers.GENERAL_SMALL_AREA_VIEW_DISTANCE_BLOCKS) {
            return NearbyPlayers.NearbyMapType.GENERAL_SMALL;
        }
        if (distance <= NearbyPlayers.GENERAL_AREA_VIEW_DISTANCE_BLOCKS) {
            return NearbyPlayers.NearbyMapType.GENERAL;
        }
        return null;
    }
}
