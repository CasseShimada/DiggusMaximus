package net.kyrptonaught.diggusmaximus.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.kyrptonaught.diggusmaximus.ExcavateTypes;
import net.kyrptonaught.diggusmaximus.PendingExcavation;
import net.kyrptonaught.diggusmaximus.StartExcavatePacket;
import net.kyrptonaught.diggusmaximus.client.DiggusConfigScreen;
import net.kyrptonaught.diggusmaximus.client.DiggusKeyMappings;
import net.kyrptonaught.diggusmaximus.config.ConfigManager;
import net.kyrptonaught.diggusmaximus.config.modmenu.ModMenuIntegration;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** End-to-end checks that require a real 26.2 client, network connection, and integrated server. */
@SuppressWarnings("UnstableApiUsage")
public final class DiggusMaximusClientGameTest implements FabricClientGameTest {
    private static final BlockPos TARGET = new BlockPos(0, 65, -3);
    private static final Set<BlockPos> DENIED_BREAKS = ConcurrentHashMap.newKeySet();
    private static final Map<BlockPos, AtomicBoolean> COUNTED_BREAKS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, BlockPos> REENTRANT_BREAKS = new ConcurrentHashMap<>();
    private static final AtomicBoolean BREAK_GUARD_REGISTERED = new AtomicBoolean();

    @Override
    public void runTest(ClientGameTestContext context) {
        registerBreakGuard();
        configScreenSavesAndReloads(context);
        ordinaryExcavationBreaksConnectedBlocks(context);
        dedicatedServerConnectionExcavates(context);
    }

