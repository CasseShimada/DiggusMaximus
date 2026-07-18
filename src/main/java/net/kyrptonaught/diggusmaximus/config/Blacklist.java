package net.kyrptonaught.diggusmaximus.config;

import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.HashSet;

public class Blacklist {
    public boolean isWhitelist = false;

    public HashSet<String> blacklistedBlocks = new HashSet<>();

    public transient HashSet<String> lookup = new HashSet<>();

    public void generateLookup() {
        lookup.clear();
        blacklistedBlocks.forEach(rawEntry -> {
            String entry = rawEntry.trim();
            if (entry.startsWith("#")) {
                Identifier id = Identifier.tryParse(entry.substring(1));
                if (id != null) {
                    TagKey<net.minecraft.world.level.block.Block> tag = TagKey.create(Registries.BLOCK, id);
                    try {
                        for (Holder<net.minecraft.world.level.block.Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                            lookup.add(BuiltInRegistries.BLOCK.getKey(holder.value()).toString());
                        }
                    } catch (IllegalStateException exception) {
                        DiggusMaximusMod.LOGGER.debug("Deferring blacklist tag {} until registry tags are bound", tag);
                    }
                }
            } else {
                Identifier id = Identifier.tryParse(entry);
                if (id != null) {
                    lookup.add(id.toString());
                }
            }
        });
    }
}
