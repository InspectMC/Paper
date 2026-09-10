package dev.systemlink.spigot.knockback;

import io.papermc.paper.configuration.type.number.DoubleOr;
import java.util.OptionalDouble;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Normal
class AdvancedKnockbackConfigurationTest {
    private static final double EPSILON = 1.0E-10;

    @Test
    void defaultsMatchVanillaKnockback() {
        final AdvancedKnockbackConfiguration profile = new AdvancedKnockbackConfiguration();

        assertEquals(0.2, profile.applyResistance(profile.baseKnockback, 0.5), EPSILON);
        assertEquals(0.2, profile.verticalVelocity(0.0, 0.2, true), EPSILON);
        assertEquals(0.1, profile.verticalVelocity(0.1, 0.2, false), EPSILON);
        assertFalse(profile.allowsSprintKnockback(false));
        assertTrue(profile.allowsSprintKnockback(true));
    }

    @Test
    void explicitVerticalAndAttackChargeOverridesAreApplied() {
        final AdvancedKnockbackConfiguration profile = new AdvancedKnockbackConfiguration();
        profile.knockbackVertical = new DoubleOr.Default(OptionalDouble.of(0.35));
        profile.knockbackVerticalLimit = 0.4;
        profile.verticalKnockbackRequireGround = false;
        profile.requireFullAttack = false;

        assertEquals(0.4, profile.verticalVelocity(0.2, 0.1, false), EPSILON);
        assertTrue(profile.allowsSprintKnockback(false));
    }

    @Test
    void resistanceModifierCannotReverseKnockback() {
        final AdvancedKnockbackConfiguration profile = new AdvancedKnockbackConfiguration();
        profile.knockbackResistanceModifier = 2.0;

        assertEquals(0.0, profile.applyResistance(0.4, 0.75), EPSILON);
    }
}
