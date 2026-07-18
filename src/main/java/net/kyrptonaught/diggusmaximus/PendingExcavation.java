package net.kyrptonaught.diggusmaximus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A validated C2S request waiting for the matching vanilla block break to succeed. */
public record PendingExcavation(
        ResourceKey<Level> dimension,
        BlockPos pos,
        Identifier blockID,
        BlockState blockState,
        Direction facing,
        int shapeSelection,
        long gameTime,
        boolean boundToVanillaAction
) {
    public static final int ARM_TTL_TICKS = 5;

    public PendingExcavation(
            ResourceKey<Level> dimension,
            BlockPos pos,
            Identifier blockID,
            BlockState blockState,
            Direction facing,
            int shapeSelection,
            long gameTime
    ) {
        this(dimension, pos, blockID, blockState, facing, shapeSelection, gameTime, false);
    }

    public PendingExcavation {
        pos = pos.immutable();
    }

    public boolean matches(
            ResourceKey<Level> brokenDimension,
            BlockPos brokenPos,
            Identifier brokenBlockID,
            BlockState brokenBlockState,
            long currentGameTime
    ) {
        return sameTarget(brokenDimension, brokenPos, brokenBlockID, brokenBlockState)
                && !isExpired(currentGameTime);
    }

    public boolean sameTarget(
            ResourceKey<Level> targetDimension,
            BlockPos targetPos,
            Identifier targetBlockID,
            BlockState targetBlockState
    ) {
        return dimension.equals(targetDimension)
                && pos.equals(targetPos)
                && blockID.equals(targetBlockID)
                && blockState.equals(targetBlockState);
    }

    public boolean isExpired(long currentGameTime) {
        long age = currentGameTime - gameTime;
        return age < 0 || (!boundToVanillaAction && age > ARM_TTL_TICKS);
    }

    public PendingExcavation bindToVanillaAction() {
        return boundToVanillaAction ? this : new PendingExcavation(
                dimension,
                pos,
                blockID,
                blockState,
                facing,
                shapeSelection,
                gameTime,
                true
        );
    }
}
