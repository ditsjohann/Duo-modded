package com.duomodded.tethered.age;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.util.Ids;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Loads the Ages from data/[namespace]/tethered_ages/*.json. */
public class AgeLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile TreeMap<Integer, AgeDef> ages = new TreeMap<>();

    public AgeLoader() {
        super(GSON, "tethered_ages");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Ids.clear("age ");
        TreeMap<Integer, AgeDef> loaded = new TreeMap<>();
        files.forEach((id, json) -> {
            try {
                AgeDef def = AgeDef.parse(id.getPath(), json.getAsJsonObject());
                loaded.put(def.order(), def);
            } catch (Exception e) {
                Ids.problem("age " + id + ": could not read file: " + e.getMessage());
            }
        });
        // Carry part of the previous Age's blocks over, so older materials don't vanish completely
        AgeDef previous = null;
        for (Integer key : new ArrayList<>(loaded.keySet())) {
            AgeDef def = loaded.get(key);
            if (previous != null && def.keepPrevious() > 0) {
                java.util.List<AgeDef.Weighted<Block>> merged = new ArrayList<>(def.blocks());
                for (AgeDef.Weighted<Block> w : previous.blocks()) {
                    merged.add(new AgeDef.Weighted<>(w.value(), Math.max(1, (int) Math.round(w.weight() * def.keepPrevious()))));
                }
                def = def.withBlocks(merged);
                loaded.put(key, def);
            }
            previous = def;
        }
        ages = loaded;
        Tethered.LOGGER.info("Loaded {} Ages", loaded.size());
    }

    @Nullable
    public static AgeDef get(int order) {
        return ages.get(order);
    }

    public static int maxAge() {
        return ages.isEmpty() ? 0 : ages.lastKey();
    }

    public static Collection<AgeDef> all() {
        return ages.values();
    }

    public static String nameOf(int order) {
        AgeDef def = ages.get(order);
        return def == null ? "Age " + order : def.name();
    }
}