    private static void configScreenSavesAndReloads(ClientGameTestContext context) {
        DiggusMaximusMod.getOptions().maxMinedBlocks = 40;
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            boolean createsNativeScreen = context.computeOnClient(client ->
                    new ModMenuIntegration().getModConfigScreenFactory().create(null) instanceof DiggusConfigScreen);
            if (!createsNativeScreen) {
                throw new AssertionError("Mod Menu entrypoint did not create the native config screen");
            }
        }
        context.setScreen(() -> new DiggusConfigScreen(null));
        context.waitForScreen(DiggusConfigScreen.class);
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            if (!(screen instanceof DiggusConfigScreen)) {
                throw new AssertionError("Diggus Maximus config screen was not open");
            }
            screen.resize(320, 240);
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget
                        && (widget.getX() < 0 || widget.getX() + widget.getWidth() > 320)) {
                    throw new AssertionError("Config widget was clipped at 320px width: " + widget.getMessage());
                }
            }
            EditBox maximumBlocks = screen.children().stream()
                    .filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast)
                    .filter(box -> box.getY() == 127)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Max mined blocks edit box was not present"));
            maximumBlocks.setValue("23");
            screen.children().stream()
                    .filter(Button.class::isInstance)
                    .map(Button.class::cast)
                    .filter(button -> button.getMessage().equals(net.minecraft.network.chat.CommonComponents.GUI_DONE))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Done button was not present"))
                    .onPress(new KeyEvent(257, 0, 0)); // GLFW_KEY_ENTER
        });
        context.waitFor(client -> !(client.gui.screen() instanceof DiggusConfigScreen));

        String saved;
        try {
            saved = Files.readString(DiggusMaximusMod.configManager.directory().resolve(ConfigManager.OPTIONS_FILE));
        } catch (Exception exception) {
            throw new AssertionError("Unable to read config saved by the config screen", exception);
        }
        if (!saved.contains("\"maxMinedBlocks\": 23")) {
            throw new AssertionError("Config screen close did not save maxMinedBlocks");
        }

        DiggusMaximusMod.configManager.load();
        if (DiggusMaximusMod.getOptions().maxMinedBlocks != 23) {
            throw new AssertionError("Saved config did not reload with maxMinedBlocks=23");
        }

        context.setScreen(() -> new DiggusConfigScreen(null));
        context.waitForScreen(DiggusConfigScreen.class);
        context.runOnClient(client -> {
            Screen screen = client.gui.screen();
            EditBox maximumBlocks = screen.children().stream()
                    .filter(EditBox.class::isInstance)
                    .map(EditBox.class::cast)
                    .filter(box -> box.getY() == 127)
                    .findFirst()
                    .orElseThrow();
            maximumBlocks.setValue("99");
            screen.onClose();
        });
        context.waitFor(client -> !(client.gui.screen() instanceof DiggusConfigScreen));
        if (DiggusMaximusMod.getOptions().maxMinedBlocks != 23) {
            throw new AssertionError("Closing without Done unexpectedly committed draft config changes");
        }
    }

    private static void shapeCycleKeyMovesForwardAndBackward(ClientGameTestContext context) {
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = true;
        shapes.selectedShape = ExcavateTypes.shape.LAYER;
        context.runOnClient(client -> {
            DiggusKeyMappings.cycle.setKey(com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.b"));
            KeyMapping.resetMapping();
        });

        context.getInput().pressKey(DiggusKeyMappings.cycle);
        context.waitTicks(2);
        if (shapes.selectedShape != ExcavateTypes.shape.HOLE) {
            throw new AssertionError("Cycle key did not move to the next shape");
        }

        context.getInput().holdShift();
        context.getInput().pressKey(DiggusKeyMappings.cycle);
        context.waitTicks(2);
        context.getInput().releaseShift();
        if (shapes.selectedShape != ExcavateTypes.shape.LAYER) {
            throw new AssertionError("Sneak + cycle did not move to the previous shape");
        }
    }

    private static void ordinaryExcavationBreaksConnectedBlocks(ClientGameTestContext context) {
        var options = DiggusMaximusMod.getOptions();
        options.enabled = true;
        options.invertActivation = false;
        options.sneakToExcavate = false;
        options.mineDiag = true;
        options.maxMinedBlocks = 40;
        options.maxMineDistance = 10;
        options.requiresTool = false;
        DiggusMaximusMod.reloadDerivedConfig();

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksDownload();
            nativeConfigKeyOpensScreen(context);
            nativeKeyMappingsPreserveLegacyActivationRules(context);
            shapeCycleKeyMovesForwardAndBackward(context);
            singleplayer.getServer().runCommand("fill -2 63 -4 2 63 2 minecraft:bedrock");
            singleplayer.getServer().runCommand("fill -1 64 -3 1 66 -3 minecraft:stone");
            singleplayer.getServer().runCommand("gamemode creative @p");
            singleplayer.getServer().runCommand("tp @p 0.5 64 0.5 180 0");
            context.waitTicks(10);

            context.getInput().lookAt(new BlockPos(0, 65, -3));
            context.getInput().holdKey(DiggusKeyMappings.activation);
            context.getInput().holdMouseFor(0, 2);
            context.getInput().releaseKey(DiggusKeyMappings.activation);
            context.waitTicks(20);

            int remaining = singleplayer.getServer().computeOnServer(server -> {
                int count = 0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = 64; y <= 66; y++) {
                        if (server.overworld().getBlockState(new BlockPos(x, y, -3)).is(Blocks.STONE)) {
                            count++;
                        }
                    }
                }
                return count;
            });
            if (remaining != 0) {
                throw new AssertionError("Expected all 9 connected stone blocks to be excavated; remaining=" + remaining);
            }

            survivalExcavationPreservesVanillaEffects(context, singleplayer);
            vanillaMiningRemainsUnaffected(context, singleplayer);
            quantityAndDistanceBoundsAreServerControlled(context, singleplayer);
            allShapeModesExecuteOnTheServer(context, singleplayer);
            blacklistWhitelistAndGroupingAreEnforcedPerBlock(context, singleplayer);
            toolAndDurabilityCombinations(context, singleplayer);
            playerExhaustionCombinations(context, singleplayer);
            enchantmentsAndBreakEventsUseVanillaPaths(context, singleplayer);
            reentrantBreakDoesNotStealTheSeedRequest(context, singleplayer);
            vanillaRestrictionsCancelTheSeedBreak(context, singleplayer);
            automaticPickupPreservesPartialRemainders(context, singleplayer);
            serverSneakActivationWorksWithoutACustomPacket(context, singleplayer);
            malformedAndStalePacketsDoNotExcavate(context, singleplayer);
            worldBorderStopsTheExcavationFrontier(context, singleplayer);
        }
    }

    private static void survivalExcavationPreservesVanillaEffects(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        var options = DiggusMaximusMod.getOptions();
        options.autoPickup = false;
        options.toolDurability = true;
        options.playerExhaustion = true;
        options.exhaustionMultiplier = 1.0F;
        DiggusMaximusMod.reloadDerivedConfig();

        singleplayer.getServer().runCommand("fill -2 63 -4 2 63 2 minecraft:bedrock");
        singleplayer.getServer().runCommand("fill -1 64 -3 1 66 -3 minecraft:redstone_ore");
        singleplayer.getServer().runCommand("clear @p");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        singleplayer.getServer().runCommand("gamemode survival @p");
        singleplayer.getServer().runCommand("effect give @p minecraft:haste 30 4 true");
        singleplayer.getServer().runCommand("tp @p 0.5 64 0.5 180 0");
        context.waitTicks(10);

        context.getInput().lookAt(new BlockPos(0, 65, -3));
        context.getInput().holdKey(DiggusKeyMappings.activation);
        BlockPos target = new BlockPos(0, 65, -3);
        String clientSetup = context.computeOnClient(client -> "mode=" + client.gameMode.getPlayerMode()
                + ", block=" + client.level.getBlockState(target)
                + ", held=" + client.player.getMainHandItem());
        boolean started = context.computeOnClient(client -> client.gameMode.startDestroyBlock(target, Direction.SOUTH));
        for (int tick = 0; tick < 100; tick++) {
            context.runOnClient(client -> client.gameMode.continueDestroyBlock(target, Direction.SOUTH));
            context.waitTick();
            if (context.computeOnClient(client -> client.level.getBlockState(target).isAir())) {
                break;
            }
        }
        context.getInput().releaseKey(DiggusKeyMappings.activation);
        context.waitTicks(20);

        SurvivalResult result = singleplayer.getServer().computeOnServer(server -> {
            int remaining = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = 64; y <= 66; y++) {
                    if (server.overworld().getBlockState(new BlockPos(x, y, -3)).is(Blocks.REDSTONE_ORE)) {
                        remaining++;
                    }
                }
            }
            var player = server.getPlayerList().getPlayers().getFirst();
            AABB evidenceArea = new AABB(-4, 62, -6, 4, 70, 1);
            int drops = server.overworld().getEntitiesOfClass(ItemEntity.class, evidenceArea, ItemEntity::isAlive).size();
            int experience = server.overworld().getEntitiesOfClass(ExperienceOrb.class, evidenceArea, ExperienceOrb::isAlive).size();
            return new SurvivalResult(remaining, player.getMainHandItem().getDamageValue(), drops, experience);
        });
        if (result.remaining != 0 || result.toolDamage != 9 || result.itemEntities == 0 || result.experienceOrbs == 0) {
            String clientAfter = context.computeOnClient(client -> "destroying=" + client.gameMode.isDestroying()
                    + ", stage=" + client.gameMode.getDestroyStage());
            throw new AssertionError("Survival excavation lost vanilla effects: " + result
                    + "; started=" + started + "; " + clientSetup + "; " + clientAfter);
        }
    }

    private record SurvivalResult(int remaining, int toolDamage, int itemEntities, int experienceOrbs) {
    }

    private static void nativeConfigKeyOpensScreen(ClientGameTestContext context) {
        context.runOnClient(client -> KeyMapping.click(DiggusKeyMappings.openConfig.getDefaultKey()));
        context.waitForScreen(DiggusConfigScreen.class);
        context.runOnClient(client -> client.gui.screen().onClose());
        context.waitFor(client -> !(client.gui.screen() instanceof DiggusConfigScreen));
    }

    private static void nativeKeyMappingsPreserveLegacyActivationRules(ClientGameTestContext context) {
        var options = DiggusMaximusMod.getOptions();
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        options.keybinding.rawKey = "not.a.valid.minecraft.key";
        options.invertActivation = false;
        DiggusKeyMappings.applyConfiguredKeys();
        context.waitTicks(2);
        if (!"not.a.valid.minecraft.key".equals(options.keybinding.rawKey)
                || DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("Invalid legacy key was rewritten or activated");
        }

        options.keybinding.rawKey = "key.keyboard.unknown";
        DiggusKeyMappings.applyConfiguredKeys();
        if (!DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("Explicit unknown activation key lost unknownIsActivated=true");
        }
        options.invertActivation = true;
        if (!DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("Explicit unknown activation key was incorrectly inverted");
        }

        options.keybinding.rawKey = "key.keyboard.g";
        options.invertActivation = false;
        DiggusKeyMappings.applyConfiguredKeys();
        DiggusKeyMappings.activation.setDown(false);
        if (DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("A released bound activation key was treated as pressed");
        }
        DiggusKeyMappings.activation.setDown(true);
        if (!DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("A pressed bound activation key was ignored");
        }
        options.invertActivation = true;
        if (DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("invertActivation did not invert a bound pressed key");
        }
        DiggusKeyMappings.activation.setDown(false);
        if (!DiggusKeyMappings.activationPressed()) {
            throw new AssertionError("invertActivation did not activate a released bound key");
        }

        shapes.shapeKey.rawKey = "key.keyboard.unknown";
        DiggusKeyMappings.applyConfiguredKeys();
        if (DiggusKeyMappings.shapePressed()) {
            throw new AssertionError("Unknown shape key ignored unknownIsActivated=false");
        }

        options.keybinding.rawKey = "key.keyboard.grave.accent";
        options.invertActivation = false;
        shapes.shapeKey.rawKey = "key.keyboard.unknown";
        DiggusKeyMappings.activation.setDown(false);
        DiggusKeyMappings.applyConfiguredKeys();
    }

    private static void vanillaMiningRemainsUnaffected(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 1 65 -3 minecraft:stone");
        context.waitTicks(5);

        breakCreative(context, TARGET, false, false);

        boolean valid = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.east()).is(Blocks.STONE)
        );
        if (!valid) {
            throw new AssertionError("Mining without an activation trigger affected adjacent blocks");
        }
    }

    private static void quantityAndDistanceBoundsAreServerControlled(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        var options = DiggusMaximusMod.getOptions();
        options.mineDiag = false;
        options.maxMinedBlocks = 3;
        options.maxMineDistance = 10;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("fill -2 65 -3 2 65 -3 minecraft:stone");
        context.waitTicks(5);
        breakCreative(context, TARGET, true, false);

        int firstRemaining = singleplayer.getServer().computeOnServer(server -> {
            int count = 0;
            for (int x = -2; x <= 2; x++) {
                if (server.overworld().getBlockState(new BlockPos(x, 65, -3)).is(Blocks.STONE)) count++;
            }
            return count;
        });
        if (firstRemaining != 2) {
            throw new AssertionError("maxMinedBlocks=3 removed the wrong number of blocks; remaining=" + firstRemaining);
        }

        singleplayer.getServer().runCommand("fill -2 65 -3 2 65 -3 minecraft:stone");
        options.maxMinedBlocks = 40;
        options.maxMineDistance = 1;
        DiggusMaximusMod.reloadDerivedConfig();
        context.waitTicks(5);
        breakCreative(context, TARGET, true, false);
        int distanceRemaining = singleplayer.getServer().computeOnServer(server -> {
            int count = 0;
            for (int x = -2; x <= 2; x++) {
                if (server.overworld().getBlockState(new BlockPos(x, 65, -3)).is(Blocks.STONE)) count++;
            }
            return count;
        });
        if (distanceRemaining != 2) {
            throw new AssertionError("maxMineDistance=1 did not preserve the two distance-2 endpoints; remaining=" + distanceRemaining);
        }
    }

    private static void allShapeModesExecuteOnTheServer(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        DiggusMaximusMod.getExcavatingShapes().enableShapes = true;
        DiggusMaximusMod.getExcavatingShapes().includeDifBlocks = false;

        for (ExcavateTypes.shape shape : ExcavateTypes.shape.values()) {
            BlockPos candidate = switch (shape) {
                case HORIZONTAL_LAYER -> TARGET.east();
                case LAYER, THREExTHREE, THREExTHREE_TUNNEL -> TARGET.above();
                case HOLE, ONExTWO_TUNNEL -> TARGET.north();
                case ONExTWO -> TARGET.below();
            };
            singleplayer.getServer().runCommand("fill -3 64 -6 3 68 -2 minecraft:air");
            singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
            singleplayer.getServer().runCommand("setblock " + candidate.getX() + " " + candidate.getY() + " " + candidate.getZ() + " minecraft:stone");
            context.waitTicks(5);
            sendShapePacket(context, TARGET, shape.ordinal());
            breakCreative(context, TARGET, false, false);

            boolean removed = singleplayer.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(TARGET).isAir()
                            && server.overworld().getBlockState(candidate).isAir()
            );
            if (!removed) {
                throw new AssertionError("Shape mode did not excavate its representative offset: " + shape);
            }
        }
    }

    private static void blacklistWhitelistAndGroupingAreEnforcedPerBlock(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = true;
        shapes.includeDifBlocks = true;
        var blacklist = DiggusMaximusMod.getBlackList();
        blacklist.blacklistedBlocks.add("minecraft:dirt");
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:dirt");
        context.waitTicks(5);
        sendShapePacket(context, TARGET, ExcavateTypes.shape.LAYER.ordinal());
        breakCreative(context, TARGET, false, false);
        boolean blacklistedRemained = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET.above()).is(Blocks.DIRT)
        );
        if (!blacklistedRemained) {
            throw new AssertionError("Shape excavation crossed into a blacklisted block");
        }

        singleplayer.getServer().runCommand("fill -3 64 -6 3 68 -2 minecraft:air");
        blacklist.isWhitelist = true;
        blacklist.blacklistedBlocks.clear();
        blacklist.blacklistedBlocks.add("#minecraft:base_stone_overworld");
        DiggusMaximusMod.reloadDerivedConfig();
        if (!blacklist.lookup.contains("minecraft:stone") || !blacklist.lookup.contains("minecraft:deepslate")) {
            throw new AssertionError("The configured vanilla block tag did not expand after tags loaded");
        }
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:deepslate");
        context.waitTicks(5);
        sendShapePacket(context, TARGET, ExcavateTypes.shape.LAYER.ordinal());
        breakCreative(context, TARGET, false, false);
        boolean whitelistRemovedBoth = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.above()).isAir()
        );
        if (!whitelistRemovedBoth) {
            throw new AssertionError("Whitelist tag did not permit both tagged blocks");
        }

        singleplayer.getServer().runCommand("fill -3 64 -6 3 68 -2 minecraft:air");
        blacklist.isWhitelist = false;
        blacklist.blacklistedBlocks.clear();
        shapes.enableShapes = false;
        shapes.includeDifBlocks = false;
        var grouping = DiggusMaximusMod.getGrouping();
        grouping.customGrouping = true;
        grouping.groups.add("minecraft:stone,minecraft:deepslate");
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:deepslate");
        context.waitTicks(5);
        breakCreative(context, TARGET, true, false);
        boolean groupedRemovedBoth = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.above()).isAir()
        );
        if (!groupedRemovedBoth) {
            throw new AssertionError("Custom grouping did not treat stone and deepslate as equivalent");
        }
    }

    private static void toolAndDurabilityCombinations(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, false);
        var options = DiggusMaximusMod.getOptions();
        options.mineDiag = false;
        options.requiresTool = true;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:dirt");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:dirt");
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        if (!singleplayer.getServer().computeOnServer(server -> server.overworld().getBlockState(TARGET.above()).is(Blocks.DIRT))) {
            throw new AssertionError("requiresTool=true excavated while the player was empty-handed");
        }

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:dirt");
        options.tools.add("minecraft:stick");
        singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK)));
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        boolean customToolWorked = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.above()).isAir()
        );
        if (!customToolWorked) {
            throw new AssertionError("Configured custom tool did not permit excavation");
        }

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        options.tools.clear();
        singleplayer.getServer().runOnServer(server -> {
            ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        boolean unbreakableWorked = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            return server.overworld().getBlockState(TARGET.above()).isAir()
                    && player.getMainHandItem().getDamageValue() == 0;
        });
        if (!unbreakableWorked) {
            throw new AssertionError("An unbreakable vanilla tool stopped satisfying requiresTool");
        }

        singleplayer.getServer().runCommand("fill 0 64 -3 0 66 -3 minecraft:stone");
        options.requiresTool = false;
        options.toolDurability = false;
        options.dontBreakTool = false;
        singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                .setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE)));
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        int noExtraDurabilityDamage = singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().getFirst().getMainHandItem().getDamageValue()
        );
        if (noExtraDurabilityDamage != 1) {
            throw new AssertionError("toolDurability=false should leave only the original block's damage; got " + noExtraDurabilityDamage);
        }

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        options.toolDurability = true;
        options.dontBreakTool = true;
        options.stopOnToolBreak = true;
        singleplayer.getServer().runOnServer(server -> {
            ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
            Tool tool = stack.get(DataComponents.TOOL);
            stack.set(DataComponents.TOOL, new Tool(tool.rules(), tool.defaultMiningSpeed(), 2, tool.canDestroyBlocksInCreative()));
            stack.setDamageValue(stack.getMaxDamage() - 4);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        boolean multiDamageStoppedSafely = singleplayer.getServer().computeOnServer(server -> {
            ItemStack held = server.getPlayerList().getPlayers().getFirst().getMainHandItem();
            return server.overworld().getBlockState(TARGET.above()).is(Blocks.STONE)
                    && held.getDamageValue() == held.getMaxDamage() - 2;
        });
        if (!multiDamageStoppedSafely) {
            throw new AssertionError("dontBreakTool did not account for TOOL.damagePerBlock=2");
        }

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        options.dontBreakTool = false;
        options.stopOnToolBreak = true;
        singleplayer.getServer().runOnServer(server -> {
            ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
            stack.setDamageValue(stack.getMaxDamage() - 1);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        boolean stoppedAfterBreak = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET.above()).is(Blocks.STONE)
                        && server.getPlayerList().getPlayers().getFirst().getMainHandItem().isEmpty()
        );
        if (!stoppedAfterBreak) {
            throw new AssertionError("stopOnToolBreak failed when the original block consumed the final durability");
        }

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:dirt");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:dirt");
        options.stopOnToolBreak = false;
        singleplayer.getServer().runOnServer(server -> {
            ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
            stack.setDamageValue(stack.getMaxDamage() - 1);
            server.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND, stack);
        });
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        boolean continuedAfterBreak = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.above()).isAir()
        );
        if (!continuedAfterBreak) {
            throw new AssertionError("stopOnToolBreak=false did not continue after the original tool broke");
        }
    }

    private static void automaticPickupPreservesPartialRemainders(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, false);
        var options = DiggusMaximusMod.getOptions();
        options.mineDiag = false;
        options.autoPickup = true;
        options.dontBreakTool = false;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("fill 0 64 -3 0 66 -3 minecraft:stone");
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var inventory = player.getInventory();
            inventory.clearContent();
            inventory.setSelectedSlot(0);
            var items = inventory.getNonEquipmentItems();
            for (int slot = 0; slot < items.size(); slot++) {
                items.set(slot, new ItemStack(Items.BARRIER, 64));
            }
            items.set(0, new ItemStack(Items.DIAMOND_PICKAXE));
            items.set(1, new ItemStack(Items.COBBLESTONE, 63));
            inventory.setChanged();
        });
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);

        PickupResult result = singleplayer.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            int inventoryCobblestone = player.getInventory().getNonEquipmentItems().stream()
                    .filter(stack -> stack.is(Items.COBBLESTONE))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            int worldCobblestone = server.overworld().getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(-2, 63, -5, 2, 68, -1),
                            ItemEntity::isAlive
                    ).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(Items.COBBLESTONE))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            int remaining = 0;
            for (int y = 64; y <= 66; y++) {
                if (server.overworld().getBlockState(new BlockPos(0, y, -3)).is(Blocks.STONE)) remaining++;
            }
            return new PickupResult(inventoryCobblestone, worldCobblestone, remaining);
        });
        if (result.inventoryCobblestone != 64 || result.worldCobblestone != 2 || result.remainingBlocks != 0) {
            throw new AssertionError("autoPickup did not preserve the seed drop and the unaccepted remainder: " + result);
        }
    }

    private record PickupResult(int inventoryCobblestone, int worldCobblestone, int remainingBlocks) {
    }

    private static void playerExhaustionCombinations(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, false);
        var options = DiggusMaximusMod.getOptions();
        options.mineDiag = false;
        options.dontBreakTool = false;
        options.playerExhaustion = false;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("fill 0 64 -3 0 66 -3 minecraft:stone");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        singleplayer.getServer().runOnServer(server -> setExhaustion(
                server.getPlayerList().getPlayers().getFirst().getFoodData(),
                0.0F
        ));
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        float disabled = singleplayer.getServer().computeOnServer(server -> getExhaustion(
                server.getPlayerList().getPlayers().getFirst().getFoodData()
        ));
        assertNear(0.005F, disabled, 0.001F, "playerExhaustion=false");

        singleplayer.getServer().runCommand("fill 0 64 -3 0 66 -3 minecraft:stone");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        options.playerExhaustion = true;
        options.exhaustionMultiplier = 3.0F;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runOnServer(server -> setExhaustion(
                server.getPlayerList().getPlayers().getFirst().getFoodData(),
                0.0F
        ));
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        float multiplied = singleplayer.getServer().computeOnServer(server -> getExhaustion(
                server.getPlayerList().getPlayers().getFirst().getFoodData()
        ));
        assertNear(0.035F, multiplied, 0.001F, "exhaustionMultiplier=3");
    }

    private static void enchantmentsAndBreakEventsUseVanillaPaths(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, false);
        DiggusMaximusMod.getOptions().mineDiag = false;
        DiggusMaximusMod.getOptions().dontBreakTool = false;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:redstone_ore");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:redstone_ore");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        singleplayer.getServer().runCommand("enchant @p minecraft:silk_touch 1");
        resetPlayerExperience(singleplayer);
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        LootResult silk = collectLootResult(singleplayer, Items.REDSTONE_ORE);
        if (silk.itemCount != 2 || silk.experienceOrbs != 0 || silk.playerExperience != 0) {
            throw new AssertionError("Silk Touch chain did not preserve vanilla loot/XP: " + silk);
        }

        singleplayer.getServer().runCommand("kill @e[type=minecraft:item]");
        singleplayer.getServer().runCommand("kill @e[type=minecraft:experience_orb]");
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:redstone_ore");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:redstone_ore");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        singleplayer.getServer().runCommand("enchant @p minecraft:fortune 3");
        resetPlayerExperience(singleplayer);
        context.waitTicks(5);
        breakSurvival(context, TARGET, true, false);
        LootResult fortune = collectLootResult(singleplayer, Items.REDSTONE);
        if (fortune.itemCount < 2 || (fortune.experienceOrbs == 0 && fortune.playerExperience == 0)) {
            throw new AssertionError("Fortune chain did not preserve vanilla loot/XP: " + fortune);
        }

        singleplayer.getServer().runCommand("fill 0 65 -3 0 67 -3 minecraft:stone");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        BlockPos denied = TARGET.above();
        BlockPos beyond = denied.above();
        COUNTED_BREAKS.put(TARGET, new AtomicBoolean());
        COUNTED_BREAKS.put(denied, new AtomicBoolean());
        COUNTED_BREAKS.put(beyond, new AtomicBoolean());
        DENIED_BREAKS.add(denied);
        boolean seedCalled;
        boolean deniedCalled;
        boolean beyondCalled;
        try {
            context.waitTicks(3);
            breakSurvival(context, TARGET, true, false);
        } finally {
            DENIED_BREAKS.remove(denied);
            seedCalled = COUNTED_BREAKS.remove(TARGET).get();
            deniedCalled = COUNTED_BREAKS.remove(denied).get();
            beyondCalled = COUNTED_BREAKS.remove(beyond).get();
        }
        boolean eventStoppedFrontier = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(denied).is(Blocks.STONE)
                        && server.overworld().getBlockState(denied.above()).is(Blocks.STONE)
        );
        if (!eventStoppedFrontier || !seedCalled || !deniedCalled || beyondCalled) {
            throw new AssertionError("Fabric BEFORE cancellation was bypassed or the queue crossed the denied block");
        }
    }

    private static void reentrantBreakDoesNotStealTheSeedRequest(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        DiggusMaximusMod.getOptions().mineDiag = false;
        DiggusMaximusMod.reloadDerivedConfig();
        BlockPos nested = TARGET.east(2);
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 2 65 -3 minecraft:stone");
        REENTRANT_BREAKS.put(TARGET, nested);
        try {
            context.waitTicks(3);
            breakCreative(context, TARGET, true, false);
        } finally {
            REENTRANT_BREAKS.clear();
        }
        var result = singleplayer.getServer().computeOnServer(server -> new ReentrantResult(
                server.overworld().getBlockState(TARGET).isAir(),
                server.overworld().getBlockState(TARGET.above()).isAir(),
                server.overworld().getBlockState(nested).isAir(),
                "seed=" + server.overworld().getBlockState(TARGET)
                        + ", extra=" + server.overworld().getBlockState(TARGET.above())
                        + ", nested=" + server.overworld().getBlockState(nested)
        ));
        if (!result.seedAir() || !result.extraAir() || !result.nestedAir()) {
            throw new AssertionError("A synchronous nested vanilla break changed outer behavior: " + result.description());
        }
    }

    private record ReentrantResult(boolean seedAir, boolean extraAir, boolean nestedAir, String description) {
    }

    private static void vanillaRestrictionsCancelTheSeedBreak(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, false);
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        singleplayer.getServer().runCommand("gamemode adventure @p");
        singleplayer.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");
        context.waitTicks(5);
        context.getInput().lookAt(TARGET);
        context.getInput().holdKey(DiggusKeyMappings.activation);
        context.getInput().holdMouseFor(0, 5);
        context.getInput().releaseKey(DiggusKeyMappings.activation);
        context.waitTicks(10);
        boolean bothRemain = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).is(Blocks.STONE)
                        && server.overworld().getBlockState(TARGET.above()).is(Blocks.STONE)
        );
        if (!bothRemain) {
            throw new AssertionError("Adventure-mode vanilla restriction did not cancel the seed and chain break");
        }
    }

    private static LootResult collectLootResult(TestSingleplayerContext singleplayer, net.minecraft.world.item.Item item) {
        return singleplayer.getServer().computeOnServer(server -> {
            AABB area = new AABB(-2, 63, -5, 2, 69, -1);
            int itemCount = server.overworld().getEntitiesOfClass(ItemEntity.class, area, ItemEntity::isAlive).stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(item))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            int experience = server.overworld().getEntitiesOfClass(ExperienceOrb.class, area, ExperienceOrb::isAlive).size();
            int playerExperience = server.getPlayerList().getPlayers().getFirst().totalExperience;
            return new LootResult(itemCount, experience, playerExperience);
        });
    }

    private static void resetPlayerExperience(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.totalExperience = 0;
            player.experienceLevel = 0;
            player.experienceProgress = 0.0F;
        });
    }

    private record LootResult(int itemCount, int experienceOrbs, int playerExperience) {
    }

    private static void registerBreakGuard() {
        if (BREAK_GUARD_REGISTERED.compareAndSet(false, true)) {
            PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                    {
                        AtomicBoolean counter = COUNTED_BREAKS.get(pos);
                        if (counter != null && !counter.compareAndSet(false, true)) {
                            throw new AssertionError("Fabric BEFORE was invoked more than once for " + pos);
                        }
                        BlockPos nested = REENTRANT_BREAKS.remove(pos);
                        if (nested != null && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            serverPlayer.gameMode.destroyBlock(nested);
                        }
                        return !DENIED_BREAKS.contains(pos);
                    }
            );
        }
    }

    private static void setExhaustion(FoodData foodData, float value) {
        try {
            exhaustionField().setFloat(foodData, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to set FoodData exhaustion for the runtime regression", exception);
        }
    }

    private static float getExhaustion(FoodData foodData) {
        try {
            return exhaustionField().getFloat(foodData);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read FoodData exhaustion for the runtime regression", exception);
        }
    }

    private static Field exhaustionField() throws NoSuchFieldException {
        Field field = FoodData.class.getDeclaredField("exhaustionLevel");
        field.setAccessible(true);
        return field;
    }

    private static void assertNear(float expected, float actual, float tolerance, String scenario) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(scenario + " expected exhaustion " + expected + " but got " + actual);
        }
    }

    private static void serverSneakActivationWorksWithoutACustomPacket(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        var options = DiggusMaximusMod.getOptions();
        options.enabled = false;
        options.sneakToExcavate = true;
        options.mineDiag = false;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        context.waitTicks(5);
        breakCreative(context, TARGET, false, true);
        boolean serverOnlyWorked = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.above()).isAir()
        );
        if (!serverOnlyWorked) {
            throw new AssertionError("sneakToExcavate no longer works independently of client packet activation");
        }

        resetConfig();
        prepareArena(context, singleplayer, true);
        options = DiggusMaximusMod.getOptions();
        options.sneakToExcavate = true;
        options.mineDiag = false;
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = true;
        shapes.includeDifBlocks = false;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 65 -4 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 1 65 -3 minecraft:stone");
        context.waitTicks(5);
        sendShapePacket(context, TARGET, ExcavateTypes.shape.HOLE.ordinal());
        breakCreative(context, TARGET, false, true);
        boolean combinedTriggersWorked = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.north()).isAir()
                        && server.overworld().getBlockState(TARGET.east()).isAir()
        );
        if (!combinedTriggersWorked) {
            throw new AssertionError("Shape + sneak no longer executes both legacy excavation paths");
        }

        resetConfig();
        prepareArena(context, singleplayer, true);
        options = DiggusMaximusMod.getOptions();
        options.sneakToExcavate = true;
        options.mineDiag = false;
        options.maxMinedBlocks = 2;
        shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = true;
        DiggusMaximusMod.reloadDerivedConfig();
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 65 -4 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 1 65 -3 minecraft:stone");
        context.waitTicks(5);
        sendShapePacket(context, TARGET, ExcavateTypes.shape.HOLE.ordinal());
        breakCreative(context, TARGET, false, true);
        boolean combinedLimitHeld = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).isAir()
                        && server.overworld().getBlockState(TARGET.north()).isAir()
                        && server.overworld().getBlockState(TARGET.east()).is(Blocks.STONE)
        );
        if (!combinedLimitHeld) {
            throw new AssertionError("Shape + sneak bypassed the shared maxMinedBlocks limit");
        }
    }

    private static void malformedAndStalePacketsDoNotExcavate(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        DiggusMaximusMod.getOptions().mineDiag = false;
        DiggusMaximusMod.getExcavatingShapes().enableShapes = true;
        DiggusMaximusMod.reloadDerivedConfig();

        setTwoStoneBlocks(context, singleplayer);
        sendRawPacket(context, TARGET, Identifier.parse("minecraft:stone"), Direction.SOUTH, 999);
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "out-of-range shape packet");

        setTwoStoneBlocks(context, singleplayer);
        sendRawPacket(context, TARGET, Identifier.parse("minecraft:dirt"), Direction.SOUTH, -1);
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "mismatched block-id packet");

        setTwoStoneBlocks(context, singleplayer);
        sendRawPacket(context, TARGET, Identifier.parse("minecraft:stone"), Direction.SOUTH, -1);
        context.waitTicks(PendingExcavation.ARM_TTL_TICKS + 2);
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "stale packet");

        setTwoStoneBlocks(context, singleplayer);
        BlockPos hostileActionPos = new BlockPos(1_000_000, 65, 1_000_000);
        boolean hostileChunkWasLoaded = singleplayer.getServer().computeOnServer(server ->
                server.overworld().hasChunkAt(hostileActionPos)
        );
        sendRawPacket(context, TARGET, Identifier.parse("minecraft:stone"), Direction.SOUTH, -1);
        context.runOnClient(client -> client.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                hostileActionPos,
                Direction.UP,
                0
        )));
        context.waitTicks(3);
        hostileChunkWasLoaded |= singleplayer.getServer().computeOnServer(server ->
                server.overworld().hasChunkAt(hostileActionPos)
        );
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "unrelated hostile vanilla action packet");
        if (hostileChunkWasLoaded) {
            throw new AssertionError("Hostile vanilla action packet synchronously loaded its remote chunk");
        }

        setTwoStoneBlocks(context, singleplayer);
        DENIED_BREAKS.add(TARGET);
        try {
            sendRawPacket(context, TARGET, Identifier.parse("minecraft:stone"), Direction.SOUTH, -1);
            breakCreative(context, TARGET, false, false);
        } finally {
            DENIED_BREAKS.remove(TARGET);
        }
        boolean failedAttemptKeptBlocks = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET).is(Blocks.STONE)
                        && server.overworld().getBlockState(TARGET.above()).is(Blocks.STONE)
        );
        if (!failedAttemptKeptBlocks) {
            throw new AssertionError("Canceled seed attempt changed a block");
        }
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "request consumed by a canceled seed attempt");

        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:oak_log[axis=y]");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:oak_log[axis=y]");
        context.waitTicks(5);
        sendRawPacket(context, TARGET, Identifier.parse("minecraft:oak_log"), Direction.SOUTH, -1);
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:oak_log[axis=x]");
        context.waitTicks(3);
        breakCreative(context, TARGET, false, false);
        boolean stateMismatchSafe = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET.above()).is(Blocks.OAK_LOG)
        );
        if (!stateMismatchSafe) {
            throw new AssertionError("A pending packet survived a same-id BlockState change");
        }

        setTwoStoneBlocks(context, singleplayer);
        sendRawPacket(
                context,
                new BlockPos(1024, 65, 1024),
                Identifier.parse("minecraft:stone"),
                Direction.SOUTH,
                -1
        );
        breakCreative(context, TARGET, false, false);
        assertExtraBlockRemained(singleplayer, "unloaded/over-distance packet");
    }

    private static void worldBorderStopsTheExcavationFrontier(
            ClientGameTestContext context,
            TestSingleplayerContext singleplayer
    ) {
        resetConfig();
        prepareArena(context, singleplayer, true);
        DiggusMaximusMod.getOptions().mineDiag = false;
        DiggusMaximusMod.reloadDerivedConfig();
        BlockPos inside = new BlockPos(0, 65, -2);
        BlockPos outside = inside.north();
        singleplayer.getServer().runCommand("worldborder center 0 0");
        singleplayer.getServer().runCommand("worldborder set 5");
        singleplayer.getServer().runCommand("setblock 0 65 -2 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        context.waitTicks(5);

        boolean respected;
        try {
            breakCreative(context, inside, true, false);
            respected = singleplayer.getServer().computeOnServer(server ->
                    server.overworld().getBlockState(inside).isAir()
                            && server.overworld().getBlockState(outside).is(Blocks.STONE)
            );
        } finally {
            singleplayer.getServer().runCommand("worldborder set 59999968");
            context.waitTicks(3);
        }
        if (!respected) {
            throw new AssertionError("Excavation crossed the vanilla world border");
        }
    }

    private static void setTwoStoneBlocks(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("setblock 0 65 -3 minecraft:stone");
        singleplayer.getServer().runCommand("setblock 0 66 -3 minecraft:stone");
        context.waitTicks(4);
    }

    private static void assertExtraBlockRemained(TestSingleplayerContext singleplayer, String scenario) {
        boolean remained = singleplayer.getServer().computeOnServer(server ->
                server.overworld().getBlockState(TARGET.above()).is(Blocks.STONE)
        );
        if (!remained) {
            throw new AssertionError("Rejected " + scenario + " still triggered excavation");
        }
    }

    private static void resetConfig() {
        var options = DiggusMaximusMod.getOptions();
        options.enabled = true;
        options.invertActivation = false;
        options.sneakToExcavate = false;
        options.mineDiag = true;
        options.maxMinedBlocks = 40;
        options.maxMineDistance = 10;
        options.autoPickup = false;
        options.requiresTool = false;
        options.dontBreakTool = true;
        options.stopOnToolBreak = true;
        options.toolDurability = true;
        options.playerExhaustion = true;
        options.exhaustionMultiplier = 1.0F;
        options.tools.clear();

        var blacklist = DiggusMaximusMod.getBlackList();
        blacklist.isWhitelist = false;
        blacklist.blacklistedBlocks.clear();
        var grouping = DiggusMaximusMod.getGrouping();
        grouping.customGrouping = false;
        grouping.groups.clear();
        var shapes = DiggusMaximusMod.getExcavatingShapes();
        shapes.enableShapes = false;
        shapes.includeDifBlocks = false;
        shapes.selectedShape = ExcavateTypes.shape.LAYER;
        DiggusMaximusMod.reloadDerivedConfig();
    }

    private static void prepareArena(ClientGameTestContext context, TestSingleplayerContext singleplayer, boolean creative) {
        singleplayer.getServer().runCommand("fill -8 63 -10 8 63 2 minecraft:bedrock");
        singleplayer.getServer().runCommand("fill -8 64 -10 8 70 2 minecraft:air");
        singleplayer.getServer().runCommand("kill @e[type=minecraft:item]");
        singleplayer.getServer().runCommand("kill @e[type=minecraft:experience_orb]");
        singleplayer.getServer().runCommand("clear @p");
        singleplayer.getServer().runCommand("effect clear @p");
        singleplayer.getServer().runCommand("gamemode " + (creative ? "creative" : "survival") + " @p");
        singleplayer.getServer().runCommand("tp @p 0.5 64 0.5 180 0");
        context.waitTicks(8);
    }

    private static void breakCreative(ClientGameTestContext context, BlockPos target, boolean activate, boolean sneak) {
        context.getInput().lookAt(target);
        if (activate) context.getInput().holdKey(DiggusKeyMappings.activation);
        if (sneak) context.getInput().holdShift();
        if (activate || sneak) context.waitTicks(2);
        context.getInput().holdMouseFor(0, 2);
        if (sneak) context.getInput().releaseShift();
        if (activate) context.getInput().releaseKey(DiggusKeyMappings.activation);
        context.waitTicks(12);
    }

    private static void breakSurvival(ClientGameTestContext context, BlockPos target, boolean activate, boolean sneak) {
        context.getInput().lookAt(target);
        if (activate) context.getInput().holdKey(DiggusKeyMappings.activation);
        if (sneak) context.getInput().holdShift();
        boolean started = context.computeOnClient(client -> client.gameMode.startDestroyBlock(target, Direction.SOUTH));
        for (int tick = 0; tick < 160; tick++) {
            context.runOnClient(client -> client.gameMode.continueDestroyBlock(target, Direction.SOUTH));
            context.waitTick();
            if (context.computeOnClient(client -> client.level.getBlockState(target).isAir())) {
                break;
            }
        }
        context.runOnClient(client -> client.gameMode.stopDestroyBlock());
        if (sneak) context.getInput().releaseShift();
        if (activate) context.getInput().releaseKey(DiggusKeyMappings.activation);
        context.waitTicks(12);
        if (!started || !context.computeOnClient(client -> client.level.getBlockState(target).isAir())) {
            throw new AssertionError("Client did not complete survival block breaking at " + target);
        }
    }

    private static void sendShapePacket(ClientGameTestContext context, BlockPos target, int shapeSelection) {
        context.getInput().lookAt(target);
        context.waitTicks(2);
        context.runOnClient(client -> {
            Identifier blockId = BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(target).getBlock());
            ClientPlayNetworking.send(new StartExcavatePacket(
                    target,
                    blockId,
                    Direction.SOUTH.get3DDataValue(),
                    shapeSelection
            ));
        });
        context.waitTicks(2);
    }

    private static void sendRawPacket(
            ClientGameTestContext context,
            BlockPos target,
            Identifier blockId,
            Direction facing,
            int shapeSelection
    ) {
        context.getInput().lookAt(target);
        context.waitTicks(2);
        context.runOnClient(client -> ClientPlayNetworking.send(new StartExcavatePacket(
                target,
                blockId,
                facing == null ? -1 : facing.get3DDataValue(),
                shapeSelection
        )));
        context.waitTicks(2);
    }

    private static void dedicatedServerConnectionExcavates(ClientGameTestContext context) {
        var options = DiggusMaximusMod.getOptions();
        options.enabled = true;
        options.invertActivation = false;
        options.maxMinedBlocks = 40;
        options.maxMineDistance = 10;

        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestServerConnection connection = server.connect()) {
            connection.getClientLevel().waitForChunksDownload();
            server.runCommand("fill -2 63 -4 2 63 2 minecraft:bedrock");
            server.runCommand("fill -1 64 -3 1 66 -3 minecraft:stone");
            server.runCommand("gamemode creative @p");
            server.runCommand("tp @p 0.5 64 0.5 180 0");
            context.waitTicks(10);

            context.getInput().lookAt(new BlockPos(0, 65, -3));
            context.getInput().holdKey(DiggusKeyMappings.activation);
            context.getInput().holdMouseFor(0, 2);
            context.getInput().releaseKey(DiggusKeyMappings.activation);
            context.waitTicks(20);

            int remaining = server.computeOnServer(minecraftServer -> {
                int count = 0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = 64; y <= 66; y++) {
                        if (minecraftServer.overworld().getBlockState(new BlockPos(x, y, -3)).is(Blocks.STONE)) {
                            count++;
                        }
                    }
                }
                return count;
            });
            if (remaining != 0) {
                throw new AssertionError("Dedicated server excavation left " + remaining + " connected blocks");
            }
        }
    }
}
