package com.duomodded.tethered.quest;

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

/** Loads quest chapters from datapacks. The chapter id is the file name. */
public final class QuestLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<QuestDefs.Chapter> chapters = List.of();
    private static volatile Map<String, QuestDefs.Quest> quests = Map.of();

    public QuestLoader() {
        super(GSON, "tethered_quests");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        List<QuestDefs.Chapter> loaded = new ArrayList<>();
        map.forEach((id, json) -> {
            try {
                loaded.add(QuestDefs.parseChapter(id.getPath(), json.getAsJsonObject()));
            } catch (Exception e) {
                Tethered.LOGGER.error("Could not load quest chapter {}", id, e);
            }
        });
        loaded.sort(Comparator.comparingInt(QuestDefs.Chapter::order));
        Map<String, QuestDefs.Quest> byId = new LinkedHashMap<>();
        int hidden = 0;
        for (QuestDefs.Chapter c : loaded) {
            for (QuestDefs.Quest q : c.quests()) {
                byId.put(q.id(), q);
                if (q.hidden()) hidden++;
            }
        }
        chapters = List.copyOf(loaded);
        quests = byId;
        Tethered.LOGGER.info("Loaded {} quest chapters with {} quests ({} hidden because a mod is missing)",
                loaded.size(), byId.size(), hidden);
        QuestManager.markDirty();
    }

    public static List<QuestDefs.Chapter> chapters() {
        return chapters;
    }

    @Nullable
    public static QuestDefs.Quest quest(String id) {
        return quests.get(id);
    }

    public static Map<String, QuestDefs.Quest> all() {
        return quests;
    }
}
