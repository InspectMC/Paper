package dev.systemlink.spigot.movement;

import org.jetbrains.annotations.ApiStatus;

/**
 * Version-independent details from a player movement packet.
 */
public final class MovementPacketWrapper {
    private final Object packet;
    private final boolean position;
    private final boolean rotation;
    private final boolean onGround;
    private final boolean horizontalCollision;
    private boolean cancelled;

    /**
     * Creates a wrapper. Server implementations construct this object when
     * dispatching movement handlers.
     *
     * @param packet              underlying version-specific packet
     * @param position            whether the packet contains a position
     * @param rotation            whether the packet contains a rotation
     * @param onGround            client-reported on-ground state
     * @param horizontalCollision client-reported horizontal collision state
     */
    @ApiStatus.Internal
    public MovementPacketWrapper(
        final Object packet,
        final boolean position,
        final boolean rotation,
        final boolean onGround,
        final boolean horizontalCollision
    ) {
        this.packet = packet;
        this.position = position;
        this.rotation = rotation;
        this.onGround = onGround;
        this.horizontalCollision = horizontalCollision;
    }

    /**
     * Gets the underlying version-specific packet object.
     *
     * @return the packet object
     */
    public Object getPacket() {
        return this.packet;
    }

    /**
     * Gets whether this packet contains position coordinates.
     *
     * @return whether position is present
     */
    public boolean hasPosition() {
        return this.position;
    }

    /**
     * Gets whether this packet contains yaw and pitch.
     *
     * @return whether rotation is present
     */
    public boolean hasRotation() {
        return this.rotation;
    }

    /**
     * Gets the client-reported on-ground state.
     *
     * @return the on-ground state
     */
    public boolean isOnGround() {
        return this.onGround;
    }

    /**
     * Gets the client-reported horizontal collision state.
     *
     * @return the horizontal collision state
     */
    public boolean hasHorizontalCollision() {
        return this.horizontalCollision;
    }

    /**
     * Cancels the movement update.
     */
    public void cancel() {
        this.cancelled = true;
    }

    /**
     * Changes whether the movement update is cancelled.
     *
     * @param cancelled cancellation state
     */
    public void setCancelled(final boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Gets whether a movement handler cancelled the update.
     *
     * @return the cancellation state
     */
    public boolean isCancelled() {
        return this.cancelled;
    }
}
