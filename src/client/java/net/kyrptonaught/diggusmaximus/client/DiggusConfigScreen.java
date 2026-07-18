package net.kyrptonaught.diggusmaximus.client;

import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.kyrptonaught.diggusmaximus.ExcavateTypes;
import net.kyrptonaught.diggusmaximus.config.Blacklist;
import net.kyrptonaught.diggusmaximus.config.BlockCategory;
import net.kyrptonaught.diggusmaximus.config.ConfigOptions;
import net.kyrptonaught.diggusmaximus.config.ExcavatingShapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Minecraft-native editor for every legacy configuration field. */
public final class DiggusConfigScreen extends Screen {
    private static final int PAGE_COUNT = 4;
    private final Screen parent;
    private final ConfigOptions draftOptions;
    private final Blacklist draftBlacklist;
    private final BlockCategory draftGrouping;
    private final ExcavatingShapes draftShapes;
    private final List<Label> labels = new ArrayList<>();
    private int page;

    public DiggusConfigScreen(Screen parent) {
        super(Component.literal("Diggus Maximus Config"));
        this.parent = parent;
        this.draftOptions = copyOptions(DiggusMaximusMod.getOptions());
        this.draftBlacklist = copyBlacklist(DiggusMaximusMod.getBlackList());
        this.draftGrouping = copyGrouping(DiggusMaximusMod.getGrouping());
        this.draftShapes = copyShapes(DiggusMaximusMod.getExcavatingShapes());
    }

