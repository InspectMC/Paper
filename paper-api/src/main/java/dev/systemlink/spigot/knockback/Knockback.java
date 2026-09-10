package dev.systemlink.spigot.knockback;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * An immutable custom-knockback specification.
 *
 * @param strength                 horizontal knockback strength
 * @param directionX               relative X position of the knockback source
 * @param directionZ               relative Z position of the knockback source
 * @param vertical                 explicit vertical knockback, or empty to use the resistance-adjusted strength
 * @param verticalLimit            maximum upward velocity produced by this knockback
 * @param resistanceModifier       multiplier applied to the target's knockback resistance
 * @param verticalOnlyWhenGrounded whether vertical velocity is unchanged for airborne targets
 */
public record Knockback(
    double strength,
    double directionX,
    double directionZ,
    OptionalDouble vertical,
    double verticalLimit,
    double resistanceModifier,
    boolean verticalOnlyWhenGrounded
) {
    /**
     * Creates a custom-knockback specification.
     */
    public Knockback {
        requireFiniteAndPositive("strength", strength);
        requireFinite("directionX", directionX);
        requireFinite("directionZ", directionZ);
        if (directionX * directionX + directionZ * directionZ < 1.0E-10) {
            throw new IllegalArgumentException("Knockback direction must not be zero");
        }
        Objects.requireNonNull(vertical, "vertical");
        if (vertical.isPresent()) {
            requireFiniteAndNonNegative("vertical", vertical.getAsDouble());
        }
        requireFiniteAndNonNegative("verticalLimit", verticalLimit);
        requireFiniteAndNonNegative("resistanceModifier", resistanceModifier);
    }

    /**
     * Creates a builder for a custom-knockback specification.
     *
     * @return a new builder
     */
    public static KnockbackBuilder builder() {
        return KnockbackBuilder.builder();
    }

    /**
     * Applies a target's knockback resistance to this specification's strength.
     *
     * @param resistance target knockback resistance
     * @return the adjusted horizontal strength
     */
    public double applyResistance(final double resistance) {
        requireFiniteAndNonNegative("resistance", resistance);
        return this.strength * Math.max(0.0, 1.0 - resistance * this.resistanceModifier);
    }

    /**
     * Calculates the resulting vertical velocity.
     *
     * @param currentVelocity  target's current vertical velocity
     * @param adjustedStrength resistance-adjusted horizontal strength
     * @param onGround         whether the target is on the ground
     * @return resulting vertical velocity
     */
    public double verticalVelocity(final double currentVelocity, final double adjustedStrength, final boolean onGround) {
        requireFinite("currentVelocity", currentVelocity);
        requireFiniteAndNonNegative("adjustedStrength", adjustedStrength);
        if (this.verticalOnlyWhenGrounded && !onGround) {
            return currentVelocity;
        }
        return Math.min(this.verticalLimit, currentVelocity / 2.0 + this.vertical.orElse(adjustedStrength));
    }

    private static void requireFinite(final String name, final double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFiniteAndPositive(final String name, final double value) {
        requireFinite(name, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
    }

    private static void requireFiniteAndNonNegative(final String name, final double value) {
        requireFinite(name, value);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
