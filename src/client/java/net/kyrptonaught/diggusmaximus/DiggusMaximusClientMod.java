package net.kyrptonaught.diggusmaximus;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.kyrptonaught.diggusmaximus.client.DiggusConfigScreen;
import net.kyrptonaught.diggusmaximus.client.DiggusKeyMappings;
import net.minecraft.network.chat.Component;

public class DiggusMaximusClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DiggusKeyMappings.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (DiggusKeyMappings.syncToLegacyConfig()) {
                DiggusMaximusMod.configManager.save();
            }
            if (client.player != null && client.gui.screen() == null && DiggusKeyMappings.openConfig.consumeClick()) {
                client.setScreenAndShow(new DiggusConfigScreen(null));
                return;
            }
            if (client.player != null
                    && DiggusMaximusMod.getExcavatingShapes().enableShapes
                    && DiggusKeyMappings.cycle.consumeClick()) {
                int selected = DiggusMaximusMod.getExcavatingShapes().selectedShape.ordinal();
                selected += client.player.isShiftKeyDown() ? -1 : 1;
                selected = Math.floorMod(selected, ExcavateTypes.shape.values().length);
                DiggusMaximusMod.getExcavatingShapes().selectedShape = ExcavateTypes.shape.values()[selected];
                client.player.sendOverlayMessage(Component.translatable("diggusmaximus.shape." + ExcavateTypes.shape.values()[selected]));
                DiggusMaximusMod.configManager.save();
            }
        });
    }
}
