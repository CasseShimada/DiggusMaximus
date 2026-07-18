package net.kyrptonaught.diggusmaximus.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.kyrptonaught.diggusmaximus.config.KeyBindingConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;

/** Native key mappings backed by the legacy rawKey fields. */
public final class DiggusKeyMappings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(DiggusMaximusMod.MOD_ID, "category")
    );

    public static KeyMapping activation;
    public static KeyMapping shape;
    public static KeyMapping cycle;
    public static KeyMapping openConfig;
    private static boolean activationValid;
    private static boolean shapeValid;
    private static boolean cycleValid;
    private static final Map<KeyMapping, String> OBSERVED_KEYS = new IdentityHashMap<>();

    private DiggusKeyMappings() {
    }

    public static void register() {
        RegisteredKey activationKey = register("key.diggusmaximus.keybind.excavate", DiggusMaximusMod.getOptions().keybinding);
        activation = activationKey.mapping();
        activationValid = activationKey.valid();
        RegisteredKey shapeKey = register("key.diggusmaximus.keybind.shapeexcavate", DiggusMaximusMod.getExcavatingShapes().shapeKey);
        shape = shapeKey.mapping();
        shapeValid = shapeKey.valid();
        RegisteredKey cycleKey = register("key.diggusmaximus.keybind.cycleshape", DiggusMaximusMod.getExcavatingShapes().cycleKey);
        cycle = cycleKey.mapping();
        cycleValid = cycleKey.valid();
        openConfig = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.diggusmaximus.keybind.config", InputConstants.KEY_O, CATEGORY)
        );
        KeyMapping.resetMapping();
    }

    private static RegisteredKey register(String translationKey, KeyBindingConfig config) {
        InputConstants.Key defaultKey;
        try {
            defaultKey = InputConstants.getKey(config.defaultKey);
        } catch (RuntimeException exception) {
            defaultKey = InputConstants.UNKNOWN;
        }
        KeyMapping mapping = KeyMappingHelper.registerKeyMapping(
                new KeyMapping(translationKey, defaultKey.getType(), defaultKey.getValue(), CATEGORY)
        );
        boolean valid = apply(mapping, config.rawKey);
        return new RegisteredKey(mapping, valid);
    }

    public static void applyConfiguredKeys() {
        activationValid = apply(activation, DiggusMaximusMod.getOptions().keybinding.rawKey);
        shapeValid = apply(shape, DiggusMaximusMod.getExcavatingShapes().shapeKey.rawKey);
        cycleValid = apply(cycle, DiggusMaximusMod.getExcavatingShapes().cycleKey.rawKey);
        KeyMapping.resetMapping();
    }

    private static boolean apply(KeyMapping mapping, String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            mapping.setKey(InputConstants.UNKNOWN);
            OBSERVED_KEYS.put(mapping, mapping.saveString());
            return true;
        }
        try {
            mapping.setKey(InputConstants.getKey(rawKey));
            OBSERVED_KEYS.put(mapping, mapping.saveString());
            return true;
        } catch (RuntimeException exception) {
            mapping.setKey(InputConstants.UNKNOWN);
            OBSERVED_KEYS.put(mapping, mapping.saveString());
            DiggusMaximusMod.LOGGER.warn("Invalid configured key '{}'; disabling this binding until it is changed", rawKey);
            return false;
        }
    }

    public static boolean activationPressed() {
        KeyBindingConfig config = DiggusMaximusMod.getOptions().keybinding;
        return config.isActivated(
                activationValid,
                activation.isUnbound(),
                activation.isDown(),
                DiggusMaximusMod.getOptions().invertActivation
        );
    }

    public static boolean shapePressed() {
        KeyBindingConfig config = DiggusMaximusMod.getExcavatingShapes().shapeKey;
        return config.isActivated(
                shapeValid,
                shape.isUnbound(),
                shape.isDown(),
                DiggusMaximusMod.getOptions().invertActivation
        );
    }

    public static boolean syncToLegacyConfig() {
        boolean changed = sync(activation, DiggusMaximusMod.getOptions().keybinding);
        if (changed) activationValid = true;
        boolean shapeChanged = sync(shape, DiggusMaximusMod.getExcavatingShapes().shapeKey);
        if (shapeChanged) shapeValid = true;
        changed |= shapeChanged;
        boolean cycleChanged = sync(cycle, DiggusMaximusMod.getExcavatingShapes().cycleKey);
        if (cycleChanged) cycleValid = true;
        changed |= cycleChanged;
        return changed;
    }

    private static boolean sync(KeyMapping mapping, KeyBindingConfig config) {
        String saved = mapping.saveString();
        String observed = OBSERVED_KEYS.put(mapping, saved);
        if (saved.equals(observed)) {
            return false;
        }
        config.rawKey = saved;
        return true;
    }

    private record RegisteredKey(KeyMapping mapping, boolean valid) {
    }
}
