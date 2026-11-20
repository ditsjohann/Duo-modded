package com.duomodded.tethered.world;

import com.duomodded.tethered.registry.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The Last Stone: one colossal boulder of rock floating in the void, everything else is empty.
 * Like StoneBlock you start inside it. Layers get richer toward the core: a stone crust with coal
 * and copper, a deepslate mantle with iron, gold, redstone and lapis, and a deep core with
 * diamonds. Winding caves (where monsters spawn), Thread Ore and rare Remnants are spread through it.
 */
public class LastStoneGenerator extends ChunkGenerator {
    public static final MapCodec<LastStoneGenerator> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            Codec.INT.optionalFieldOf("radius", 96).forGetter(g -> g.radius),
            Codec.INT.optionalFieldOf("center_y", 64).forGetter(g -> g.centerY)
    ).apply(inst, LastStoneGenerator::new));

    /** The Hollow Heart (start chamber) is kept free of caves within this distance of the center. */
    public static final int CORE_CLEAR = 14;

    private final int radius;
    private final int centerY;
    private final SimplexNoise shapeNoise = new SimplexNoise(new LegacyRandomSource(0x7E7E0001L));
    private final SimplexNoise caveNoise = new SimplexNoise(new LegacyRandomSource(0x7E7E0002L));
    private final SimplexNoise caveNoise2 = new SimplexNoise(new LegacyRandomSource(0x7E7E0003L));
    private final SimplexNoise rockNoise = new SimplexNoise(new LegacyRandomSource(0x7E7E0004L));

    public LastStoneGenerator(BiomeSource biomeSource, int radius, int centerY) {
        super(biomeSource);
        this.radius = radius;
        this.centerY = centerY;
    }

    public int radius() {
        return radius;
    }

    public int centerY() {
        return centerY;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ shape

    private double dist(int x, int y, int z) {
        double dy = (y - centerY) * 1.12;
        return Math.sqrt((double) x * x + dy * dy + (double) z * z);
    }

    /** Solid before caves are cut. */
    public boolean inShape(int x, int y, int z) {
        double d = dist(x, y, z);
        if (d > radius + 14) return false;
        double edge = radius + shapeNoise.getValue(x * 0.018, y * 0.018, z * 0.018) * 9.0
                + shapeNoise.getValue(x * 0.06, y * 0.06, z * 0.06) * 2.5;
        return d < edge;
    }

    private boolean isCave(int x, int y, int z, double d) {
        if (d < CORE_CLEAR || d > radius - 7) return false;
        double a = caveNoise.getValue(x * 0.032, y * 0.045, z * 0.032);
        double b = caveNoise2.getValue(x * 0.032, y * 0.045, z * 0.032);
        // Two thin noise "sheets" crossing make long winding tunnels
        if (Math.abs(a) < 0.07 && Math.abs(b) < 0.07) return true;
        // Occasional larger caverns in the mantle
        return d > radius * 0.35 && d < radius * 0.8 && caveNoise.getValue(x * 0.012, y * 0.02, z * 0.012) > 0.72;
    }

    private static long hash(int x, int y, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    private static double rand(int x, int y, int z, int salt) {
        return (hash(x + salt * 7919, y - salt * 104729, z + salt * 1299709) >>> 11) * 0x1.0p-53;
    }

    /** The block at a position (including caves, ores and Remnants), or air. */
    public BlockState blockAt(int x, int y, int z) {
        if (!inShape(x, y, z)) return AIR;
        double d = dist(x, y, z);
        if (isCave(x, y, z, d)) return AIR;
        double f = d / radius;
        boolean deep = f < 0.62;

        // Rare Remnants: old caches of the unwoven world
        if (d > CORE_CLEAR + 2 && rand(x, y, z, 1) < 0.00022) return ModRegistry.REMNANT.get().defaultBlockState();

        // Ores come in small clusters: one decision per 2x2x2 cell
        double cell = rand(x >> 1, y >> 1, z >> 1, 2);
        if (rand(x, y, z, 3) < 0.6) {
            BlockState ore = ore(cell, f, deep);
            if (ore != null) return ore;
        }

        double rock = rockNoise.getValue(x * 0.05, y * 0.05, z * 0.05);
        if (f < 0.32) {
            if (rock > 0.55) return Blocks.BLACKSTONE.defaultBlockState();
            if (rock < -0.6) return Blocks.TUFF.defaultBlockState();
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (deep) {
            if (rock > 0.6) return Blocks.TUFF.defaultBlockState();
            if (rock < -0.62) return Blocks.CALCITE.defaultBlockState();
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (rock > 0.55) return Blocks.ANDESITE.defaultBlockState();
        if (rock < -0.55) return Blocks.DIORITE.defaultBlockState();
        if (rock > 0.42 && rock < 0.46) return Blocks.GRANITE.defaultBlockState();
        if (f > 0.97 && rand(x, y, z, 4) < 0.25) return Blocks.COBBLESTONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    private static BlockState ore(double cell, double f, boolean deep) {
        double c = cell;
        if (f >= 0.62) { // crust
            if ((c -= 0.024) < 0) return pick(deep, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            if ((c -= 0.016) < 0) return pick(deep, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            if ((c -= 0.005) < 0) return pick(deep, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            if ((c -= 0.005) < 0) return thread(deep);
            return null;
        }
        if (f >= 0.32) { // mantle
            if ((c -= 0.016) < 0) return pick(deep, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            if ((c -= 0.006) < 0) return pick(deep, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
            if ((c -= 0.009) < 0) return pick(deep, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            if ((c -= 0.005) < 0) return pick(deep, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
            if ((c -= 0.008) < 0) return pick(deep, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            if ((c -= 0.007) < 0) return thread(deep);
            return null;
        }
        // core
        if ((c -= 0.0028) < 0) return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        if ((c -= 0.0009) < 0) return Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
        if ((c -= 0.008) < 0) return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        if ((c -= 0.012) < 0) return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        if ((c -= 0.006) < 0) return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
        if ((c -= 0.009) < 0) return thread(true);
        return null;
    }

    private static BlockState pick(boolean deep, net.minecraft.world.level.block.Block normal, net.minecraft.world.level.block.Block deepslate) {
        return (deep ? deepslate : normal).defaultBlockState();
    }

    private static BlockState thread(boolean deep) {
        return (deep ? ModRegistry.DEEPSLATE_THREAD_ORE.get() : ModRegistry.THREAD_ORE.get()).defaultBlockState();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    // ------------------------------------------------------------------ ChunkGenerator

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        // Nearest point of this chunk to the center: skip chunks that are all void
        int nx = Math.max(x0, Math.min(0, x0 + 15));
        int nz = Math.max(z0, Math.min(0, z0 + 15));
        if ((double) nx * nx + (double) nz * nz > (double) (radius + 16) * (radius + 16)) {
            return CompletableFuture.completedFuture(chunk);
        }
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int minY = Math.max(chunk.getMinBuildHeight(), (int) (centerY - (radius + 16) / 1.12));
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, (int) (centerY + (radius + 16) / 1.12));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                if ((double) x * x + (double) z * z > (double) (radius + 15) * (radius + 15)) continue;
                for (int y = minY; y <= maxY; y++) {
                    BlockState state = blockAt(x, y, z);
                    if (state.isAir()) continue;
                    pos.set(x, y, z);
                    chunk.setBlockState(pos, state, false);
                    oceanFloor.update(lx, y, lz, state);
                    worldSurface.update(lx, y, lz, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    /** Highest solid block of the boulder's shape at x/z (ignores caves), or the bottom of the world. */
    public int topAt(int x, int z) {
        int top = (int) (centerY + (radius + 14) / 1.12);
        int bottom = (int) (centerY - (radius + 14) / 1.12);
        for (int y = top; y >= bottom; y--) {
            if (inShape(x, y, z)) return y;
        }
        return getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = topAt(x, z);
        return top <= getMinY() ? level.getMinBuildHeight() : top + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = height.getMinBuildHeight() + i;
            states[i] = inShape(x, y, z) ? Blocks.STONE.defaultBlockState() : AIR;
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Last Stone: " + (int) dist(pos.getX(), pos.getY(), pos.getZ()) + " / " + radius + " from the core");
    }

    /** Small helper for code that wants a random source tied to a position. */
    public static RandomSource randomAt(BlockPos pos) {
        return RandomSource.create(hash(pos.getX(), pos.getY(), pos.getZ()));
    }
}
