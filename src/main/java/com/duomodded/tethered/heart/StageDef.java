package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One Heart Tree stage, loaded from data/[namespace]/heart_tree_stages/*.json.
 * "requires" is what the pair must offer to reach this stage; "rings" are the islands it spawns.
 * Requirements accept an item id or a tag (prefix with #), so modpacks can use c: tags.
 */
public record StageDef(int stage, String name, String message, List<Requirement> requires, List<RingSpec> rings,
                       List<String> unlocks) {

    public record Requirement(@Nullable Item item, @Nullable TagKey<Item> tag, int count) {
        public boolean matches(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return item != null ? stack.is(item) : tag != null && stack.is(tag);
        }

        public Component displayName() {
            if (item != null) return item.getDescription();
            if (tag == null) return Component.literal("?");
            Optional<Component> first = BuiltInRegistries.ITEM.getTag(tag)
                    .flatMap(set -> set.stream().findFirst())
                    .map(holder -> holder.value().getDescription());
            return first.<Component>map(c -> Component.translatable("msg.tethered.tree.any", c))
                    .orElse(Component.literal("#" + tag.location()));
        }
    }

    public static StageDef parse(JsonObject o) {
        int stage = GsonHelper.getAsInt(o, "stage");
        List<Requirement> requires = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "requires", new JsonArray())) {
            JsonObject r = e.getAsJsonObject();
            int count = GsonHelper.getAsInt(r, "count", 1);
            if (r.has("tag")) {
                String tag = GsonHelper.getAsString(r, "tag").replace("#", "");
                requires.add(new Requirement(null, TagKey.create(Registries.ITEM, ResourceLocation.parse(tag)), count));
            } else {
                ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(r, "item"));
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isPresent()) {
                    requires.add(new Requirement(item.get(), null, count));
                } else {
                    Tethered.LOGGER.warn("Heart Tree stage {}: unknown item {} (mod not installed?), skipping", stage, id);
                }
            }
        }
        List<RingSpec> rings = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "rings", new JsonArray())) {
            rings.add(RingSpec.parse(e.getAsJsonObject()));
        }
        List<String> unlocks = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "unlocks", new JsonArray())) {
            unlocks.add(e.getAsString());
        }
        return new StageDef(stage,
                GsonHelper.getAsString(o, "name", "Stage " + stage),
                GsonHelper.getAsString(o, "message", ""),
                requires, rings, unlocks);
    }
}
