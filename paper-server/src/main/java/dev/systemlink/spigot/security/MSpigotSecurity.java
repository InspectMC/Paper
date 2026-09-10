package dev.systemlink.spigot.security;

import com.mojang.logging.LogUtils;
import dev.systemlink.spigot.configuration.MSpigotConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import org.slf4j.Logger;

/**
 * Lightweight exploit-facing guards for data that comes directly from clients.
 *
 * <p>Minecraft's modern item data lives in data components rather than one raw
 * NBT tree. These checks focus on the components historically abused by NBT,
 * book, creative-slot, window-click, custom-payload, and world-downloader
 * crashers.</p>
 */
public final class MSpigotSecurity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component INVALID_ITEM_DATA = Component.literal("Invalid item data");
    private static final Component INVALID_CUSTOM_PAYLOAD = Component.literal("Invalid custom payload");

    private MSpigotSecurity() {
    }

    public static boolean handleInboundPacket(final Connection connection, final Packet<?> packet) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null) {
            return true;
        }

        if (packet instanceof ServerboundCustomPayloadPacket customPayloadPacket
            && !handleCustomPayload(connection, customPayloadPacket, snapshot.security())) {
            return false;
        }

        final MSpigotConfig.NbtProtectionSettings nbt = snapshot.security().nbtProtection();
        if (!nbt.enabled()) {
            return true;
        }

        if (packet instanceof ServerboundSetCreativeModeSlotPacket creativeModeSlotPacket) {
            return validateClientItem(connection, connection.getPlayer(), creativeModeSlotPacket.itemStack(), "creative slot", true, nbt);
        }

        if (packet instanceof ServerboundEditBookPacket editBookPacket) {
            return validateBookEdit(connection, connection.getPlayer(), editBookPacket, nbt);
        }

        if (packet instanceof ServerboundContainerClickPacket containerClickPacket) {
            return validateContainerClick(connection, connection.getPlayer(), containerClickPacket, nbt);
        }

        return true;
    }

    public static boolean handleHeldItemUse(final Connection connection, final ServerPlayer player, final ItemStack stack, final String action) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null || !snapshot.security().nbtProtection().enabled()) {
            return true;
        }
        return validateClientItem(connection, player, stack, action, false, snapshot.security().nbtProtection());
    }

    public static boolean mayApplyPlacedBlockEntityData(final ItemStack stack, final ServerPlayer player) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null || !snapshot.security().nbtProtection().enabled()) {
            return true;
        }
        final MSpigotConfig.NbtProtectionSettings nbt = snapshot.security().nbtProtection();
        if (!nbt.readNbtFromPlacedBlocks()) {
            logBlocked(player, "placed block entity data");
            return false;
        }
        return !isHighRiskBlockEntityData(stack, player, nbt);
    }

    public static boolean movementBurstAllowed(final Connection connection, final ServerPlayer player, final int packetsThisTick) {
        final MSpigotConfig.Snapshot snapshot = MSpigotConfig.current();
        if (snapshot == null || !snapshot.security().russianCrasherMovementCheck().enabled()) {
            return true;
        }
        final int limit = snapshot.security().russianCrasherMovementCheck().maxMovePacketsPerTick();
        if (limit > 0 && packetsThisTick > limit) {
            LOGGER.warn("mSpigot dropped excessive movement packet {} of {} this tick from {}", packetsThisTick, limit, player.getScoreboardName());
            return false;
        }
        return true;
    }

    private static boolean handleCustomPayload(
        final Connection connection,
        final ServerboundCustomPayloadPacket packet,
        final MSpigotConfig.SecuritySettings security
    ) {
        if (!(packet.payload() instanceof DiscardedPayload discardedPayload)) {
            return true;
        }
        final Identifier id = packet.payload().type().id();
        final byte[] data = discardedPayload.data();
        final MSpigotConfig.NbtProtectionSettings nbt = security.nbtProtection();
        if (nbt.enabled() && nbt.maxCustomPayloadBytes() > 0 && data.length > nbt.maxCustomPayloadBytes()) {
            return rejectCustomPayload(connection, "custom payload larger than " + nbt.maxCustomPayloadBytes() + " bytes on channel " + id);
        }
        if (security.blockWorldDownloader() && isWorldDownloaderPayload(id, data)) {
            return rejectCustomPayload(connection, "world downloader channel " + id);
        }
        return true;
    }

    private static boolean validateClientItem(
        final Connection connection,
        final ServerPlayer player,
        final ItemStack stack,
        final String action,
        final boolean strictClientCreatedItem,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        if (stack.isEmpty()) {
            return true;
        }
        if (nbt.maxItemComponents() > 0 && stack.getComponents().size() > nbt.maxItemComponents()) {
            return rejectItem(connection, player, action + " item has too many components", nbt);
        }
        if (strictClientCreatedItem && nbt.blockContainerItems() && (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS))) {
            return rejectItem(connection, player, action + " item contains nested item storage", nbt);
        }
        if (stack.has(DataComponents.ENTITY_DATA) && !canUseGameMasterNbt(player)) {
            return rejectItem(connection, player, action + " item contains entity data", nbt);
        }
        if (isHighRiskBlockEntityData(stack, player, nbt)) {
            return rejectItem(connection, player, action + " item contains restricted block entity data", nbt);
        }
        final WritableBookContent writableBookContent = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (writableBookContent != null && !validateWritableBook(connection, player, action, writableBookContent, nbt)) {
            return false;
        }
        final WrittenBookContent writtenBookContent = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return writtenBookContent == null || validateWrittenBook(connection, player, action, writtenBookContent, nbt);
    }

    private static boolean validateContainerClick(
        final Connection connection,
        final ServerPlayer player,
        final ServerboundContainerClickPacket packet,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        if (packet.changedSlots().size() > 128) {
            return rejectItem(connection, player, "window click changed too many slots", nbt);
        }
        if (hashedStackTooLarge(packet.carriedItem(), nbt.maxItemComponents())) {
            return rejectItem(connection, player, "window click cursor item has too many component hashes", nbt);
        }
        for (final HashedStack changedStack : packet.changedSlots().values()) {
            if (hashedStackTooLarge(changedStack, nbt.maxItemComponents())) {
                return rejectItem(connection, player, "window click changed-slot item has too many component hashes", nbt);
            }
        }
        return true;
    }

    private static boolean validateBookEdit(
        final Connection connection,
        final ServerPlayer player,
        final ServerboundEditBookPacket packet,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        final List<String> pages = packet.pages();
        if (nbt.maxBookPages() > 0 && pages.size() > nbt.maxBookPages()) {
            return rejectItem(connection, player, "book edit has too many pages", nbt);
        }
        int totalLength = 0;
        for (final String page : pages) {
            if (nbt.maxBookPageLength() > 0 && page.length() > nbt.maxBookPageLength()) {
                return rejectItem(connection, player, "book edit page is too long", nbt);
            }
            totalLength += page.length();
            if (nbt.maxBookTotalLength() > 0 && totalLength > nbt.maxBookTotalLength()) {
                return rejectItem(connection, player, "book edit total length is too large", nbt);
            }
        }
        return true;
    }

    private static boolean validateWritableBook(
        final Connection connection,
        final ServerPlayer player,
        final String action,
        final WritableBookContent content,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        if (nbt.maxBookPages() > 0 && content.pages().size() > nbt.maxBookPages()) {
            return rejectItem(connection, player, action + " writable book has too many pages", nbt);
        }
        int totalLength = 0;
        for (final net.minecraft.server.network.Filterable<String> page : content.pages()) {
            final String raw = page.raw();
            if (nbt.maxBookPageLength() > 0 && raw.length() > nbt.maxBookPageLength()) {
                return rejectItem(connection, player, action + " writable book page is too long", nbt);
            }
            totalLength += raw.length();
            if (nbt.maxBookTotalLength() > 0 && totalLength > nbt.maxBookTotalLength()) {
                return rejectItem(connection, player, action + " writable book total length is too large", nbt);
            }
        }
        return true;
    }

    private static boolean validateWrittenBook(
        final Connection connection,
        final ServerPlayer player,
        final String action,
        final WrittenBookContent content,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        if (nbt.maxBookPages() > 0 && content.pages().size() > nbt.maxBookPages()) {
            return rejectItem(connection, player, action + " written book has too many pages", nbt);
        }
        int totalLength = 0;
        for (final net.minecraft.server.network.Filterable<Component> page : content.pages()) {
            final String raw = page.raw().getString();
            if (nbt.maxBookPageLength() > 0 && raw.length() > nbt.maxBookPageLength()) {
                return rejectItem(connection, player, action + " written book page is too long", nbt);
            }
            totalLength += raw.length();
            if (nbt.maxBookTotalLength() > 0 && totalLength > nbt.maxBookTotalLength()) {
                return rejectItem(connection, player, action + " written book total length is too large", nbt);
            }
        }
        return true;
    }

    private static boolean isHighRiskBlockEntityData(
        final ItemStack stack,
        final ServerPlayer player,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        final net.minecraft.world.item.component.TypedEntityData<BlockEntityType<?>> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (blockEntityData == null) {
            return false;
        }
        if (!canUseGameMasterNbt(player)) {
            return true;
        }
        return nbt.blockLecternBookData() && blockEntityData.type() == BlockEntityTypes.LECTERN;
    }

    private static boolean hashedStackTooLarge(final HashedStack hashedStack, final int maxComponents) {
        if (maxComponents <= 0 || !(hashedStack instanceof HashedStack.ActualItem actualItem)) {
            return false;
        }
        final HashedPatchMap components = actualItem.components();
        return components.addedComponents().size() + components.removedComponents().size() > maxComponents;
    }

    private static boolean canUseGameMasterNbt(final ServerPlayer player) {
        return player != null && (player.canUseGameMasterBlocks() || player.getBukkitEntity().hasPermission("minecraft.nbt.place"));
    }

    private static boolean rejectItem(
        final Connection connection,
        final ServerPlayer player,
        final String reason,
        final MSpigotConfig.NbtProtectionSettings nbt
    ) {
        logBlocked(player, reason);
        if (!nbt.nbtSkipProtector() && nbt.kickOnViolation()) {
            connection.disconnect(INVALID_ITEM_DATA);
        }
        return false;
    }

    private static boolean rejectCustomPayload(final Connection connection, final String reason) {
        final ServerPlayer player = connection.getPlayer();
        logBlocked(player, reason);
        connection.disconnect(INVALID_CUSTOM_PAYLOAD);
        return false;
    }

    private static void logBlocked(final ServerPlayer player, final String reason) {
        LOGGER.warn("mSpigot blocked {} from {}", reason, player != null ? player.getScoreboardName() : "unknown connection");
    }

    private static boolean isWorldDownloaderPayload(final Identifier identifier, final byte[] data) {
        final String channel = identifier.toString().toLowerCase(Locale.ROOT);
        if (channel.contains("wdl") || channel.contains("worlddownloader")) {
            return true;
        }
        final String payload = new String(data, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return payload.contains("wdl") || payload.contains("worlddownloader");
    }
}
