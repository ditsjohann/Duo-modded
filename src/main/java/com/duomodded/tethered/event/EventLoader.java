package com.duomodded.tethered.event;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.util.Ids;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Loads world events from data/[namespace]/tethered_events/*.json. */
public class EventLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, EventDef> events = new TreeMap<>();

    public EventLoader() {
        super(GSON, "tethered_events");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Ids.clear("event ");
        Map<String, EventDef> loaded = new TreeMap<>();
        files.forEach((id, json) -> {
            try {
                loaded.put(id.getPath(), EventDef.parse(id.getPath(), json.getAsJsonObject()));
            } catch (Exception e) {
                Ids.problem("event " + id + ": could not read file: " + e.getMessage());
            }
        });
        events = loaded;
        Tethered.LOGGER.info("Loaded {} world events", loaded.size());
    }

    public static Collection<EventDef> all() {
        return events.values();
    }

    @Nullable
    public static EventDef get(String id) {
        return events.get(id);
    }
}
