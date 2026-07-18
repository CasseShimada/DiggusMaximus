package net.kyrptonaught.diggusmaximus.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingConfigTest {
    private final KeyBindingConfig activation = new KeyBindingConfig(true, true, "key.keyboard.grave.accent");

    @Test
    void invalidKeysNeverActivate() {
        assertFalse(activation.isActivated(false, true, false, false));
        assertFalse(activation.isActivated(false, true, false, true));
    }

    @Test
    void unknownActivationReturnsBeforeInversion() {
        assertTrue(activation.isActivated(true, true, false, false));
        assertTrue(activation.isActivated(true, true, false, true));
    }

    @Test
    void inversionOnlyAppliesToBoundActivationKeys() {
        assertTrue(activation.isActivated(true, false, true, false));
        assertFalse(activation.isActivated(true, false, false, false));
        assertFalse(activation.isActivated(true, false, true, true));
        assertTrue(activation.isActivated(true, false, false, true));
    }
}
