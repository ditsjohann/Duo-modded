package com.duomodded.tethered.world;

import com.duomodded.tethered.Tethered;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads the world map's islands from datapacks. The island id is the file name. */
public final class IslandLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, IslandDef> islands = new LinkedHashMap<>();

    public IslandLoader() {
        super(GSON, "tethered_islands");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        List<IslandDef> list = new ArrayList<>();
        map.forEach((id, json) -> {
            try {
                list.add(IslandDef.parse(id.getPath(), json.getAsJsonObject()));
            } catch (Exception e) {
                Tethered.LOGGER.error("Could not load island {}", id, e);
            }
        });
        list.sort(Comparator.comparingInt(IslandDef::order));
        Map<String, IslandDef> loaded = new LinkedHashMap<>();
        for (IslandDef def : list) loaded.put(def.id(), def);
        islands = loaded;
        Tethered.LOGGER.info("Loaded {} islands", loaded.size());
    }

    public static Map<String, IslandDef> all() {
        return islands;
    }

    @Nullable
    public static IslandDef get(String id) {
        return islands.get(id);
    }
}
