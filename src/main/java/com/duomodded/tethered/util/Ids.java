package com.duomodded.tethered.util;

import com.duomodded.tethered.Tethered;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Looks up blocks, items and mobs by id for the data files. Ids from mods that aren't installed are
 * skipped quietly. Ids that should exist but don't are collected (with close matches) so the CI
 * smoke test can print them, which is how wrong ids get found and fixed.
 */
public final class Ids {
    private static final Set<String> PROBLEMS = Collections.synchronizedSet(new LinkedHashSet<>());

    public static boolean modPresent(ResourceLocation id) {
        String ns = id.getNamespace();
        return "minecraft".equals(ns) || Tethered.MODID.equals(ns) || ModList.get().isLoaded(ns);
    }

    @Nullable
    public static ResourceLocation parse(String id) {
        return ResourceLocation.tryParse(id.trim());
    }

    @Nullable
    public static Block block(String id, String where) {
        return lookup(BuiltInRegistries.BLOCK, id, where, "block");
    }

    @Nullable
    public static Item item(String id, String where) {
        return lookup(BuiltInRegistries.ITEM, id, where, "item");
    }

    @Nullable
    public static EntityType<?> entity(String id, String where) {
        return lookup(BuiltInRegistries.ENTITY_TYPE, id, where, "mob");
    }

    @Nullable
    private static <T> T lookup(Registry<T> registry, String raw, String where, String kind) {
        ResourceLocation id = parse(raw);
        if (id == null) {
            problem(where + ": bad " + kind + " id '" + raw + "'");
            return null;
        }
        Optional<T> found = registry.getOptional(id);
        if (found.isPresent()) return found.get();
        if (modPresent(id)) {
            problem(where + ": unknown " + kind + " " + id + suggestions(registry, id));
        }
        return null;
    }

    /** Up to five ids from the same mod that share a word with the missing one. */
    private static <T> String suggestions(Registry<T> registry, ResourceLocation id) {
        String[] words = id.getPath().split("[_/]");
        List<String> hits = new ArrayList<>();
        for (ResourceLocation key : registry.keySet()) {
            if (!key.getNamespace().equals(id.getNamespace())) continue;
            int score = 0;
            for (String w : words) if (w.length() > 2 && key.getPath().contains(w)) score++;
            if (score > 0) hits.add(score + ":" + key.getPath());
        }
        hits.sort((a, b) -> Integer.parseInt(b.split(":")[0]) - Integer.parseInt(a.split(":")[0]));
        if (hits.isEmpty()) return "";
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5, hits.size()); i++) top.add(hits.get(i).substring(hits.get(i).indexOf(':') + 1));
        return "  (maybe: " + String.join(", ", top) + ")";
    }

    public static void problem(String text) {
        if (PROBLEMS.add(text)) Tethered.LOGGER.warn("Tethered data: {}", text);
    }

    public static List<String> problems() {
        synchronized (PROBLEMS) {
            return new ArrayList<>(PROBLEMS);
        }
    }

    public static void clear(String prefix) {
        synchronized (PROBLEMS) {
            PROBLEMS.removeIf(p -> p.startsWith(prefix));
        }
    }

    private Ids() {}
}
