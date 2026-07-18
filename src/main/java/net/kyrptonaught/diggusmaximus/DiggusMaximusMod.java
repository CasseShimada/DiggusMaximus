package net.kyrptonaught.diggusmaximus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.kyrptonaught.diggusmaximus.config.Blacklist;
import net.kyrptonaught.diggusmaximus.config.BlockCategory;
import net.kyrptonaught.diggusmaximus.config.ConfigManager;
import net.kyrptonaught.diggusmaximus.config.ConfigOptions;
import net.kyrptonaught.diggusmaximus.config.ExcavatingShapes;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiggusMaximusMod implements ModInitializer {
    public static final String MOD_ID = "diggusmaximus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ConfigManager configManager = new ConfigManager(MOD_ID);

    @Override
    public void onInitialize() {
        configManager.load();
        ExcavateHelper.resetMaximums();

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> reloadDerivedConfig());
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(player -> {
            DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
            PendingExcavation pending = diggingPlayer.diggus$getPendingExcavation();
            if (pending == null) {
                return;
            }
            var level = player.level();
            if (pending.isExpired(level.getGameTime())
                    || !pending.dimension().equals(level.dimension())
                    || !level.hasChunkAt(pending.pos())
                    || !pending.blockState().equals(level.getBlockState(pending.pos()))) {
                diggingPlayer.diggus$setPendingExcavation(null);
            }
        }));
        StartExcavatePacket.register();
        LOGGER.info("Diggus Maximus initialized for Minecraft 26.2");
    }

    public static ConfigOptions getOptions() {
        return configManager.options();
    }

    public static Blacklist getBlackList() {
        return configManager.blacklist();
    }

    public static BlockCategory getGrouping() {
        return configManager.grouping();
    }

    public static ExcavatingShapes getExcavatingShapes() {
        return configManager.shapes();
    }

    public static Identifier getIDFromConfigLookup(Identifier blockID) {
        return getGrouping().lookup.getOrDefault(blockID, blockID);
    }

    public static void saveAndReloadDerivedConfig() {
        configManager.save();
        reloadDerivedConfig();
    }

    public static void reloadDerivedConfig() {
        getGrouping().generateLookup();
        getBlackList().generateLookup();
        ExcavateHelper.resetMaximums();
    }
}
