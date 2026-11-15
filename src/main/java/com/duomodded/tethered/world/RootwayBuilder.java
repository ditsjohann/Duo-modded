package com.duomodded.tethered.world;

import com.duomodded.tethered.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Rootway: a living root bridge the Heart Tree grows from home out to an island.
 * Heartwood deck, cherry fence rails, lanterns, and roots hanging underneath.
 * Only fills air, so it never damages anything already built.
 */
public final class RootwayBuilder {
    public static void build(ServerLevel level, BlockPos tree, int homeRadius, IslandDef island) {
        double dx = island.center().getX() - tree.getX();
        double dz = island.center().getZ() - tree.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1) return;
        double ux = dx / length;
        double uz = dz / length;
        double px = -uz; // perpendicular, for width
        double pz = ux;

        double start = homeRadius;
        double end = length - island.radius() * 0.6;
        if (end <= start) return;

        Direction.Axis axis = Math.abs(ux) > Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState deck = ModRegistry.HEARTWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        BlockState rail = Blocks.CHERRY_FENCE.defaultBlockState();
        BlockState leaves = ModRegistry.HEART_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState root = ModRegistry.HEARTWOOD_LOG.get().defaultBlockState();

        int startY = tree.getY() - 1;
        int endY = island.center().getY();
        int steps = (int) Math.ceil(end - start);
        for (int i = 0; i <= steps; i++) {
            double d = start + i;
            double t = (d - start) / Math.max(1, end - start);
            double cx = tree.getX() + 0.5 + ux * d;
            double cz = tree.getZ() + 0.5 + uz * d;
            int y = (int) Math.round(Mth.lerp(t, startY, endY));

            for (int w = -1; w <= 1; w++) {
                fill(level, at(cx + px * w, y, cz + pz * w), deck);
            }
            BlockPos left = at(cx + px * 2, y + 1, cz + pz * 2);
            BlockPos right = at(cx - px * 2, y + 1, cz - pz * 2);
            fill(level, left.below(), deck);
            fill(level, right.below(), deck);
            fill(level, left, rail);
            fill(level, right, rail);

            if (i % 12 == 0) {
                fill(level, left.above(), Blocks.LANTERN.defaultBlockState());
                fill(level, right.above(), Blocks.LANTERN.defaultBlockState());
            }
            if (i % 7 == 3) {
                fill(level, (i % 14 == 3 ? left : right).above(), leaves);
            }
            if (i % 24 == 12) {
                BlockPos under = at(cx, y - 1, cz);
                for (int k = 0; k < 4; k++) fill(level, under.below(k), root);
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oz = -1; oz <= 1; oz++) {
                        fill(level, under.offset(ox, -4, oz), leaves);
                    }
                }
            }
        }
    }

    private static BlockPos at(double x, int y, double z) {
        return new BlockPos(Mth.floor(x), y, Mth.floor(z));
    }

    private static void fill(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, 3);
    }

    private RootwayBuilder() {}
}
