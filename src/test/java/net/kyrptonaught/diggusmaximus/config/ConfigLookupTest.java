package net.kyrptonaught.diggusmaximus.config;

import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.Identifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLookupTest {
    @Test
    void tagsCanBeLoadedBeforeRegistryTagsAreBound() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Blacklist blacklist = new Blacklist();
        blacklist.blacklistedBlocks.add("minecraft:bedrock");
        blacklist.blacklistedBlocks.add("#minecraft:impermeable");

        BlockCategory grouping = new BlockCategory();
        grouping.groups.add("minecraft:stone,#minecraft:base_stone_overworld");

        assertDoesNotThrow(blacklist::generateLookup);
        assertDoesNotThrow(grouping::generateLookup);
        assertTrue(blacklist.lookup.contains("minecraft:bedrock"));
    }

    @Test
    void overlappingGroupsKeepLegacyFirstEntryBehavior() {
        BlockCategory grouping = new BlockCategory();
        grouping.groups.add("minecraft:stone,minecraft:deepslate");
        grouping.groups.add("minecraft:deepslate,minecraft:tuff");

        grouping.generateLookup();

        assertEquals(Identifier.withDefaultNamespace("stone"), grouping.lookup.get(Identifier.withDefaultNamespace("deepslate")));
        assertEquals(Identifier.withDefaultNamespace("deepslate"), grouping.lookup.get(Identifier.withDefaultNamespace("tuff")));
    }
}
