package net.kyrptonaught.diggusmaximus.config;

import net.kyrptonaught.diggusmaximus.ExcavateTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerCompatibilityTest {
    @TempDir
    Path directory;

    @Test
    void preservesLegacyValuesMissingDefaultsAndUnknownFields() throws Exception {
        Files.writeString(directory.resolve(ConfigManager.OPTIONS_FILE), """
                {
                  enabled: false,
                  keybinding: 'key.keyboard.g',
                  invertActivation: true,
                  maxMinedBlocks: 73,
                  tools: ['minecraft:stick'],
                  futureRoot: {value: 9},
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(ConfigManager.BLACKLIST_FILE), """
                {isWhitelist: true, blacklistedBlocks: ['#minecraft:logs', 'minecraft:stone']}
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(ConfigManager.GROUPING_FILE), """
                {customGrouping: true, groups: ['minecraft:dirt,minecraft:grass_block']}
                """, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve(ConfigManager.SHAPES_FILE), """
                {
                  enableShapes: true,
                  includeDifBlocks: true,
                  shapeKey: 'key.keyboard.v',
                  cycleKey: 'key.keyboard.b',
                  selectedShape: 'THREExTHREE_TUNNEL',
                }
                """, StandardCharsets.UTF_8);

        ConfigManager manager = new ConfigManager(directory);
        manager.load();

        assertFalse(manager.options().enabled);
        assertTrue(manager.options().invertActivation);
        assertEquals(73, manager.options().maxMinedBlocks);
        assertEquals(10, manager.options().maxMineDistance, "missing fields keep the old default");
        assertEquals("key.keyboard.g", manager.options().keybinding.rawKey);
        assertTrue(manager.blacklist().isWhitelist);
        assertTrue(manager.grouping().customGrouping);
        assertEquals(ExcavateTypes.shape.THREExTHREE_TUNNEL, manager.shapes().selectedShape);
        Map<String, Object> automaticallyCompleted = Json5.parseObject(
                Files.readString(directory.resolve(ConfigManager.OPTIONS_FILE))
        );
        assertEquals(10L, automaticallyCompleted.get("maxMineDistance"), "missing keys are written with legacy defaults");
        assertTrue(automaticallyCompleted.containsKey("futureRoot"));

        manager.options().maxMineDistance = 31;
        manager.save();

        Map<String, Object> saved = Json5.parseObject(Files.readString(directory.resolve(ConfigManager.OPTIONS_FILE)));
        assertTrue(saved.containsKey("futureRoot"));
        assertEquals("key.keyboard.g", saved.get("keybinding"), "legacy key fields must remain JSON strings");
        assertEquals(31L, saved.get("maxMineDistance"));
        Map<String, Object> savedShapes = Json5.parseObject(Files.readString(directory.resolve(ConfigManager.SHAPES_FILE)));
        assertEquals("key.keyboard.v", savedShapes.get("shapeKey"));
        assertEquals("key.keyboard.b", savedShapes.get("cycleKey"));

        ConfigManager reloaded = new ConfigManager(directory);
        reloaded.load();
        assertEquals(31, reloaded.options().maxMineDistance);
        assertEquals("key.keyboard.g", reloaded.options().keybinding.rawKey);
    }

    @Test
    void preservesPreviewObjectKeyFieldsWithoutForcingLegacyStringsToObjects() throws Exception {
        Files.writeString(directory.resolve(ConfigManager.OPTIONS_FILE), """
                {
                  keybinding: {
                    rawKey: 'key.keyboard.k',
                    defaultKey: 'key.keyboard.grave.accent',
                    previewUnknown: 'keep-me',
                  },
                }
                """, StandardCharsets.UTF_8);

        ConfigManager manager = new ConfigManager(directory);
        manager.load();
        manager.options().keybinding.rawKey = "key.keyboard.j";
        manager.save();

        Map<String, Object> saved = Json5.parseObject(Files.readString(directory.resolve(ConfigManager.OPTIONS_FILE)));
        Map<?, ?> savedKey = (Map<?, ?>) saved.get("keybinding");
        assertEquals("key.keyboard.j", savedKey.get("rawKey"));
        assertEquals("keep-me", savedKey.get("previewUnknown"));
        assertFalse(savedKey.containsKey("modID"), "do not inject migration-only fields into an existing object");
    }

    @Test
    void completesMissingRawKeyInsidePreviewObjects() throws Exception {
        Files.writeString(directory.resolve(ConfigManager.OPTIONS_FILE), """
                {
                  enabled: true,
                  keybinding: {previewUnknown: 'keep-me'},
                  invertActivation: false,
                  sneakToExcavate: false,
                  mineDiag: true,
                  maxMinedBlocks: 40,
                  maxMineDistance: 10,
                  autoPickup: true,
                  requiresTool: false,
                  dontBreakTool: true,
                  stopOnToolBreak: true,
                  toolDurability: true,
                  playerExhaustion: true,
                  exhaustionMultiplier: 1.0,
                  tools: [],
                }
                """, StandardCharsets.UTF_8);

        ConfigManager manager = new ConfigManager(directory);
        manager.load();

        Map<String, Object> saved = Json5.parseObject(Files.readString(directory.resolve(ConfigManager.OPTIONS_FILE)));
        Map<?, ?> key = (Map<?, ?>) saved.get("keybinding");
        assertEquals("key.keyboard.grave.accent", key.get("rawKey"));
        assertEquals("keep-me", key.get("previewUnknown"));
    }

    @Test
    void neverOverwritesAnUnreadableLegacyFile() throws Exception {
        String invalid = "{ enabled: true, this is not valid";
        Path options = directory.resolve(ConfigManager.OPTIONS_FILE);
        Files.writeString(options, invalid, StandardCharsets.UTF_8);

        ConfigManager manager = new ConfigManager(directory);
        manager.load();
        manager.options().enabled = false;
        manager.save();

        assertEquals(invalid, Files.readString(options, StandardCharsets.UTF_8));
    }
}
