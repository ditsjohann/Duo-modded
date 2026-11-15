package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds floating islands: a rounded blob of blocks with ores, then trees, animals, loot and a biome. */
public final class IslandBuilder {

    /** Spawns one ring of islands around the tree. Each stage and ring is rotated so rings don't line up. */
    public static void buildRing(ServerLevel level, BlockPos tree, RingSpec ring, int stage, int ringIndex, RandomSource random) {
        double base = Math.toRadians(stage * 37 + ringIndex * 23);
        for (int i = 0; i < ring.count(); i++) {
            double angle = base + i * (Math.PI * 2 / ring.count()) + (random.nextDouble() - 0.5) * 0.4;
            double dist = ring.radius() + (random.nextDouble() - 0.5) * 6;
            int size = ring.sizeMin() + random.nextInt(Math.max(1, ring.sizeMax() - ring.sizeMin() + 1));
            int dy = ring.yMin() + random.nextInt(Math.max(1, ring.yMax() - ring.yMin() + 1));
            BlockPos center = tree.offset((int) Math.round(Math.cos(angle) * dist), dy - 1, (int) Math.round(Math.sin(angle) * dist));
            buildIsland(level, center, ring, size, random);
        }
    }

    /**
     * Builds one island whose top surface is around center.y.
     * @return the top block of every column, for placing things on.
     */
    public static List<BlockPos> buildIsland(ServerLevel level, BlockPos center, RingSpec spec, int radius, RandomSource random) {
        SimplexNoise noise = new SimplexNoise(random);
        int depth = Math.max(3, (int) (radius * 0.9));
        List<BlockPos> tops = new ArrayList<>();
        List<BlockPos> inner = new ArrayList<>();

        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double n = noise.getValue(dx * 0.12, dz * 0.12);
                double edge = radius * (0.85 + 0.2 * n);
                double d = Math.sqrt(dx * dx + dz * dz) / edge;
                if (d > 1.0) continue;

                int top = center.getY() + (int) Math.round(noise.getValue(dx * 0.2 + 100, dz * 0.2) * spec.bumpiness() * (1 - d));
                double shape = Math.pow(1 - d * d, 0.7) * (0.75 + 0.25 * (noise.getValue(dx * 0.3 + 50, dz * 0.3) + 1));
                int columnDepth = Math.max(1, (int) Math.round(depth * shape));

                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                for (int y = top - columnDepth; y <= top; y++) {
                    BlockState state;
                    if (y == top) state = spec.surface();
                    else if (y > top - 3) state = spec.filler();
                    else state = rollOre(spec, random);
                    level.setBlock(new BlockPos(x, y, z), state, 2);
                }
                BlockPos topPos = new BlockPos(x, top, z);
                tops.add(topPos);
                if (d < 0.7) inner.add(topPos);
            }
        }
        if (inner.isEmpty()) inner = tops;
        if (inner.isEmpty()) return tops;

        placeFeatures(level, spec, inner, random);
        spawnEntities(level, spec, inner, random);
        placeLoot(level, spec, inner, random);
        fillBiome(level, spec, center, radius, depth);
        return tops;
    }

    private static BlockState rollOre(RingSpec spec, RandomSource random) {
        for (RingSpec.Ore ore : spec.ores()) {
            if (random.nextFloat() < ore.chance()) return ore.block();
        }
        return spec.core();
    }

    private static void placeFeatures(ServerLevel level, RingSpec spec, List<BlockPos> spots, RandomSource random) {
        for (RingSpec.Feature feature : spec.features()) {
            ConfiguredFeature<?, ?> configured = level.registryAccess()
                    .registryOrThrow(Registries.CONFIGURED_FEATURE).get(feature.id());
            if (configured == null) {
                Tethered.LOGGER.warn("Unknown configured feature {} (mod not installed?)", feature.id());
                continue;
            }
            for (int i = 0; i < feature.tries(); i++) {
                BlockPos spot = spots.get(random.nextInt(spots.size())).above();
                try {
                    configured.place(level, level.getChunkSource().getGenerator(), random, spot);
                } catch (Exception e) {
                    Tethered.LOGGER.warn("Feature {} failed at {}", feature.id(), spot, e);
                }
            }
        }
    }

    private static void spawnEntities(ServerLevel level, RingSpec spec, List<BlockPos> spots, RandomSource random) {
        for (RingSpec.Spawn spawn : spec.entities()) {
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(spawn.id());
            if (type.isEmpty()) continue;
            for (int i = 0; i < spawn.count(); i++) {
                BlockPos spot = spots.get(random.nextInt(spots.size())).above();
                Entity entity = type.get().spawn(level, spot, MobSpawnType.EVENT);
                if (entity instanceof Mob mob) mob.setPersistenceRequired();
            }
        }
    }

    private static void placeLoot(ServerLevel level, RingSpec spec, List<BlockPos> spots, RandomSource random) {
        if (spec.loot() == null) return;
        BlockPos spot = spots.get(random.nextInt(spots.size())).above();
        level.setBlock(spot, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(spot) instanceof ChestBlockEntity chest) {
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, spec.loot()), random.nextLong());
        }
    }

    /** Uses the vanilla /fillbiome command in small boxes (it has a volume limit). */
    private static void fillBiome(ServerLevel level, RingSpec spec, BlockPos center, int radius, int depth) {
        if (spec.biome() == null) return;
        if (!level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(spec.biome())) {
            Tethered.LOGGER.warn("Unknown biome {} (mod not installed?)", spec.biome());
            return;
        }
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level).withPermission(4).withSuppressedOutput();
        int x0 = center.getX() - radius - 3, x1 = center.getX() + radius + 3;
        int z0 = center.getZ() - radius - 3, z1 = center.getZ() + radius + 3;
        int y0 = center.getY() - depth - 4, y1 = center.getY() + 28;
        for (int x = x0; x <= x1; x += 16) {
            for (int z = z0; z <= z1; z += 16) {
                String cmd = String.format("fillbiome %d %d %d %d %d %d %s",
                        x, y0, z, Math.min(x + 15, x1), y1, Math.min(z + 15, z1), spec.biome());
                server.getCommands().performPrefixedCommand(source, cmd);
            }
        }
    }

    private IslandBuilder() {}
}
