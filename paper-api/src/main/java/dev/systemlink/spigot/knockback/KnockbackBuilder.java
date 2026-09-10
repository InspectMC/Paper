package dev.systemlink.spigot.knockback;

import java.util.OptionalDouble;

/**
 * Fluent builder for a {@link Knockback} specification.
 */
public final class KnockbackBuilder {
    private double strength = 0.4;
    private double directionX;
    private double directionZ;
    private boolean directionSet;
    private OptionalDouble vertical = OptionalDouble.empty();
    private double verticalLimit = 0.4;
    private double resistanceModifier = 1.0;
    private boolean verticalOnlyWhenGrounded = true;

    private KnockbackBuilder() {
    }

    /**
     * Creates a new builder with vanilla-compatible defaults.
     *
     * @return a new builder
     */
    public static KnockbackBuilder builder() {
        return new KnockbackBuilder();
    }

    /**
     * Sets horizontal knockback strength.
     *
     * @param strength positive strength
     * @return this builder
     */
    public KnockbackBuilder strength(final double strength) {
        this.strength = strength;
        return this;
    }

    /**
     * Sets the relative horizontal position of the knockback source.
     *
     * @param directionX relative X position
     * @param directionZ relative Z position
     * @return this builder
     */
    public KnockbackBuilder direction(final double directionX, final double directionZ) {
        this.directionX = directionX;
        this.directionZ = directionZ;
        this.directionSet = true;
        return this;
    }

    /**
     * Sets explicit vertical knockback.
     *
     * @param vertical non-negative vertical knockback
     * @return this builder
     */
    public KnockbackBuilder vertical(final double vertical) {
        this.vertical = OptionalDouble.of(vertical);
        return this;
    }

    /**
     * Uses resistance-adjusted horizontal strength as vertical knockback.
     *
     * @return this builder
     */
    public KnockbackBuilder defaultVertical() {
        this.vertical = OptionalDouble.empty();
        return this;
    }

    /**
     * Sets the maximum upward velocity produced by this knockback.
     *
     * @param verticalLimit non-negative velocity limit
     * @return this builder
     */
    public KnockbackBuilder verticalLimit(final double verticalLimit) {
        this.verticalLimit = verticalLimit;
        return this;
    }

    /**
     * Sets the multiplier applied to the target's knockback resistance.
     *
     * @param resistanceModifier non-negative multiplier
     * @return this builder
     */
    public KnockbackBuilder resistanceModifier(final double resistanceModifier) {
        this.resistanceModifier = resistanceModifier;
        return this;
    }

    /**
     * Controls whether vertical velocity may change while the target is airborne.
     *
     * @param verticalOnlyWhenGrounded whether vertical changes require the target to be grounded
     * @return this builder
     */
    public KnockbackBuilder verticalOnlyWhenGrounded(final boolean verticalOnlyWhenGrounded) {
        this.verticalOnlyWhenGrounded = verticalOnlyWhenGrounded;
        return this;
    }

    /**
     * Builds and validates the custom-knockback specification.
     *
     * @return immutable knockback specification
     * @throws IllegalStateException if no direction was supplied
     * @throws IllegalArgumentException if a numeric setting is invalid
     */
    public Knockback build() {
        if (!this.directionSet) {
            throw new IllegalStateException("Knockback direction must be configured");
        }
        return new Knockback(
            this.strength,
            this.directionX,
            this.directionZ,
            this.vertical,
            this.verticalLimit,
            this.resistanceModifier,
            this.verticalOnlyWhenGrounded
        );
    }
}