    @Override
    protected void init() {
        labels.clear();
        switch (page) {
            case 0 -> addGeneralPage();
            case 1 -> addToolPage();
            case 2 -> addListPage();
            case 3 -> addShapePage();
            default -> throw new IllegalStateException("Unknown config page " + page);
        }

        int arrowWidth = Math.min(40, Math.max(24, width / 8));
        int doneWidth = Math.min(150, Math.max(60, width - arrowWidth * 2 - 40));
        int navigationWidth = arrowWidth * 2 + doneWidth + 20;
        int navigationX = (width - navigationWidth) / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(navigationX, height - 28, arrowWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
                .bounds(navigationX + arrowWidth + 10, height - 28, doneWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(navigationX + arrowWidth + doneWidth + 20, height - 28, arrowWidth, 20).build());
    }

    private void addGeneralPage() {
        var options = draftOptions;
        toggle(0, "key.diggusmaximus.config.enabled", () -> options.enabled, value -> options.enabled = value);
        text(1, "key.diggusmaximus.config.hotkey", options.keybinding.rawKey, value -> options.keybinding.rawKey = value);
        toggle(2, "key.diggusmaximus.config.invertactivation", () -> options.invertActivation, value -> options.invertActivation = value);
        toggle(3, "key.diggusmaximus.config.sneaktoexcavate", () -> options.sneakToExcavate, value -> options.sneakToExcavate = value);
        toggle(4, "key.diggusmaximus.config.minediag", () -> options.mineDiag, value -> options.mineDiag = value);
        integer(5, "key.diggusmaximus.config.maxmine", options.maxMinedBlocks, 1, 2048, value -> options.maxMinedBlocks = value);
        integer(6, "key.diggusmaximus.config.maxdistance", options.maxMineDistance, 1, 128, value -> options.maxMineDistance = value);
    }

    private void addToolPage() {
        var options = draftOptions;
        toggle(0, "key.diggusmaximus.config.autopickup", () -> options.autoPickup, value -> options.autoPickup = value);
        toggle(1, "key.diggusmaximus.config.requirestool", () -> options.requiresTool, value -> options.requiresTool = value);
        toggle(2, "key.diggusmaximus.config.toolduribility", () -> options.toolDurability, value -> options.toolDurability = value);
        toggle(3, "key.diggusmaximus.config.stopontoolbreak", () -> options.stopOnToolBreak, value -> options.stopOnToolBreak = value);
        toggle(4, "key.diggusmaximus.config.dontbreaktool", () -> options.dontBreakTool, value -> options.dontBreakTool = value);
        toggle(5, "key.diggusmaximus.config.playerexhaustion", () -> options.playerExhaustion, value -> options.playerExhaustion = value);
        decimal(6, "key.diggusmaximus.config.exhaustionmultiplier", options.exhaustionMultiplier, value -> options.exhaustionMultiplier = value);
        text(7, "key.diggusmaximus.config.toollist", String.join(",", options.tools), value -> options.tools = csvSet(value));
    }

    private void addListPage() {
        var blacklist = draftBlacklist;
        var grouping = draftGrouping;
        toggle(0, "key.diggusmaximus.config.invertlist", () -> blacklist.isWhitelist, value -> blacklist.isWhitelist = value);
        text(1, "key.diggusmaximus.config.blacklist", String.join(",", blacklist.blacklistedBlocks), value -> blacklist.blacklistedBlocks = csvSet(value));
        toggle(2, "key.diggusmaximus.config.customgrouping", () -> grouping.customGrouping, value -> grouping.customGrouping = value);
        text(3, "key.diggusmaximus.config.grouplist", String.join(";", grouping.groups), value -> grouping.groups = semicolonList(value));
    }

    private void addShapePage() {
        var shapes = draftShapes;
        toggle(0, "key.diggusmaximus.config.enableshapes", () -> shapes.enableShapes, value -> shapes.enableShapes = value);
        toggle(1, "key.diggusmaximus.config.includedifblock", () -> shapes.includeDifBlocks, value -> shapes.includeDifBlocks = value);
        text(2, "key.diggusmaximus.config.shapekey", shapes.shapeKey.rawKey, value -> shapes.shapeKey.rawKey = value);
        text(3, "key.diggusmaximus.config.cyclekey", shapes.cycleKey.rawKey, value -> shapes.cycleKey.rawKey = value);

        int[] selected = {shapes.selectedShape.ordinal()};
        Button button = Button.builder(shapeMessage(shapes.selectedShape), pressed -> {
            selected[0] = (selected[0] + 1) % ExcavateTypes.shape.values().length;
            shapes.selectedShape = ExcavateTypes.shape.values()[selected[0]];
            pressed.setMessage(shapeMessage(shapes.selectedShape));
        }).bounds(x(4), y(4), controlWidth(), 20).build();
        addRenderableWidget(button);
    }

    private void toggle(int index, String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Button button = Button.builder(toggleMessage(key, getter.get()), pressed -> {
            boolean value = !getter.get();
            setter.accept(value);
            pressed.setMessage(toggleMessage(key, value));
        }).bounds(x(index), y(index), controlWidth(), 20).build();
        addRenderableWidget(button);
    }

    private void integer(int index, String key, int initial, int min, int max, Consumer<Integer> setter) {
        text(index, key, Integer.toString(initial), value -> {
            try {
                setter.accept(Math.clamp(Integer.parseInt(value), min, max));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void decimal(int index, String key, float initial, Consumer<Float> setter) {
        text(index, key, Float.toString(initial), value -> {
            try {
                setter.accept(Float.parseFloat(value));
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void text(int index, String key, String initial, Consumer<String> responder) {
        int x = x(index);
        int y = y(index);
        EditBox box = new EditBox(font, x, y, controlWidth(), 20, Component.translatable(key));
        // Never truncate a legacy list merely because it is longer than the visible edit box.
        box.setMaxLength(initial.length() > Integer.MAX_VALUE - 4096 ? Integer.MAX_VALUE : Math.max(4096, initial.length() + 4096));
        box.setValue(initial);
        box.setResponder(responder);
        labels.add(new Label(Component.translatable(key), x, y - 10));
        addRenderableWidget(box);
    }

    private int x(int index) {
        int gap = Math.min(20, Math.max(6, width / 40));
        int totalWidth = controlWidth() * 2 + gap;
        return (width - totalWidth) / 2 + (index % 2) * (controlWidth() + gap);
    }

    private int controlWidth() {
        int gap = Math.min(20, Math.max(6, width / 40));
        return Math.max(40, Math.min(240, (Math.max(width, 100) - 20 - gap) / 2));
    }

    private int y(int index) {
        return 53 + (index / 2) * 37;
    }

    private void changePage(int delta) {
        page = Math.floorMod(page + delta, PAGE_COUNT);
        rebuildWidgets();
    }

    private Component pageTitle() {
        return switch (page) {
            case 0 -> Component.translatable("key.diggusmaximus.config.category");
            case 1 -> Component.translatable("key.diggusmaximus.config.toollist");
            case 2 -> Component.translatable("key.diggusmaximus.config.blacklistcat");
            case 3 -> Component.translatable("key.diggusmaximus.config.shapecat");
            default -> Component.empty();
        };
    }

    private static Component toggleMessage(String key, boolean value) {
        return Component.translatable(key).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
    }

    private static Component shapeMessage(ExcavateTypes.shape shape) {
        return Component.translatable("key.diggusmaximus.config.selectedshape")
                .append(": ")
                .append(Component.translatable("diggusmaximus.shape." + shape));
    }

    private static HashSet<String> csvSet(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static List<String> semicolonList(String value) {
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFF);
        graphics.centeredText(font, pageTitle(), width / 2, 29, 0xAAAAAA);
        labels.forEach(label -> graphics.text(font, label.text, label.x, label.y, 0xFFFFFF));
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }

    private void saveAndClose() {
        applyDraft();
        DiggusMaximusMod.saveAndReloadDerivedConfig();
        DiggusKeyMappings.applyConfiguredKeys();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private void applyDraft() {
        ConfigOptions options = DiggusMaximusMod.getOptions();
        options.enabled = draftOptions.enabled;
        options.keybinding.rawKey = draftOptions.keybinding.rawKey;
        options.invertActivation = draftOptions.invertActivation;
        options.sneakToExcavate = draftOptions.sneakToExcavate;
        options.mineDiag = draftOptions.mineDiag;
        options.maxMinedBlocks = draftOptions.maxMinedBlocks;
        options.maxMineDistance = draftOptions.maxMineDistance;
        options.autoPickup = draftOptions.autoPickup;
        options.requiresTool = draftOptions.requiresTool;
        options.dontBreakTool = draftOptions.dontBreakTool;
        options.stopOnToolBreak = draftOptions.stopOnToolBreak;
        options.toolDurability = draftOptions.toolDurability;
        options.playerExhaustion = draftOptions.playerExhaustion;
        options.exhaustionMultiplier = draftOptions.exhaustionMultiplier;
        options.tools = new HashSet<>(draftOptions.tools);

        Blacklist blacklist = DiggusMaximusMod.getBlackList();
        blacklist.isWhitelist = draftBlacklist.isWhitelist;
        blacklist.blacklistedBlocks = new HashSet<>(draftBlacklist.blacklistedBlocks);

        BlockCategory grouping = DiggusMaximusMod.getGrouping();
        grouping.customGrouping = draftGrouping.customGrouping;
        grouping.groups = new ArrayList<>(draftGrouping.groups);

        ExcavatingShapes shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = draftShapes.enableShapes;
        shapes.includeDifBlocks = draftShapes.includeDifBlocks;
        shapes.shapeKey.rawKey = draftShapes.shapeKey.rawKey;
        shapes.cycleKey.rawKey = draftShapes.cycleKey.rawKey;
        shapes.selectedShape = draftShapes.selectedShape;
    }

    private static ConfigOptions copyOptions(ConfigOptions source) {
        ConfigOptions copy = new ConfigOptions();
        copy.enabled = source.enabled;
        copy.keybinding.rawKey = source.keybinding.rawKey;
        copy.invertActivation = source.invertActivation;
        copy.sneakToExcavate = source.sneakToExcavate;
        copy.mineDiag = source.mineDiag;
        copy.maxMinedBlocks = source.maxMinedBlocks;
        copy.maxMineDistance = source.maxMineDistance;
        copy.autoPickup = source.autoPickup;
        copy.requiresTool = source.requiresTool;
        copy.dontBreakTool = source.dontBreakTool;
        copy.stopOnToolBreak = source.stopOnToolBreak;
        copy.toolDurability = source.toolDurability;
        copy.playerExhaustion = source.playerExhaustion;
        copy.exhaustionMultiplier = source.exhaustionMultiplier;
        copy.tools = new HashSet<>(source.tools);
        return copy;
    }

    private static Blacklist copyBlacklist(Blacklist source) {
        Blacklist copy = new Blacklist();
        copy.isWhitelist = source.isWhitelist;
        copy.blacklistedBlocks = new HashSet<>(source.blacklistedBlocks);
        return copy;
    }

    private static BlockCategory copyGrouping(BlockCategory source) {
        BlockCategory copy = new BlockCategory();
        copy.customGrouping = source.customGrouping;
        copy.groups = new ArrayList<>(source.groups);
        return copy;
    }

    private static ExcavatingShapes copyShapes(ExcavatingShapes source) {
        ExcavatingShapes copy = new ExcavatingShapes();
        copy.enableShapes = source.enableShapes;
        copy.includeDifBlocks = source.includeDifBlocks;
        copy.shapeKey.rawKey = source.shapeKey.rawKey;
        copy.cycleKey.rawKey = source.cycleKey.rawKey;
        copy.selectedShape = source.selectedShape;
        return copy;
    }

    private record Label(Component text, int x, int y) {
    }
}
