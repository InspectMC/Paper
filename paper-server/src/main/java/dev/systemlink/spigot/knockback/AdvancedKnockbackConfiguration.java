package dev.systemlink.spigot.knockback;

import io.papermc.paper.configuration.ConfigurationPart;
import io.papermc.paper.configuration.type.number.DoubleOr;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.PostProcess;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Controls the knockback profile used in a world.
 *
 * <p>The defaults reproduce vanilla/Paper knockback. Named profiles are stored
 * under {@code profiles} in {@code knockback.yml}.</p>
 */
@SuppressWarnings("FieldMayBeFinal")
public final class AdvancedKnockbackConfiguration extends ConfigurationPart {
    @Comment("Whether this world uses the SystemLink advanced knockback profile.")
    public boolean enabled = true;

    @Comment("Base horizontal knockback applied when an entity takes damage.")
    public double baseKnockback = 0.4;

    @Comment("Multiplier applied to an entity's knockback-resistance attribute.")
    public double knockbackResistanceModifier = 1.0;

    @Comment("Vertical knockback, or 'default' to use the resistance-adjusted knockback power.")
    public DoubleOr.Default knockbackVertical = DoubleOr.Default.USE_DEFAULT;

    @Comment("Maximum upward velocity that knockback may produce.")
    public double knockbackVerticalLimit = 0.4;

    @Comment("Knockback produced when an attack is blocked with a shield.")
    public double shieldHitKnockback = 0.5;

    @Comment("Additional knockback produced by a sprint attack.")
    public double extraKnockback = 0.5;

    @Comment("Whether sprint knockback requires a fully charged attack.")
    public boolean requireFullAttack = true;

    @Comment("Knockback applied to entities hit by a sweeping attack.")
    public double sweepingEdgeKnockback = 0.4;

    @Comment("Whether vertical knockback is only changed while the target is on the ground.")
    public boolean verticalKnockbackRequireGround = true;

    public double applyResistance(final double power, final double resistance) {
        return power * Math.max(0.0, 1.0 - resistance * this.knockbackResistanceModifier);
    }

    public double verticalVelocity(final double currentVelocity, final double power, final boolean onGround) {
        if (this.verticalKnockbackRequireGround && !onGround) {
            return currentVelocity;
        }
        return Math.min(this.knockbackVerticalLimit, currentVelocity / 2.0 + this.knockbackVertical.or(power));
    }

    public boolean allowsSprintKnockback(final boolean fullStrengthAttack) {
        return fullStrengthAttack || !this.requireFullAttack;
    }

    @PostProcess
    private void validate() throws SerializationException {
        this.requireFiniteAndNonNegative("base-knockback", this.baseKnockback);
        this.requireFiniteAndNonNegative("knockback-resistance-modifier", this.knockbackResistanceModifier);
        if (this.knockbackVertical.value().isPresent()) {
            this.requireFiniteAndNonNegative("knockback-vertical", this.knockbackVertical.doubleValue());
        }
        this.requireFiniteAndNonNegative("knockback-vertical-limit", this.knockbackVerticalLimit);
        this.requireFiniteAndNonNegative("shield-hit-knockback", this.shieldHitKnockback);
        this.requireFiniteAndNonNegative("extra-knockback", this.extraKnockback);
        this.requireFiniteAndNonNegative("sweeping-edge-knockback", this.sweepingEdgeKnockback);
    }

    private void requireFiniteAndNonNegative(final String name, final double value) throws SerializationException {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new SerializationException("knockback profile setting " + name + " must be a finite, non-negative number");
        }
    }
}
