package org.bukkit.event.inventory;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a living entity's equipment slot is changed.
 */
public class EquipmentSetEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final LivingEntity entity;
    private final EquipmentSlot slot;
    private final ItemStack oldItem;
    private final ItemStack newItem;
    private boolean cancelled;

    @ApiStatus.Internal
    public EquipmentSetEvent(
        @NotNull final LivingEntity entity,
        @NotNull final EquipmentSlot slot,
        @NotNull final ItemStack oldItem,
        @NotNull final ItemStack newItem
    ) {
        this.entity = entity;
        this.slot = slot;
        this.oldItem = oldItem.clone();
        this.newItem = newItem.clone();
    }

    /**
     * Gets the living entity whose equipment is changing.
     *
     * @return the living entity
     */
    @NotNull
    public LivingEntity getEntity() {
        return this.entity;
    }

    /**
     * Gets the equipment slot being changed.
     *
     * @return the equipment slot
     */
    @NotNull
    public EquipmentSlot getSlot() {
        return this.slot;
    }

    /**
     * Gets the previously equipped item.
     *
     * @return the old item
     */
    @NotNull
    public ItemStack getOldItem() {
        return this.oldItem.clone();
    }

    /**
     * Gets the item that will be equipped.
     *
     * @return the new item
     */
    @NotNull
    public ItemStack getNewItem() {
        return this.newItem.clone();
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
