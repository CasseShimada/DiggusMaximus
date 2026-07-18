package net.kyrptonaught.diggusmaximus.config;

/**
 * Loader-neutral representation of the legacy key object.
 * Field names deliberately match the old JSON5 so existing files remain readable.
 */
public class KeyBindingConfig {
    public boolean respectsInvert;
    public boolean unknownIsActivated;
    public String defaultKey;
    public String rawKey;
    public String modID = "diggusmaximus";

    public KeyBindingConfig(boolean respectsInvert, boolean unknownIsActivated, String defaultKey) {
        this.respectsInvert = respectsInvert;
        this.unknownIsActivated = unknownIsActivated;
        this.defaultKey = defaultKey;
        this.rawKey = defaultKey;
    }

    /**
     * Preserves the legacy key semantics: invalid keys never activate, while an empty or explicit
     * unknown key returns {@link #unknownIsActivated} before activation inversion is considered.
     */
    public boolean isActivated(boolean valid, boolean unbound, boolean pressed, boolean invertActivation) {
        if (!valid) {
            return false;
        }
        if (unbound) {
            return unknownIsActivated;
        }
        return respectsInvert && invertActivation ? !pressed : pressed;
    }
}
