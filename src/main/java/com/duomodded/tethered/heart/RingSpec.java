package com.duomodded.tethered.heart;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One ring of floating islands spawned when the Heart Tree reaches a stage.
 * Block, biome, feature and entity ids can come from any mod; missing ones are skipped.
 */
public record RingSpec(
        int count,
        int radius,
        int sizeMin,
        int sizeMax,
        int yMin,
        int yMax,
        float bumpiness,
        BlockState surface,
        BlockState filler,
        BlockState core,
        List<Ore> ores,
        @Nullable ResourceLocation biome,
        List<Feature> features,
        List<Spawn> entities,
        @Nullable ResourceLocation loot
) {
    public record Ore(BlockState block, float chance) {}

    public record Feature(ResourceLocation id, int tries) {}

    public record Spawn(ResourceLocation id, int count) {}

    public static RingSpec parse(JsonObject o) {
        List<Ore> ores = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "ores", new JsonArray())) {
            JsonObject ore = e.getAsJsonObject();
            BlockState state = block(GsonHelper.getAsString(ore, "block"), null);
            if (state != null) ores.add(new Ore(state, GsonHelper.getAsFloat(ore, "chance", 0.02f)));
        }
        List<Feature> features = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "features", new JsonArray())) {
            JsonObject f = e.getAsJsonObject();
            features.add(new Feature(ResourceLocation.parse(GsonHelper.getAsString(f, "id")), GsonHelper.getAsInt(f, "tries", 1)));
        }
        List<Spawn> entities = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "entities", new JsonArray())) {
            JsonObject s = e.getAsJsonObject();
            entities.add(new Spawn(ResourceLocation.parse(GsonHelper.getAsString(s, "id")), GsonHelper.getAsInt(s, "count", 1)));
        }
        String biome = GsonHelper.getAsString(o, "biome", "");
        String loot = GsonHelper.getAsString(o, "loot", "");

        int sizeMin = Math.max(3, GsonHelper.getAsInt(o, "size_min", 6));
        int sizeMax = Math.min(14, Math.max(sizeMin, GsonHelper.getAsInt(o, "size_max", 9)));
        return new RingSpec(
                Math.max(1, GsonHelper.getAsInt(o, "count", 3)),
                GsonHelper.getAsInt(o, "radius", 40),
                sizeMin,
                sizeMax,
                GsonHelper.getAsInt(o, "y_min", -6),
                GsonHelper.getAsInt(o, "y_max", 6),
                GsonHelper.getAsFloat(o, "bumpiness", 1.5f),
                block(GsonHelper.getAsString(o, "surface", "minecraft:grass_block"), Blocks.GRASS_BLOCK.defaultBlockState()),
                block(GsonHelper.getAsString(o, "filler", "minecraft:dirt"), Blocks.DIRT.defaultBlockState()),
                block(GsonHelper.getAsString(o, "core", "minecraft:stone"), Blocks.STONE.defaultBlockState()),
                ores,
                biome.isEmpty() ? null : ResourceLocation.parse(biome),
                features,
                entities,
                loot.isEmpty() ? null : ResourceLocation.parse(loot));
    }

    /** A plain grass/dirt/stone island, used for the starting island. */
    public static RingSpec starter() {
        return new RingSpec(1, 0, 6, 6, 0, 0, 0f,
                Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                List.of(new Ore(Blocks.COAL_ORE.defaultBlockState(), 0.04f), new Ore(Blocks.IRON_ORE.defaultBlockState(), 0.015f)),
                null, List.of(), List.of(), null);
    }

    @Nullable
    private static BlockState block(String id, @Nullable BlockState fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return fallback;
        return BuiltInRegistries.BLOCK.getOptional(rl).map(Block::defaultBlockState).orElse(fallback);
    }
}
