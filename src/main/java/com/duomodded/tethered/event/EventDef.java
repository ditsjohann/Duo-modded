package com.duomodded.tethered.event;

import com.duomodded.tethered.age.AgeDef;
import com.duomodded.tethered.util.Ids;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A world event, from data/[namespace]/tethered_events/[id].json. The "type" picks the behaviour
 * (hollow_tide, threadfall, wandering_weaver, crimson_weave, heart_tremor, unraveling_storm),
 * the rest tunes it: when it can happen, which mobs (per Age), rewards and trades.
 */
public record EventDef(String id, String type, String name, int color, String time, int minAge, int weight,
                       String warning, String success, String fail,
                       int waves, int perWave, double perAge, int duration,
                       List<Mob> mobs, int threadsMin, int threadsMax, double favorChance, int cacheRolls,
                       List<Trade> trades, @Nullable AgeDef.Trial champion) {

    public record Mob(EntityType<?> type, int minAge, int weight) {}

    public record Trade(Item cost, int costCount, Item result, int count, int maxUses, int minAge) {}

    static EventDef parse(String id, JsonObject o) {
        String where = "event " + id;
        List<Mob> mobs = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "mobs", new JsonArray())) {
            JsonObject m = e.getAsJsonObject();
            String mod = GsonHelper.getAsString(m, "mod", "");
            if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) continue;
            EntityType<?> type = Ids.entity(GsonHelper.getAsString(m, "entity"), where + " mobs");
            if (type != null) mobs.add(new Mob(type, GsonHelper.getAsInt(m, "min_age", 0), GsonHelper.getAsInt(m, "weight", 10)));
        }
        List<Trade> trades = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "trades", new JsonArray())) {
            JsonObject t = e.getAsJsonObject();
            String mod = GsonHelper.getAsString(t, "mod", "");
            if (!mod.isEmpty() && !ModList.get().isLoaded(mod)) continue;
            Item cost = Ids.item(GsonHelper.getAsString(t, "cost", "tethered:lost_thread"), where + " trades");
            Item result = Ids.item(GsonHelper.getAsString(t, "result"), where + " trades");
            if (cost == null || result == null) continue;
            trades.add(new Trade(cost, GsonHelper.getAsInt(t, "cost_count", 4), result, GsonHelper.getAsInt(t, "count", 1),
                    GsonHelper.getAsInt(t, "max_uses", 3), GsonHelper.getAsInt(t, "min_age", 0)));
        }
        JsonObject rewards = GsonHelper.getAsJsonObject(o, "rewards", new JsonObject());
        JsonArray threads = GsonHelper.getAsJsonArray(rewards, "threads", new JsonArray());
        int tMin = threads.size() > 0 ? threads.get(0).getAsInt() : 0;
        int tMax = threads.size() > 1 ? threads.get(1).getAsInt() : tMin;
        AgeDef.Trial champion = o.has("champion") ? AgeDef.parseTrial(where, GsonHelper.getAsJsonObject(o, "champion")) : null;
        int color;
        try {
            color = Integer.parseInt(GsonHelper.getAsString(o, "color", "#B07AA1").replace("#", ""), 16);
        } catch (NumberFormatException e) {
            color = 0xB07AA1;
        }
        return new EventDef(id, GsonHelper.getAsString(o, "type"), GsonHelper.getAsString(o, "name", id), color,
                GsonHelper.getAsString(o, "time", "any"), GsonHelper.getAsInt(o, "min_age", 0), GsonHelper.getAsInt(o, "weight", 10),
                GsonHelper.getAsString(o, "warning", ""), GsonHelper.getAsString(o, "success", ""), GsonHelper.getAsString(o, "fail", ""),
                GsonHelper.getAsInt(o, "waves", 3), GsonHelper.getAsInt(o, "per_wave", 3), GsonHelper.getAsDouble(o, "per_age", 0.5),
                GsonHelper.getAsInt(o, "duration", 240), mobs, tMin, tMax,
                GsonHelper.getAsDouble(rewards, "favor_chance", 0.0), GsonHelper.getAsInt(rewards, "cache_rolls", 0),
                trades, champion);
    }
}
