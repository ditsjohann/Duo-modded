package com.duomodded.tethered.age;

import com.duomodded.tethered.util.Ids;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One Age of the story, from data/[namespace]/tethered_ages/[name].json. The Age's "order" is its number
 * (0 = the start). What the Heartstone gives while you're in this Age is listed under blocks / mobs /
 * cache. "awaken" says what it takes to reach this Age from the one before: memories (breaks), an
 * offering and a Trial boss.
 */
public record AgeDef(
        int order, String name, String title, int color, String icon,
        List<String> lore, String weaver,
        int breaks, List<Offer> offering, @Nullable Trial trial,
        List<Weighted<Block>> blocks, List<Block> opening,
        double mobChance, List<Weighted<EntityType<?>>> mobs,
        double cacheChance, int cacheRolls, List<Loot> cache,
        List<String> unlocks, double keepPrevious) {

    public record Weighted<T>(T value, int weight) {}

    public record Offer(@Nullable Item item, @Nullable TagKey<Item> tag, int count, String label) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            if (item != null) return stack.is(item);
            return tag != null && stack.is(tag);
        }

        public Component displayName() {
            if (!label.isEmpty()) return Component.literal(label);
            if (item != null) return item.getDescription();
            return Component.literal("#" + (tag == null ? "?" : tag.location().getPath()));
        }

        public String id() {
            if (item != null) return BuiltInRegistries.ITEM.getKey(item).toString();
            return tag == null ? "" : "#" + tag.location();
        }
    }

    public record Loot(Item item, int min, int max, int weight) {
        public ItemStack roll(RandomSource random) {
            int n = min + (max > min ? random.nextInt(max - min + 1) : 0);
            return new ItemStack(item, Math.max(1, Math.min(n, item.getDefaultMaxStackSize())));
        }
    }

    public record Minion(EntityType<?> entity, int every, int count, int max) {}

    public record Trial(String name, EntityType<?> entity, double health, double damage, double scale, double armor,
                        double speed, List<Minion> minions, List<Holder<MobEffect>> effects,
                        Map<EquipmentSlot, Item> equipment, String intro) {}

    public static <T> T pick(List<Weighted<T>> list, RandomSource random) {
        int total = 0;
        for (Weighted<T> w : list) total += w.weight();
        int r = random.nextInt(Math.max(1, total));
        for (Weighted<T> w : list) {
            r -= w.weight();
            if (r < 0) return w.value();
        }
        return list.get(0).value();
    }

    public static Loot pickLoot(List<Loot> list, RandomSource random) {
        int total = 0;
        for (Loot l : list) total += l.weight();
        int r = random.nextInt(Math.max(1, total));
        for (Loot l : list) {
            r -= l.weight();
            if (r < 0) return l;
        }
        return list.get(0);
    }

    private static boolean modOk(JsonObject o) {
        String mod = GsonHelper.getAsString(o, "mod", "");
        return mod.isEmpty() || ModList.get().isLoaded(mod);
    }

    static AgeDef parse(String file, JsonObject o) {
        String where = "age " + file;
        int order = GsonHelper.getAsInt(o, "order");
        List<String> lore = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "lore", new JsonArray())) lore.add(e.getAsString());

        // ---- awaken: what it takes to reach this Age
        JsonObject awaken = GsonHelper.getAsJsonObject(o, "awaken", new JsonObject());
        int breaks = GsonHelper.getAsInt(awaken, "breaks", 0);
        List<Offer> offering = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(awaken, "offering", new JsonArray())) {
            JsonObject r = e.getAsJsonObject();
            if (!modOk(r)) continue;
            int count = GsonHelper.getAsInt(r, "count", 1);
            String label = GsonHelper.getAsString(r, "label", "");
            if (r.has("tag")) {
                ResourceLocation tag = ResourceLocation.parse(GsonHelper.getAsString(r, "tag").replace("#", ""));
                offering.add(new Offer(null, TagKey.create(Registries.ITEM, tag), count, label));
            } else {
                Item item = Ids.item(GsonHelper.getAsString(r, "item"), where + " offering");
                if (item != null) offering.add(new Offer(item, null, count, label));
            }
        }
        Trial trial = awaken.has("trial") ? parseTrial(where, GsonHelper.getAsJsonObject(awaken, "trial")) : null;

        // ---- what the Heartstone gives in this Age
        List<Weighted<Block>> blocks = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "blocks", new JsonArray())) {
            JsonObject b = e.getAsJsonObject();
            if (!modOk(b)) continue;
            Block block = Ids.block(GsonHelper.getAsString(b, "block"), where + " blocks");
            if (block != null) blocks.add(new Weighted<>(block, GsonHelper.getAsInt(b, "weight", 10)));
        }
        List<Block> opening = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "opening", new JsonArray())) {
            Block block = Ids.block(e.getAsString(), where + " opening");
            if (block != null) opening.add(block);
        }

        JsonObject mobsObj = GsonHelper.getAsJsonObject(o, "mobs", new JsonObject());
        List<Weighted<EntityType<?>>> mobs = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(mobsObj, "list", new JsonArray())) {
            JsonObject m = e.getAsJsonObject();
            if (!modOk(m)) continue;
            EntityType<?> type = Ids.entity(GsonHelper.getAsString(m, "entity"), where + " mobs");
            if (type != null) mobs.add(new Weighted<>(type, GsonHelper.getAsInt(m, "weight", 10)));
        }

        JsonObject cacheObj = GsonHelper.getAsJsonObject(o, "cache", new JsonObject());
        List<Loot> cache = parseLoot(GsonHelper.getAsJsonArray(cacheObj, "items", new JsonArray()), where + " cache");

        List<String> unlocks = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "unlocks", new JsonArray())) unlocks.add(e.getAsString());

        return new AgeDef(order,
                GsonHelper.getAsString(o, "name", file),
                GsonHelper.getAsString(o, "title", ""),
                parseColor(GsonHelper.getAsString(o, "color", "#FFB6D5")),
                GsonHelper.getAsString(o, "icon", "minecraft:stone"),
                lore, GsonHelper.getAsString(o, "weaver", ""),
                breaks, offering, trial, blocks, opening,
                GsonHelper.getAsDouble(mobsObj, "chance", 0.0), mobs,
                GsonHelper.getAsDouble(cacheObj, "chance", 0.0), GsonHelper.getAsInt(cacheObj, "rolls", 4), cache,
                unlocks, GsonHelper.getAsDouble(o, "keep_previous", 0.0));
    }

    public static List<Loot> parseLoot(JsonArray array, String where) {
        List<Loot> list = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject l = e.getAsJsonObject();
            if (!modOk(l)) continue;
            Item item = Ids.item(GsonHelper.getAsString(l, "item"), where);
            if (item == null) continue;
            int min = GsonHelper.getAsInt(l, "min", GsonHelper.getAsInt(l, "count", 1));
            list.add(new Loot(item, min, GsonHelper.getAsInt(l, "max", min), GsonHelper.getAsInt(l, "weight", 10)));
        }
        return list;
    }

    @Nullable
    public static Trial parseTrial(String where, JsonObject t) {
        EntityType<?> entity = null;
        JsonElement ids = t.get("entity");
        List<String> candidates = new ArrayList<>();
        if (ids != null && ids.isJsonArray()) for (JsonElement e : ids.getAsJsonArray()) candidates.add(e.getAsString());
        else if (ids != null) candidates.add(ids.getAsString());
        for (String id : candidates) {
            entity = Ids.entity(id, where + " trial");
            if (entity != null) break;
        }
        if (entity == null) {
            Ids.problem(where + ": no usable trial boss in " + candidates);
            return null;
        }
        List<Minion> minions = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(t, "minions", new JsonArray())) {
            JsonObject m = e.getAsJsonObject();
            if (!modOk(m)) continue;
            EntityType<?> type = Ids.entity(GsonHelper.getAsString(m, "entity"), where + " minions");
            if (type != null) {
                minions.add(new Minion(type, GsonHelper.getAsInt(m, "every", 10), GsonHelper.getAsInt(m, "count", 2),
                        GsonHelper.getAsInt(m, "max", 6)));
            }
        }
        List<Holder<MobEffect>> effects = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(t, "effects", new JsonArray())) {
            ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
            if (id == null) continue;
            BuiltInRegistries.MOB_EFFECT.getHolder(id).ifPresentOrElse(effects::add,
                    () -> Ids.problem(where + ": unknown effect " + e.getAsString()));
        }
        Map<EquipmentSlot, Item> equipment = new EnumMap<>(EquipmentSlot.class);
        JsonObject eq = GsonHelper.getAsJsonObject(t, "equipment", new JsonObject());
        for (String slot : eq.keySet()) {
            try {
                Item item = Ids.item(eq.get(slot).getAsString(), where + " trial equipment");
                if (item != null) equipment.put(EquipmentSlot.byName(slot), item);
            } catch (IllegalArgumentException ex) {
                Ids.problem(where + ": unknown equipment slot " + slot);
            }
        }
        return new Trial(GsonHelper.getAsString(t, "name", "Guardian"), entity,
                GsonHelper.getAsDouble(t, "health", 60), GsonHelper.getAsDouble(t, "damage", 1.0),
                GsonHelper.getAsDouble(t, "scale", 1.0), GsonHelper.getAsDouble(t, "armor", 0),
                GsonHelper.getAsDouble(t, "speed", 1.0), minions, effects, equipment,
                GsonHelper.getAsString(t, "intro", ""));
    }

    private static int parseColor(String s) {
        try {
            return Integer.parseInt(s.replace("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFB6D5;
        }
    }

    /** Same Age with extra blocks (used to carry part of the previous Age's pool over). */
    AgeDef withBlocks(List<Weighted<Block>> merged) {
        return new AgeDef(order, name, title, color, icon, lore, weaver, breaks, offering, trial, merged, opening,
                mobChance, mobs, cacheChance, cacheRolls, cache, unlocks, keepPrevious);
    }
}
