package com.duomodded.tethered.quest;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.util.Ids;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Quest chapters, loaded from data/[namespace]/tethered_quests/[chapter].json.
 * A quest with "mod": "create" only shows up when Create is installed. A quest that mentions an
 * item or mob that doesn't exist is hidden (and logged), so a missing mod never blocks the story.
 */
public final class QuestDefs {

    /** A chapter opens when its island is unlocked and/or the world has reached its Age. */
    public record Chapter(String id, String title, @Nullable String island, int age, int order, String icon,
                          String description, List<Quest> quests) {}

    public record Quest(String id, String title, String description, String icon, @Nullable String mod,
                        List<String> requires, List<Task> tasks, List<Reward> rewards, boolean optional, boolean valid) {

        /** Hidden quests don't show and count as done for anything that requires them. */
        public boolean hidden() {
            return !valid || (mod != null && !ModList.get().isLoaded(mod));
        }
    }

    /**
     * Task types: item (bring items; "consume": false = just have them), advancement, kill, visit,
     * attune, age (reach an Age), heart (break the Heartstone N times in total), event (win events
     * of a type), dimension (set foot in a dimension), boons (pick N Boons), together (stand close).
     */
    public record Task(String type, @Nullable Item item, @Nullable TagKey<Item> tag, @Nullable ResourceLocation id,
                       int count, boolean consume, String label) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (item != null) return stack.is(item);
            return tag != null && stack.is(tag);
        }
    }

    /** Reward types: item, shard, xp, unlock (island), ending, favor (a Weaver's Favor each), threads. */
    public record Reward(String type, @Nullable Item item, int count, @Nullable String island) {}

    static Chapter parseChapter(String id, JsonObject o) {
        List<Quest> quests = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "quests", new JsonArray())) {
            quests.add(parseQuest(id, e.getAsJsonObject()));
        }
        String island = GsonHelper.getAsString(o, "island", "");
        return new Chapter(id, GsonHelper.getAsString(o, "title", id), island.isEmpty() ? null : island,
                GsonHelper.getAsInt(o, "age", 0), GsonHelper.getAsInt(o, "order", 0),
                GsonHelper.getAsString(o, "icon", "minecraft:book"), GsonHelper.getAsString(o, "description", ""), quests);
    }

    private static Quest parseQuest(String chapter, JsonObject o) {
        String id = chapter + "/" + GsonHelper.getAsString(o, "id");
        String mod = GsonHelper.getAsString(o, "mod", "");
        boolean modMissing = !mod.isEmpty() && !ModList.get().isLoaded(mod);
        boolean valid = true;

        List<String> requires = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "requires", new JsonArray())) {
            String r = e.getAsString();
            requires.add(r.contains("/") ? r : chapter + "/" + r);
        }

        List<Task> tasks = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "tasks", new JsonArray())) {
            JsonObject t = e.getAsJsonObject();
            String type = GsonHelper.getAsString(t, "type", "item");
            int count = GsonHelper.getAsInt(t, "count", 1);
            String label = GsonHelper.getAsString(t, "label", "");
            Item item = null;
            TagKey<Item> tag = null;
            ResourceLocation rid = null;
            switch (type) {
                case "item" -> {
                    if (t.has("tag")) {
                        tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(GsonHelper.getAsString(t, "tag").replace("#", "")));
                    } else {
                        item = modMissing ? null : Ids.item(GsonHelper.getAsString(t, "item"), "quest " + id);
                        if (item == null) valid = false;
                    }
                }
                case "kill" -> {
                    rid = ResourceLocation.parse(GsonHelper.getAsString(t, "entity"));
                    if (modMissing || Ids.entity(rid.toString(), "quest " + id) == null) valid = false;
                }
                case "advancement" -> rid = ResourceLocation.parse(GsonHelper.getAsString(t, "id"));
                case "visit", "attune" -> rid = ResourceLocation.fromNamespaceAndPath(Tethered.MODID, GsonHelper.getAsString(t, "island"));
                case "tree_stage", "age" -> {
                    type = "age";
                    count = GsonHelper.getAsInt(t, "age", GsonHelper.getAsInt(t, "stage", count));
                }
                case "heart", "boons", "together" -> { }
                case "event" -> rid = ResourceLocation.fromNamespaceAndPath(Tethered.MODID, GsonHelper.getAsString(t, "event"));
                case "dimension" -> rid = ResourceLocation.parse(GsonHelper.getAsString(t, "dimension"));
                default -> {
                    valid = false;
                    Tethered.LOGGER.warn("Quest {}: unknown task type {}", id, type);
                }
            }
            tasks.add(new Task(type, item, tag, rid, count, GsonHelper.getAsBoolean(t, "consume", true), label));
        }

        List<Reward> rewards = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "rewards", new JsonArray())) {
            JsonObject r = e.getAsJsonObject();
            String type = GsonHelper.getAsString(r, "type", "item");
            Item item = null;
            if ("item".equals(type)) {
                item = modMissing ? null : Ids.item(GsonHelper.getAsString(r, "item"), "quest " + id + " reward");
                if (item == null) continue; // reward from a missing mod: just skip it
            }
            String island = GsonHelper.getAsString(r, "island", "");
            rewards.add(new Reward(type, item, GsonHelper.getAsInt(r, "count", 1), island.isEmpty() ? null : island));
        }

        return new Quest(id,
                GsonHelper.getAsString(o, "title", id),
                GsonHelper.getAsString(o, "description", ""),
                GsonHelper.getAsString(o, "icon", "minecraft:book"),
                mod.isEmpty() ? null : mod,
                requires, tasks, rewards,
                GsonHelper.getAsBoolean(o, "optional", false),
                valid);
    }

    private QuestDefs() {}
}
