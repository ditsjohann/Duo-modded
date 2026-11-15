package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Loads Heart Tree stages from datapacks, so modpacks (or KubeJS) can add or change stages. */
public final class StageLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile NavigableMap<Integer, StageDef> stages = new TreeMap<>();

    public StageLoader() {
        super(GSON, "heart_tree_stages");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        TreeMap<Integer, StageDef> loaded = new TreeMap<>();
        map.forEach((id, json) -> {
            try {
                StageDef def = StageDef.parse(json.getAsJsonObject());
                loaded.put(def.stage(), def);
            } catch (Exception e) {
                Tethered.LOGGER.error("Could not load Heart Tree stage {}", id, e);
            }
        });
        stages = loaded;
        Tethered.LOGGER.info("Loaded {} Heart Tree stages", loaded.size());
    }

    @Nullable
    public static StageDef get(int stage) {
        return stages.get(stage);
    }

    public static int maxStage() {
        NavigableMap<Integer, StageDef> s = stages;
        return s.isEmpty() ? 0 : s.lastKey();
    }

    public static String nameOf(int stage) {
        StageDef def = get(stage);
        return def != null ? def.name() : "Sapling";
    }
}
