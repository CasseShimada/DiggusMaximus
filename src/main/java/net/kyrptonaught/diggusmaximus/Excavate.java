package net.kyrptonaught.diggusmaximus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Executes one bounded excavation entirely through the vanilla server block-break path. */
public final class Excavate {
    private final BlockPos startPos;
    private final ServerPlayer player;
    private final Identifier startID;
    private final Item startTool;
    private final ServerLevel world;
    private final Direction facing;
    private final int shapeSelection;
    private final Set<UUID> seedDropIds;
    private final Deque<BlockPos> points = new ArrayDeque<>();
    private final Set<BlockPos> visited = new HashSet<>();
    private int mined;

    public Excavate(
            BlockPos pos,
            Identifier blockID,
            Item startTool,
            ServerPlayer player,
            Direction facing,
            int shapeSelection,
            Set<UUID> seedDropIds
    ) {
        this.startPos = pos.immutable();
        this.player = player;
        this.world = player.level();
        this.startID = ExcavateHelper.configAllowsMining(blockID.toString()) ? blockID : null;
        this.startTool = startTool;
        this.facing = facing;
        this.shapeSelection = shapeSelection;
        this.seedDropIds = seedDropIds;
    }

    public void startExcavate() {
        startExcavate(false);
    }

    /**
     * Runs the packet-selected mode and, when requested, the legacy sneak mode as a second phase.
     * Both phases share one visited set and one configured block limit so a combined trigger cannot
     * bypass maxMinedBlocks.
     */
    public void startExcavate(boolean includeSneakPhase) {
        if (((DiggingPlayerEntity) player).diggus$isExcavating() || player.level() != world) {
            return;
        }

        if (DiggusMaximusMod.getOptions().autoPickup) {
            ExcavateHelper.pickupDrops(world, startPos, player, seedDropIds);
        }
        if (startID == null) {
            if (includeSneakPhase && DiggusMaximusMod.getOptions().autoPickup) {
                ExcavateHelper.pickupDrops(world, startPos, player);
            }
            return;
        }

        points.add(startPos);
        visited.add(startPos);
        mined = 1; // The player's original block counts toward the configured maximum, as before.

        DiggingPlayerEntity diggingPlayer = (DiggingPlayerEntity) player;
        diggingPlayer.diggus$setExcavating(true);
        try {
            while (!points.isEmpty() && mined < ExcavateHelper.maxMined() && player.level() == world) {
                spread(points.removeFirst(), shapeSelection);
            }

            if (includeSneakPhase && player.level() == world) {
                // The old server-only sneak hook ran after the vanilla seed break, so it could
                // collect the seed's new drop after the pre-break packet phase had intentionally
                // ignored it.
                if (DiggusMaximusMod.getOptions().autoPickup) {
                    ExcavateHelper.pickupDrops(world, startPos, player);
                }
                points.addLast(startPos);
                while (!points.isEmpty() && mined < ExcavateHelper.maxMined() && player.level() == world) {
                    spread(points.removeFirst(), -1);
                }
            }
        } finally {
            diggingPlayer.diggus$setExcavating(false);
        }
    }

    private void spread(BlockPos pos, int spreadSelection) {
        for (BlockPos relative : ExcavateTypes.getSpreadType(spreadSelection, facing, startPos, pos)) {
            if (ExcavateHelper.isValidOffset(relative)) {
                excavateAt(pos.offset(relative), spreadSelection);
            }
        }
    }

    private void excavateAt(BlockPos pos, int activeSelection) {
        BlockPos immutable = pos.immutable();
        if (player.level() != world
                || mined >= ExcavateHelper.maxMined()
                || !visited.add(immutable)
                || !world.hasChunkAt(immutable)) {
            return;
        }

        var state = world.getBlockState(immutable);
        if (state.isAir()) {
            return;
        }
        Identifier blockID = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!ExcavateHelper.configAllowsMining(blockID.toString())
                || !ExcavateHelper.isTheSameBlock(startID, blockID, activeSelection)
                || !ExcavateHelper.canMine(player, startTool, world, startPos, immutable, state)) {
            return;
        }

        // Fabric's break events, protection hooks, vanilla restrictions, loot, XP, enchantments and durability
        // all run exactly once inside this method.
        if (player.gameMode.destroyBlock(immutable)
                && player.level() == world
                && !world.getBlockState(immutable).equals(state)) {
            points.addLast(immutable);
            mined++;
            if (DiggusMaximusMod.getOptions().autoPickup) {
                ExcavateHelper.pickupDrops(world, immutable, player);
            }
        }
    }
}
