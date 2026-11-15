package com.duomodded.tethered.heart;

import com.duomodded.tethered.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the visible tree above the heart block. Each stage makes it taller and wider.
 * The heart block sits under the trunk with its sides left open so it can always be clicked.
 */
public final class TreeShaper {
    public static void grow(ServerLevel level, BlockPos heart, int stage, boolean bloom) {
        RandomSource random = RandomSource.create(heart.asLong() ^ (stage * 31L));
        BlockState log = ModRegistry.HEARTWOOD_LOG.get().defaultBlockState();
        BlockState leaves = ModRegistry.HEART_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);

        int height = 2 + stage * 3;
        for (int y = 1; y <= height; y++) {
            place(level, heart.above(y), log, true);
        }

        // Roots on the diagonals (keeps the four sides of the heart free)
        if (stage >= 3) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    place(level, heart.offset(dx, 0, dz), log, true);
                    place(level, heart.offset(dx, 1, dz), log, true);
                }
            }
        }

        // Branches with small leaf blobs
        if (stage >= 2) {
            int branchY = (int) (height * 0.6);
            int length = 1 + stage;
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    BlockPos end = heart.above(branchY);
                    for (int i = 1; i <= length; i++) {
                        end = heart.offset(dx * i, branchY + i / 2, dz * i);
                        place(level, end, log, true);
                    }
                    blob(level, end, 2, leaves, random);
                }
            }
        }

        // Crown
        blob(level, heart.above(height), 1 + stage, leaves, random);

        if (bloom) scatterPetals(level, heart, random);
    }

    private static void blob(ServerLevel level, BlockPos center, int radius, BlockState leaves, RandomSource random) {
        double ry = Math.max(1.0, radius * 0.65);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = (dx * dx + dz * dz) / (double) (radius * radius) + (dy * dy) / (ry * ry);
                    if (d <= 1.0 + random.nextDouble() * 0.15) {
                        place(level, center.offset(dx, dy, dz), leaves, false);
                    }
                }
            }
        }
    }

    private static void scatterPetals(ServerLevel level, BlockPos heart, RandomSource random) {
        for (int i = 0; i < 120; i++) {
            int dx = random.nextInt(21) - 10;
            int dz = random.nextInt(21) - 10;
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos pos = heart.offset(dx, dy, dz);
                BlockState petals = Blocks.PINK_PETALS.defaultBlockState()
                        .setValue(PinkPetalsBlock.AMOUNT, 1 + random.nextInt(4))
                        .setValue(PinkPetalsBlock.FACING, Direction.from2DDataValue(random.nextInt(4)));
                if (level.getBlockState(pos).isAir() && petals.canSurvive(level, pos)) {
                    level.setBlock(pos, petals, 3);
                    break;
                }
            }
        }
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, boolean isLog) {
        BlockState current = level.getBlockState(pos);
        if (current.is(ModRegistry.HEART_TREE.get())) return;
        boolean free = current.isAir() || current.canBeReplaced() || (isLog && current.is(BlockTags.LEAVES));
        if (free) level.setBlock(pos, state, 3);
    }

    private TreeShaper() {}
}
