package dev.systemlink.spigot.knockback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnockbackBuilderTest {
    private static final double EPSILON = 1.0E-10;

    @Test
    void buildsVanillaCompatibleDefaults() {
        final Knockback knockback = Knockback.builder().direction(1.0, 0.0).build();

        final double adjustedStrength = knockback.applyResistance(0.5);
        assertEquals(0.2, adjustedStrength, EPSILON);
        assertEquals(0.2, knockback.verticalVelocity(0.0, adjustedStrength, true), EPSILON);
        assertEquals(0.1, knockback.verticalVelocity(0.1, adjustedStrength, false), EPSILON);
    }

    @Test
    void appliesExplicitSettings() {
        final Knockback knockback = KnockbackBuilder.builder()
            .direction(-2.0, 3.0)
            .strength(0.7)
            .vertical(0.35)
            .verticalLimit(0.4)
            .resistanceModifier(0.5)
            .verticalOnlyWhenGrounded(false)
            .build();

        assertEquals(0.525, knockback.applyResistance(0.5), EPSILON);
        assertEquals(0.4, knockback.verticalVelocity(0.2, 0.525, false), EPSILON);
    }

    @Test
    void rejectsMissingOrInvalidDirection() {
        assertThrows(IllegalStateException.class, () -> Knockback.builder().build());
        assertThrows(IllegalArgumentException.class, () -> Knockback.builder().direction(0.0, 0.0).build());
        assertThrows(IllegalArgumentException.class, () -> Knockback.builder().direction(Double.NaN, 1.0).build());
    }
}
