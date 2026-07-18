package net.kyrptonaught.diggusmaximus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ExcavateHelper {
    private static int maxMined = 40;
    private static double maxDistance = 11;

    private ExcavateHelper() {
    }

    public static void resetMaximums() {
        maxMined = Math.clamp(DiggusMaximusMod.getOptions().maxMinedBlocks, 1, 2048);
        maxDistance = Math.clamp(DiggusMaximusMod.getOptions().maxMineDistance + 1.0, 1.0, 128.0);
    }

    static int maxMined() {
        return maxMined;
    }

    static void pickupDrops(ServerLevel world, BlockPos pos, ServerPlayer player) {
        pickupDrops(world, pos, player, null);
    }

    static void pickupDrops(ServerLevel world, BlockPos pos, ServerPlayer player, Set<UUID> eligibleIds) {
        for (ItemEntity entity : world.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(pos),
                entity -> entity.isAlive() && (eligibleIds == null || eligibleIds.contains(entity.getUUID()))
        )) {
            ItemStack stack = entity.getItem();
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                entity.discard();
            }
        }
    }

    public static Set<UUID> itemEntityIds(ServerLevel world, BlockPos pos) {
        Set<UUID> result = new HashSet<>();
        for (ItemEntity entity : world.getEntitiesOfClass(ItemEntity.class, new AABB(pos), ItemEntity::isAlive)) {
            result.add(entity.getUUID());
        }
        return result;
    }

    static boolean isTheSameBlock(Identifier startID, Identifier newID, int shapeSelection) {
        if (shapeSelection >= 0 && DiggusMaximusMod.getExcavatingShapes().includeDifBlocks) {
            return true;
        }
        if (DiggusMaximusMod.getGrouping().customGrouping) {
            newID = DiggusMaximusMod.getIDFromConfigLookup(newID);
            startID = DiggusMaximusMod.getIDFromConfigLookup(startID);
        }
        return startID.equals(newID);
    }

    static boolean configAllowsMining(String blockID) {
        return DiggusMaximusMod.getBlackList().isWhitelist == DiggusMaximusMod.getBlackList().lookup.contains(blockID);
    }

    static boolean isValidOffset(BlockPos pos) {
        return pos.getX() != 0 || pos.getY() != 0 || pos.getZ() != 0;
    }

    static boolean canMine(ServerPlayer player, Item startTool, ServerLevel world, BlockPos startPos, BlockPos pos, BlockState state) {
        return pos.closerThan(startPos, maxDistance)
                && player.level() == world
                && checkTool(player, startTool, world, pos, state)
                && world.mayInteract(player, pos)
                && world.isInsideBuildHeight(pos)
                && state.getDestroySpeed(world, pos) >= 0.0F;
    }

    private static boolean checkTool(
            ServerPlayer player,
            Item startTool,
            ServerLevel world,
            BlockPos pos,
            BlockState state
    ) {
        if (player.isCreative()) {
            return true;
        }
        ItemStack held = player.getMainHandItem();
        int expectedDamage = expectedMiningDamage(held, world, pos, state);
        if (DiggusMaximusMod.getOptions().dontBreakTool
                && held.isDamageableItem()
                && expectedDamage > 0
                && held.getDamageValue() + expectedDamage >= held.getMaxDamage()) {
            return false;
        }
        if (held.getItem() != startTool
                && (DiggusMaximusMod.getOptions().stopOnToolBreak || DiggusMaximusMod.getOptions().requiresTool)) {
            return false;
        }
        return isTool(held) || !DiggusMaximusMod.getOptions().requiresTool;
    }

    private static int expectedMiningDamage(ItemStack stack, ServerLevel world, BlockPos pos, BlockState state) {
        if (!DiggusMaximusMod.getOptions().toolDurability || state.getDestroySpeed(world, pos) == 0.0F) {
            return 0;
        }
        Tool tool = stack.get(DataComponents.TOOL);
        // Legacy code treated every damageable custom item as costing one durability. Keep that
        // conservative fallback for modded items which override mining without a TOOL component.
        return tool == null ? (stack.isDamageableItem() ? 1 : 0) : Math.max(tool.damagePerBlock(), 0);
    }

    private static boolean isTool(ItemStack stack) {
        return stack.has(DataComponents.MAX_DAMAGE)
                || DiggusMaximusMod.getOptions().tools.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
