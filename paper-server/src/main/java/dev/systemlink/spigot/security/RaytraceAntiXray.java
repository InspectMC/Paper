package dev.systemlink.spigot.security;

import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player anti-xray layer that dynamically corrects exposed ore visibility.
 *
 * <p>Paper's chunk anti-xray handles bulk chunk packet obfuscation. This layer
 * focuses on live updates: after mining, movement, or exposure changes it sends
 * per-player block updates so an ore is only visible to clients that can see it
 * with the server's own ray trace.</p>
 */
public final class RaytraceAntiXray {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<UUID, PlayerView> VIEWS = new HashMap<>();

    private RaytraceAntiXray() {
    }

    public static void tick(final MinecraftServer server) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null) {
            return;
        }

        final MSpigotConfig.RaytraceXraySettings settings = snapshot.security().raytraceXray();
        if (!settings.enabled()) {
            revealAndClearAll(server);
            return;
        }

        if (server.getTickCount() % settings.updateIntervalTicks() != 0) {
            return;
        }

        final Set<UUID> onlinePlayers = new HashSet<>();
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            onlinePlayers.add(player.getUUID());
            updateAround(player, player.level(), player.blockPosition(), settings, false);
        }
        VIEWS.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
    }

    public static void onBlockMined(final ServerPlayer player, final ServerLevel level, final BlockPos pos) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null) {
            return;
        }

        final MSpigotConfig.RaytraceXraySettings settings = snapshot.security().raytraceXray();
        if (!settings.enabled()) {
            return;
        }

        final PlayerView view = view(player);
        clearFakeOresNear(player, level, pos, view, settings.fakeOres(), true);
        updateAround(player, level, pos, settings, true);
        maybeCreateFakeOre(player, level, pos, view, settings.fakeOres());
    }

    private static void updateAround(
        final ServerPlayer player,
        final ServerLevel level,
        final BlockPos center,
        final MSpigotConfig.RaytraceXraySettings settings,
        final boolean fromMining
    ) {
        final PlayerView view = view(player);
        clearFakeOresNear(player, level, center, view, settings.fakeOres(), fromMining);

        final int radius = settings.updateRadius();
        final int radiusSquared = radius * radius;
        cleanupHiddenOresNear(player, level, center, view, radiusSquared);
        int processedOres = 0;
        final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    if (distanceSquared(center.getX(), center.getY(), center.getZ(), x, y, z) > radiusSquared) {
                        continue;
                    }

                    mutable.set(x, y, z);
                    if (!level.isInWorldBounds(mutable)) {
                        continue;
                    }

                    final BlockState state = level.getBlockStateIfLoaded(mutable);
                    if (state == null) {
                        continue;
                    }
                    if (!isOre(state) || !isExposed(level, mutable)) {
                        continue;
                    }

                    final BlockPos orePos = mutable.immutable();
                    updateOreFor(player, level, orePos, state, view, settings.maxRayDistance());
                    if (++processedOres >= settings.maxOresPerScan()) {
                        return;
                    }
                }
            }
        }
    }

    private static void cleanupHiddenOresNear(
        final ServerPlayer player,
        final ServerLevel level,
        final BlockPos center,
        final PlayerView view,
        final int radiusSquared
    ) {
        if (view.hiddenOres.isEmpty()) {
            return;
        }

        final Iterator<Map.Entry<BlockKey, BlockState>> iterator = view.hiddenOres.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BlockKey, BlockState> entry = iterator.next();
            final BlockKey key = entry.getKey();
            if (!key.dimension().equals(level.dimension()) || distanceSquared(center, key.pos()) > radiusSquared) {
                continue;
            }

            final BlockState currentState = level.getBlockStateIfLoaded(key.pos());
            if (currentState == null) {
                iterator.remove();
                continue;
            }

            if (!currentState.equals(entry.getValue()) || !isOre(currentState)) {
                sendBlock(player, key.pos(), currentState);
                iterator.remove();
            }
        }
    }

    private static void updateOreFor(
        final ServerPlayer player,
        final ServerLevel level,
        final BlockPos pos,
        final BlockState oreState,
        final PlayerView view,
        final double maxRayDistance
    ) {
        final BlockKey key = new BlockKey(level.dimension(), pos);
        if (view.fakeOres.containsKey(key)) {
            return;
        }

        if (isVisibleByRayTrace(player, level, pos, maxRayDistance)) {
            if (view.hiddenOres.remove(key) != null) {
                sendBlock(player, pos, oreState);
            }
            return;
        }

        if (!oreState.equals(view.hiddenOres.get(key))) {
            view.hiddenOres.put(key, oreState);
            sendBlock(player, pos, disguiseFor(oreState));
        }
    }

    private static void maybeCreateFakeOre(
        final ServerPlayer player,
        final ServerLevel level,
        final BlockPos minedPos,
        final PlayerView view,
        final MSpigotConfig.FakeOreSettings settings
    ) {
        if (!settings.enabled() || level.getRandom().nextDouble() > settings.chance()) {
            return;
        }

        final int sameDimensionFakes = countFakeOresIn(level, view);
        if (sameDimensionFakes >= settings.maxPerPlayer()) {
            return;
        }

        final int radius = settings.radius();
        for (int attempt = 0; attempt < settings.attempts(); attempt++) {
            final int x = minedPos.getX() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            final int y = minedPos.getY() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            final int z = minedPos.getZ() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            final BlockPos candidate = new BlockPos(x, y, z);
            if (!level.isInWorldBounds(candidate)) {
                continue;
            }

            final BlockState realState = level.getBlockStateIfLoaded(candidate);
            if (realState == null) {
                continue;
            }
            if (!isSafeFakeOreHost(realState) || isExposed(level, candidate)) {
                continue;
            }

            final BlockKey key = new BlockKey(level.dimension(), candidate);
            if (view.fakeOres.containsKey(key) || view.hiddenOres.containsKey(key)) {
                continue;
            }

            final BlockState fakeState = randomFakeOre(level, realState);
            view.fakeOres.put(key, new FakeOre(realState, fakeState, level.getServer().getTickCount()));
            sendBlock(player, candidate, fakeState);
            return;
        }
    }

    private static void clearFakeOresNear(
        final ServerPlayer player,
        final ServerLevel level,
        final BlockPos center,
        final PlayerView view,
        final MSpigotConfig.FakeOreSettings settings,
        final boolean mined
    ) {
        if (view.fakeOres.isEmpty()) {
            return;
        }

        final int tick = level.getServer().getTickCount();
        final int disappearRadiusSquared = settings.disappearRadius() * settings.disappearRadius();
        final Iterator<Map.Entry<BlockKey, FakeOre>> iterator = view.fakeOres.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BlockKey, FakeOre> entry = iterator.next();
            final BlockKey key = entry.getKey();
            final FakeOre fakeOre = entry.getValue();

            if (!key.dimension().equals(level.dimension())) {
                continue;
            }

            final BlockPos pos = key.pos();
            final BlockState currentState = level.getBlockStateIfLoaded(pos);
            if (currentState == null) {
                iterator.remove();
                continue;
            }
            final boolean expired = tick - fakeOre.createdTick() >= settings.expireTicks();
            final boolean minedNearby = mined && distanceSquared(center, pos) <= disappearRadiusSquared;
            final boolean hostChanged = !currentState.equals(fakeOre.realState());
            if (expired || minedNearby || hostChanged) {
                sendBlock(player, pos, currentState);
                iterator.remove();
            }
        }
    }

    private static void revealAndClearAll(final MinecraftServer server) {
        if (VIEWS.isEmpty() || server.getPlayerList() == null) {
            VIEWS.clear();
            return;
        }

        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            final PlayerView view = VIEWS.remove(player.getUUID());
            if (view == null) {
                continue;
            }
            revealTrackedBlocks(player, player.level(), view);
        }
        VIEWS.clear();
    }

    private static void revealTrackedBlocks(final ServerPlayer player, final ServerLevel level, final PlayerView view) {
        for (final BlockKey key : view.hiddenOres.keySet()) {
            if (key.dimension().equals(level.dimension())) {
                final BlockState state = level.getBlockStateIfLoaded(key.pos());
                if (state != null) {
                    sendBlock(player, key.pos(), state);
                }
            }
        }
        for (final BlockKey key : view.fakeOres.keySet()) {
            if (key.dimension().equals(level.dimension())) {
                final BlockState state = level.getBlockStateIfLoaded(key.pos());
                if (state != null) {
                    sendBlock(player, key.pos(), state);
                }
            }
        }
        view.hiddenOres.clear();
        view.fakeOres.clear();
    }

    private static boolean isVisibleByRayTrace(final ServerPlayer player, final ServerLevel level, final BlockPos pos, final double maxDistance) {
        final Vec3 eye = player.getEyePosition();
        final Vec3 target = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(target) > maxDistance * maxDistance) {
            return false;
        }

        final BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    private static boolean isExposed(final ServerLevel level, final BlockPos pos) {
        final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (final Direction direction : DIRECTIONS) {
            neighbor.setWithOffset(pos, direction);
            if (!level.isInWorldBounds(neighbor)) {
                continue;
            }
            final BlockState neighborState = level.getBlockStateIfLoaded(neighbor);
            if (neighborState == null) {
                continue;
            }
            if (neighborState.isAir() || !neighborState.isCollisionShapeFullBlock(level, neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOre(final BlockState state) {
        return state.is(Blocks.COAL_ORE)
            || state.is(Blocks.DEEPSLATE_COAL_ORE)
            || state.is(Blocks.COPPER_ORE)
            || state.is(Blocks.DEEPSLATE_COPPER_ORE)
            || state.is(Blocks.IRON_ORE)
            || state.is(Blocks.DEEPSLATE_IRON_ORE)
            || state.is(Blocks.GOLD_ORE)
            || state.is(Blocks.DEEPSLATE_GOLD_ORE)
            || state.is(Blocks.REDSTONE_ORE)
            || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
            || state.is(Blocks.EMERALD_ORE)
            || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
            || state.is(Blocks.LAPIS_ORE)
            || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
            || state.is(Blocks.DIAMOND_ORE)
            || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
            || state.is(Blocks.NETHER_GOLD_ORE)
            || state.is(Blocks.NETHER_QUARTZ_ORE)
            || state.is(Blocks.ANCIENT_DEBRIS);
    }

    private static boolean isSafeFakeOreHost(final BlockState state) {
        return state.is(Blocks.STONE)
            || state.is(Blocks.DEEPSLATE)
            || state.is(Blocks.NETHERRACK)
            || state.is(Blocks.END_STONE);
    }

    private static BlockState disguiseFor(final BlockState oreState) {
        if (oreState.is(Blocks.DEEPSLATE_COAL_ORE)
            || oreState.is(Blocks.DEEPSLATE_COPPER_ORE)
            || oreState.is(Blocks.DEEPSLATE_IRON_ORE)
            || oreState.is(Blocks.DEEPSLATE_GOLD_ORE)
            || oreState.is(Blocks.DEEPSLATE_REDSTONE_ORE)
            || oreState.is(Blocks.DEEPSLATE_EMERALD_ORE)
            || oreState.is(Blocks.DEEPSLATE_LAPIS_ORE)
            || oreState.is(Blocks.DEEPSLATE_DIAMOND_ORE)) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (oreState.is(Blocks.NETHER_GOLD_ORE) || oreState.is(Blocks.NETHER_QUARTZ_ORE) || oreState.is(Blocks.ANCIENT_DEBRIS)) {
            return Blocks.NETHERRACK.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockState randomFakeOre(final ServerLevel level, final BlockState host) {
        final int choice = level.getRandom().nextInt(100);
        if (host.is(Blocks.DEEPSLATE)) {
            if (choice < 35) {
                return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
            }
            if (choice < 60) {
                return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
            }
            if (choice < 80) {
                return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
            }
            if (choice < 92) {
                return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
            }
            return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        }
        if (host.is(Blocks.NETHERRACK)) {
            if (choice < 55) {
                return Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
            }
            if (choice < 92) {
                return Blocks.NETHER_GOLD_ORE.defaultBlockState();
            }
            return Blocks.ANCIENT_DEBRIS.defaultBlockState();
        }
        if (host.is(Blocks.END_STONE)) {
            return Blocks.DIAMOND_ORE.defaultBlockState();
        }
        if (choice < 30) {
            return Blocks.IRON_ORE.defaultBlockState();
        }
        if (choice < 52) {
            return Blocks.COAL_ORE.defaultBlockState();
        }
        if (choice < 70) {
            return Blocks.COPPER_ORE.defaultBlockState();
        }
        if (choice < 84) {
            return Blocks.REDSTONE_ORE.defaultBlockState();
        }
        if (choice < 94) {
            return Blocks.GOLD_ORE.defaultBlockState();
        }
        return Blocks.DIAMOND_ORE.defaultBlockState();
    }

    private static int countFakeOresIn(final ServerLevel level, final PlayerView view) {
        int count = 0;
        for (final BlockKey key : view.fakeOres.keySet()) {
            if (key.dimension().equals(level.dimension())) {
                count++;
            }
        }
        return count;
    }

    private static void sendBlock(final ServerPlayer player, final BlockPos pos, final BlockState state) {
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    private static PlayerView view(final ServerPlayer player) {
        return VIEWS.computeIfAbsent(player.getUUID(), ignored -> new PlayerView());
    }

    private static int distanceSquared(final BlockPos first, final BlockPos second) {
        return distanceSquared(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ());
    }

    private static int distanceSquared(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        final int dx = x1 - x2;
        final int dy = y1 - y2;
        final int dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private record BlockKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record FakeOre(BlockState realState, BlockState fakeState, int createdTick) {
    }

    private static final class PlayerView {
        private final Map<BlockKey, BlockState> hiddenOres = new HashMap<>();
        private final Map<BlockKey, FakeOre> fakeOres = new HashMap<>();
    }
}
