package net.kyrptonaught.diggusmaximus.config.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.kyrptonaught.diggusmaximus.client.DiggusConfigScreen;

/** Optional adapter; the screen and all configuration logic are owned by Diggus Maximus. */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DiggusConfigScreen::new;
    }
}
