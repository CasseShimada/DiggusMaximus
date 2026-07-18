package net.kyrptonaught.diggusmaximus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcavateTypesTest {
    @Test
    void ordinaryNeighborSetsRemainSixAndTwentySixEffectiveOffsets() {
        assertEquals(6, new HashSet<>(ExcavateTypes.standard).size());
        HashSet<BlockPos> diagonal = new HashSet<>(ExcavateTypes.standardDiag);
        assertEquals(27, diagonal.size());
        assertTrue(diagonal.contains(BlockPos.ZERO));
    }

    @Test
    void allSevenShapesKeepTheirDirectionalGeometry() {
        BlockPos start = new BlockPos(10, 64, 10);
        Set<BlockPos> horizontal = Set.of(
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        );
        for (Direction facing : Direction.values()) {
            assertEquals(horizontal, offsets(ExcavateTypes.shape.HORIZONTAL_LAYER, facing, start));

            Set<BlockPos> expectedLayer = switch (facing.getAxis()) {
                case Y -> horizontal;
                case Z -> Set.of(
                        new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                        new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0)
                );
                case X -> Set.of(
                        new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                        new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
                );
            };
            assertEquals(expectedLayer, offsets(ExcavateTypes.shape.LAYER, facing, start));

            BlockPos forward = BlockPos.ZERO.relative(facing.getOpposite());
            assertEquals(Set.of(forward), offsets(ExcavateTypes.shape.HOLE, facing, start));
            assertEquals(Set.of(new BlockPos(0, -1, 0)), offsets(ExcavateTypes.shape.ONExTWO, facing, start));
            Set<BlockPos> oneByTwoTunnel = new HashSet<>();
            oneByTwoTunnel.add(forward);
            oneByTwoTunnel.add(new BlockPos(0, -1, 0));
            assertEquals(oneByTwoTunnel, offsets(ExcavateTypes.shape.ONExTWO_TUNNEL, facing, start));

            Set<BlockPos> plane = perpendicularPlane(facing.getAxis());
            assertEquals(plane, offsets(ExcavateTypes.shape.THREExTHREE, facing, start));
            Set<BlockPos> tunnel = new HashSet<>(plane);
            tunnel.add(forward);
            plane.forEach(offset -> tunnel.add(offset.offset(forward)));
            tunnel.add(forward.offset(forward));
            assertEquals(tunnel, offsets(ExcavateTypes.shape.THREExTHREE_TUNNEL, facing, start));
        }
    }

    private static Set<BlockPos> offsets(ExcavateTypes.shape shape, Direction facing, BlockPos start) {
        return new HashSet<>(ExcavateTypes.getSpreadType(shape.ordinal(), facing, start, start));
    }

    private static Set<BlockPos> perpendicularPlane(Direction.Axis axis) {
        Set<BlockPos> result = new HashSet<>();
        for (int first = -1; first <= 1; first++) {
            for (int second = -1; second <= 1; second++) {
                if (first == 0 && second == 0) continue;
                result.add(switch (axis) {
                    case X -> new BlockPos(0, first, second);
                    case Y -> new BlockPos(first, 0, second);
                    case Z -> new BlockPos(first, second, 0);
                });
            }
        }
        return result;
    }

    @Test
    void malformedShapeInputFallsBackWithoutIndexingTheEnum() {
        assertEquals(ExcavateTypes.standard, ExcavateTypes.getSpreadType(999, Direction.NORTH, BlockPos.ZERO, BlockPos.ZERO));
        assertEquals(ExcavateTypes.standard, ExcavateTypes.getSpreadType(0, null, BlockPos.ZERO, BlockPos.ZERO));
    }
}
