package net.kyrptonaught.diggusmaximus.config;

import net.kyrptonaught.diggusmaximus.DiggusMaximusMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BlockCategory {
    public boolean customGrouping = false;
    public List<String> groups = new ArrayList<>();

    public transient HashMap<Identifier, Identifier> lookup = new HashMap<>();

    public void generateLookup() {
        lookup.clear();
        groups.forEach(group -> {
            List<Identifier> expanded = new ArrayList<>();
            for (String rawItem : group.split(",")) {
                String item = rawItem.trim();
                if (item.startsWith("#")) {
                    Identifier tagId = Identifier.tryParse(item.substring(1));
                    if (tagId != null) {
                        TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
                        try {
                            for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag)) {
                                expanded.add(BuiltInRegistries.BLOCK.getKey(holder.value()));
                            }
                        } catch (IllegalStateException exception) {
                            DiggusMaximusMod.LOGGER.debug("Deferring grouping tag {} until registry tags are bound", tag);
                        }
                    }
                } else {
                    Identifier id = Identifier.tryParse(item);
                    if (id != null) {
                        expanded.add(id);
                    }
                }
            }

            if (!expanded.isEmpty()) {
                Identifier canonical = expanded.getFirst();
                // The legacy lookup only mapped later entries. Mapping the canonical entry to
                // itself would overwrite a previous mapping when groups overlap.
                for (int index = 1; index < expanded.size(); index++) {
                    lookup.put(expanded.get(index), canonical);
                }
            }
        });
    }
}
