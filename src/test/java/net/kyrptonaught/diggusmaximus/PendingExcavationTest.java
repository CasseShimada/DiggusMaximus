package net.kyrptonaught.diggusmaximus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingExcavationTest {
    @Test
    void requestMustMatchPositionBlockAndFreshServerTick() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BlockPos pos = new BlockPos(1, 2, 3);
        Identifier log = Identifier.withDefaultNamespace("oak_log");
        var verticalLog = Blocks.OAK_LOG.defaultBlockState();
        var horizontalLog = verticalLog.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        PendingExcavation pending = new PendingExcavation(
                Level.OVERWORLD,
                pos,
                log,
                verticalLog,
                Direction.UP,
                2,
                100
        );

        assertTrue(pending.matches(Level.OVERWORLD, pos, log, verticalLog, 100));
        assertTrue(pending.matches(Level.OVERWORLD, pos, log, verticalLog, 100 + PendingExcavation.ARM_TTL_TICKS));
        assertFalse(pending.matches(Level.NETHER, pos, log, verticalLog, 101));
        assertFalse(pending.matches(Level.OVERWORLD, pos.offset(1, 0, 0), log, verticalLog, 101));
        assertFalse(pending.matches(Level.OVERWORLD, pos, Identifier.withDefaultNamespace("dirt"), verticalLog, 101));
        assertFalse(pending.matches(Level.OVERWORLD, pos, log, horizontalLog, 101));
        assertFalse(pending.matches(Level.OVERWORLD, pos, log, verticalLog, 99));
        assertFalse(pending.matches(Level.OVERWORLD, pos, log, verticalLog, 101 + PendingExcavation.ARM_TTL_TICKS));
        PendingExcavation bound = pending.bindToVanillaAction();
        assertTrue(bound.matches(Level.OVERWORLD, pos, log, verticalLog, 10_000));
    }
}
