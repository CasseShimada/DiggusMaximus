package net.kyrptonaught.diggusmaximus.config;

import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.diggusmaximus.ExcavateTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the four legacy JSON5 files without an external configuration library. */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("diggusmaximus-config");
    public static final String OPTIONS_FILE = "config.json5";
    public static final String BLACKLIST_FILE = "blacklist.json5";
    public static final String GROUPING_FILE = "grouping.json5";
    public static final String SHAPES_FILE = "excavatingshapes.json5";
    private static final Set<String> OPTION_KEYS = Set.of(
            "enabled", "keybinding", "invertActivation", "sneakToExcavate", "mineDiag",
            "maxMinedBlocks", "maxMineDistance", "autoPickup", "requiresTool", "dontBreakTool",
            "stopOnToolBreak", "toolDurability", "playerExhaustion", "exhaustionMultiplier", "tools"
    );
    private static final Set<String> BLACKLIST_KEYS = Set.of("isWhitelist", "blacklistedBlocks");
    private static final Set<String> GROUPING_KEYS = Set.of("customGrouping", "groups");
    private static final Set<String> SHAPE_KEYS = Set.of(
            "enableShapes", "includeDifBlocks", "shapeKey", "cycleKey", "selectedShape"
    );

    private final Path directory;
    private final Map<String, Document> documents = new LinkedHashMap<>();
    private ConfigOptions options = new ConfigOptions();
    private Blacklist blacklist = new Blacklist();
    private BlockCategory grouping = new BlockCategory();
    private ExcavatingShapes shapes = new ExcavatingShapes();

    public ConfigManager(String modId) {
        this(FabricLoader.getInstance().getConfigDir().resolve(modId));
    }

    ConfigManager(Path directory) {
        this.directory = directory;
    }

    public synchronized void load() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create config directory " + directory, exception);
        }

        documents.clear();
        options = new ConfigOptions();
        blacklist = new Blacklist();
        grouping = new BlockCategory();
        shapes = new ExcavatingShapes();

        Document optionDocument = read(OPTIONS_FILE);
        Document blacklistDocument = read(BLACKLIST_FILE);
        Document groupingDocument = read(GROUPING_FILE);
        Document shapesDocument = read(SHAPES_FILE);

        loadOptions(optionDocument.root);
        loadBlacklist(blacklistDocument.root);
        loadGrouping(groupingDocument.root);
        loadShapes(shapesDocument.root);

        if (documents.values().stream().anyMatch(Document::newFile)
                || needsDefaults(optionDocument, OPTION_KEYS)
                || needsNestedKeyDefault(optionDocument, "keybinding")
                || needsDefaults(blacklistDocument, BLACKLIST_KEYS)
                || needsDefaults(groupingDocument, GROUPING_KEYS)
                || needsDefaults(shapesDocument, SHAPE_KEYS)
                || needsNestedKeyDefault(shapesDocument, "shapeKey")
                || needsNestedKeyDefault(shapesDocument, "cycleKey")) {
            save();
        }
    }

    public synchronized void save() {
        updateOptions(document(OPTIONS_FILE).root);
        updateBlacklist(document(BLACKLIST_FILE).root);
        updateGrouping(document(GROUPING_FILE).root);
        updateShapes(document(SHAPES_FILE).root);

        documents.forEach((name, document) -> {
            if (!document.writable) {
                LOGGER.error("Not overwriting unreadable config file {}", directory.resolve(name));
                return;
            }
            write(name, document.root);
            document.newFile = false;
        });
    }

    public ConfigOptions options() {
        return options;
    }

    public Blacklist blacklist() {
        return blacklist;
    }

    public BlockCategory grouping() {
        return grouping;
    }

    public ExcavatingShapes shapes() {
        return shapes;
    }

    public Path directory() {
        return directory;
    }

    private Document read(String name) {
        Path path = directory.resolve(name);
        boolean exists = Files.exists(path);
        Document document;
        if (!exists) {
            document = new Document(new LinkedHashMap<>(), true, true);
        } else {
            try {
                document = new Document(Json5.parseObject(Files.readString(path, StandardCharsets.UTF_8)), true, false);
            } catch (Exception exception) {
                LOGGER.error("Failed to read {}; using defaults in memory and preserving the original file", path, exception);
                document = new Document(new LinkedHashMap<>(), false, false);
            }
        }
        documents.put(name, document);
        return document;
    }

    private void write(String name, Map<String, Object> root) {
        Path target = directory.resolve(name);
        Path temporary = directory.resolve(name + ".tmp");
        try {
            Files.writeString(temporary, Json5.writeObject(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException suppressed) {
                exception.addSuppressed(suppressed);
            }
            LOGGER.error("Failed to save {}", target, exception);
        }
    }

    private Document document(String name) {
        Document document = documents.get(name);
        if (document == null) {
            throw new IllegalStateException("Config manager has not been loaded");
        }
        return document;
    }

    private static boolean needsDefaults(Document document, Set<String> requiredKeys) {
        return document.writable && !document.root.keySet().containsAll(requiredKeys);
    }

    private static boolean needsNestedKeyDefault(Document document, String key) {
        Object value = document.root.get(key);
        return document.writable && value instanceof Map<?, ?> map && !map.containsKey("rawKey");
    }

    private void loadOptions(Map<String, Object> root) {
        options.enabled = bool(root, "enabled", options.enabled);
        loadKey(root.get("keybinding"), options.keybinding);
        options.invertActivation = bool(root, "invertActivation", options.invertActivation);
        options.sneakToExcavate = bool(root, "sneakToExcavate", options.sneakToExcavate);
        options.mineDiag = bool(root, "mineDiag", options.mineDiag);
        options.maxMinedBlocks = integer(root, "maxMinedBlocks", options.maxMinedBlocks);
        options.maxMineDistance = integer(root, "maxMineDistance", options.maxMineDistance);
        options.autoPickup = bool(root, "autoPickup", options.autoPickup);
        options.requiresTool = bool(root, "requiresTool", options.requiresTool);
        options.dontBreakTool = bool(root, "dontBreakTool", options.dontBreakTool);
        options.stopOnToolBreak = bool(root, "stopOnToolBreak", options.stopOnToolBreak);
        options.toolDurability = bool(root, "toolDurability", options.toolDurability);
        options.playerExhaustion = bool(root, "playerExhaustion", options.playerExhaustion);
        options.exhaustionMultiplier = decimal(root, "exhaustionMultiplier", options.exhaustionMultiplier);
        options.tools = new HashSet<>(strings(root.get("tools")));
    }

    private void loadBlacklist(Map<String, Object> root) {
        blacklist.isWhitelist = bool(root, "isWhitelist", blacklist.isWhitelist);
        blacklist.blacklistedBlocks = new HashSet<>(strings(root.get("blacklistedBlocks")));
    }

    private void loadGrouping(Map<String, Object> root) {
        grouping.customGrouping = bool(root, "customGrouping", grouping.customGrouping);
        grouping.groups = new ArrayList<>(strings(root.get("groups")));
    }

    private void loadShapes(Map<String, Object> root) {
        shapes.enableShapes = bool(root, "enableShapes", shapes.enableShapes);
        shapes.includeDifBlocks = bool(root, "includeDifBlocks", shapes.includeDifBlocks);
        loadKey(root.get("shapeKey"), shapes.shapeKey);
        loadKey(root.get("cycleKey"), shapes.cycleKey);
        Object selected = root.get("selectedShape");
        if (selected != null) {
            try {
                shapes.selectedShape = ExcavateTypes.shape.valueOf(String.valueOf(selected));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring unknown selectedShape '{}'", selected);
            }
        }
    }

    private void updateOptions(Map<String, Object> root) {
        root.put("enabled", options.enabled);
        root.put("keybinding", updateKey(root.get("keybinding"), options.keybinding));
        root.put("invertActivation", options.invertActivation);
        root.put("sneakToExcavate", options.sneakToExcavate);
        root.put("mineDiag", options.mineDiag);
        root.put("maxMinedBlocks", options.maxMinedBlocks);
        root.put("maxMineDistance", options.maxMineDistance);
        root.put("autoPickup", options.autoPickup);
        root.put("requiresTool", options.requiresTool);
        root.put("dontBreakTool", options.dontBreakTool);
        root.put("stopOnToolBreak", options.stopOnToolBreak);
        root.put("toolDurability", options.toolDurability);
        root.put("playerExhaustion", options.playerExhaustion);
        root.put("exhaustionMultiplier", options.exhaustionMultiplier);
        root.put("tools", sorted(options.tools));
    }

    private void updateBlacklist(Map<String, Object> root) {
        root.put("isWhitelist", blacklist.isWhitelist);
        root.put("blacklistedBlocks", sorted(blacklist.blacklistedBlocks));
    }

    private void updateGrouping(Map<String, Object> root) {
        root.put("customGrouping", grouping.customGrouping);
        root.put("groups", new ArrayList<>(grouping.groups));
    }

    private void updateShapes(Map<String, Object> root) {
        root.put("enableShapes", shapes.enableShapes);
        root.put("includeDifBlocks", shapes.includeDifBlocks);
        root.put("shapeKey", updateKey(root.get("shapeKey"), shapes.shapeKey));
        root.put("cycleKey", updateKey(root.get("cycleKey"), shapes.cycleKey));
        root.put("selectedShape", shapes.selectedShape.name());
    }

    private static void loadKey(Object value, KeyBindingConfig target) {
        if (value instanceof Map<?, ?> map) {
            target.respectsInvert = bool(map, "respectsInvert", target.respectsInvert);
            target.unknownIsActivated = bool(map, "unknownIsActivated", target.unknownIsActivated);
            target.defaultKey = string(map.get("defaultKey"), target.defaultKey);
            target.rawKey = string(map.get("rawKey"), target.rawKey);
            target.modID = string(map.get("modID"), target.modID);
        } else if (value instanceof String rawKey) {
            target.rawKey = rawKey;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object updateKey(Object value, KeyBindingConfig source) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = (Map<String, Object>) map;
            // Object-form keys were never emitted by the legacy serializer, but retain support for
            // configs written by migration previews without injecting fields the user did not have.
            if (result.containsKey("respectsInvert")) result.put("respectsInvert", source.respectsInvert);
            if (result.containsKey("unknownIsActivated")) result.put("unknownIsActivated", source.unknownIsActivated);
            if (result.containsKey("defaultKey")) result.put("defaultKey", source.defaultKey);
            result.put("rawKey", source.rawKey);
            if (result.containsKey("modID")) result.put("modID", source.modID);
            return result;
        }

        // The legacy keybinding serializer represented this value as a JSON string.
        // Keep that shape for existing and newly-created files instead of silently migrating it.
        return source.rawKey;
    }

    private static boolean bool(Map<?, ?> root, String key, boolean fallback) {
        Object value = root.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            if (string.equalsIgnoreCase("true")) return true;
            if (string.equalsIgnoreCase("false")) return false;
        }
        return fallback;
    }

    private static int integer(Map<String, Object> root, String key, int fallback) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float decimal(Map<String, Object> root, String key, float fallback) {
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return value == null ? fallback : Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(entry -> {
                if (entry != null) result.add(String.valueOf(entry));
            });
        }
        return result;
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    private static final class Document {
        private final Map<String, Object> root;
        private final boolean writable;
        private boolean newFile;

        private Document(Map<String, Object> root, boolean writable, boolean newFile) {
            this.root = root;
            this.writable = writable;
            this.newFile = newFile;
        }

        private boolean newFile() {
            return newFile;
        }
    }
}
