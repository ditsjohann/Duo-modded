package com.duomodded.tethered.tether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-wide save data: who is tied to whom, plus Heart Tree progress. Stored on the overworld. */
public final class TetherData extends SavedData {
    private static final String NAME = "tethered";

    private final Map<UUID, Link> links = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private boolean startIslandGenerated;
    @Nullable
    private BlockPos heartTreePos;
    private int heartStage;

    // World progress, shared by everyone in this (duo) world
    private final Set<String> unlockedIslands = new HashSet<>();
    private final Set<String> generatedIslands = new HashSet<>();
    private final Set<String> visitedIslands = new HashSet<>();
    private final Set<String> attunedIslands = new HashSet<>();
    private final Set<String> completedQuests = new HashSet<>();
    private final Map<String, Integer> taskProgress = new HashMap<>();
    private final Set<UUID> journalGiven = new HashSet<>();
    private boolean endingUnlocked;

    /** Extra rope length from the Heart Tree's "Deep Roots" blessing (kept in sync with the stage). */
    public static volatile float ropeBonus;

    public static final class Link {
        public final UUID a;
        public final UUID b;
        public int tier;
        public boolean slack;
        public boolean endingOffered;

        // Runtime only (not saved)
        public float currentLength;
        public final Set<UUID> hauling = new HashSet<>();
        public final Map<UUID, Boolean> choices = new HashMap<>();

        Link(UUID a, UUID b, int tier) {
            this.a = a;
            this.b = b;
            this.tier = tier;
            this.currentLength = maxLength();
        }

        public UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }

        public RopeTier ropeTier() {
            return RopeTier.byLevel(tier);
        }

        public float maxLength() {
            return ropeTier().length + ropeBonus;
        }

        public void setTier(int newTier) {
            this.tier = newTier;
            this.currentLength = maxLength();
            if (newTier < RopeTier.GOLDEN.level) this.slack = false;
        }
    }

    public TetherData() {
        ropeBonus = 0f; // fresh world (load() sets it again from the saved stage)
    }

    public static TetherData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(TetherData::new, TetherData::load, null), NAME);
    }

    @Nullable
    public Link getLink(UUID id) {
        return links.get(id);
    }

    public Link link(UUID a, UUID b, int tier) {
        unlink(a);
        unlink(b);
        Link link = new Link(a, b, tier);
        links.put(a, link);
        links.put(b, link);
        setDirty();
        return link;
    }

    @Nullable
    public Link unlink(UUID id) {
        Link link = links.remove(id);
        if (link != null) {
            links.remove(link.other(id));
            setDirty();
        }
        return link;
    }

    /** Each link once (both players map to the same object). */
    public List<Link> uniqueLinks() {
        return new ArrayList<>(new LinkedHashSet<>(links.values()));
    }

    public void rememberName(UUID id, String name) {
        if (!name.equals(names.put(id, name))) setDirty();
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, "???");
    }

    public boolean startIslandGenerated() {
        return startIslandGenerated;
    }

    public void setStartIslandGenerated() {
        startIslandGenerated = true;
        setDirty();
    }

    @Nullable
    public BlockPos heartTreePos() {
        return heartTreePos;
    }

    public void setHeartTreePos(BlockPos pos) {
        heartTreePos = pos.immutable();
        setDirty();
    }

    public int heartStage() {
        return heartStage;
    }

    public void setHeartStage(int stage) {
        heartStage = Math.max(heartStage, stage);
        ropeBonus = com.duomodded.tethered.heart.Blessings.ropeBonus(heartStage);
        setDirty();
    }

    // ---- islands and quests
    public Set<String> unlockedIslands() { return unlockedIslands; }
    public Set<String> generatedIslands() { return generatedIslands; }
    public Set<String> visitedIslands() { return visitedIslands; }
    public Set<String> attunedIslands() { return attunedIslands; }
    public Set<String> completedQuests() { return completedQuests; }
    public Map<String, Integer> taskProgress() { return taskProgress; }

    public boolean markJournalGiven(UUID id) {
        boolean added = journalGiven.add(id);
        if (added) setDirty();
        return added;
    }

    public boolean endingUnlocked() { return endingUnlocked; }

    public void setEndingUnlocked() {
        endingUnlocked = true;
        setDirty();
    }

    private static ListTag strings(Set<String> set) {
        ListTag list = new ListTag();
        for (String s : set) list.add(net.minecraft.nbt.StringTag.valueOf(s));
        return list;
    }

    private static void readStrings(CompoundTag tag, String key, Set<String> into) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Link link : uniqueLinks()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("a", link.a);
            t.putUUID("b", link.b);
            t.putInt("tier", link.tier);
            t.putBoolean("slack", link.slack);
            t.putBoolean("endingOffered", link.endingOffered);
            list.add(t);
        }
        tag.put("links", list);

        CompoundTag nameTag = new CompoundTag();
        names.forEach((id, name) -> nameTag.putString(id.toString(), name));
        tag.put("names", nameTag);

        tag.putBoolean("startIslandGenerated", startIslandGenerated);
        if (heartTreePos != null) tag.putLong("heartTreePos", heartTreePos.asLong());
        tag.putInt("heartStage", heartStage);
        tag.put("unlockedIslands", strings(unlockedIslands));
        tag.put("generatedIslands", strings(generatedIslands));
        tag.put("visitedIslands", strings(visitedIslands));
        tag.put("attunedIslands", strings(attunedIslands));
        tag.put("completedQuests", strings(completedQuests));
        CompoundTag progress = new CompoundTag();
        taskProgress.forEach(progress::putInt);
        tag.put("taskProgress", progress);
        ListTag given = new ListTag();
        for (UUID id : journalGiven) given.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("journalGiven", given);
        tag.putBoolean("endingUnlocked", endingUnlocked);
        return tag;
    }

    public static TetherData load(CompoundTag tag, HolderLookup.Provider registries) {
        TetherData data = new TetherData();
        ListTag list = tag.getList("links", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Link link = new Link(t.getUUID("a"), t.getUUID("b"), t.getInt("tier"));
            link.slack = t.getBoolean("slack");
            link.endingOffered = t.getBoolean("endingOffered");
            data.links.put(link.a, link);
            data.links.put(link.b, link);
        }
        CompoundTag nameTag = tag.getCompound("names");
        for (String key : nameTag.getAllKeys()) {
            try {
                data.names.put(UUID.fromString(key), nameTag.getString(key));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        data.startIslandGenerated = tag.getBoolean("startIslandGenerated");
        if (tag.contains("heartTreePos")) data.heartTreePos = BlockPos.of(tag.getLong("heartTreePos"));
        data.heartStage = tag.getInt("heartStage");
        ropeBonus = com.duomodded.tethered.heart.Blessings.ropeBonus(data.heartStage);
        readStrings(tag, "unlockedIslands", data.unlockedIslands);
        readStrings(tag, "generatedIslands", data.generatedIslands);
        readStrings(tag, "visitedIslands", data.visitedIslands);
        readStrings(tag, "attunedIslands", data.attunedIslands);
        readStrings(tag, "completedQuests", data.completedQuests);
        CompoundTag progress = tag.getCompound("taskProgress");
        for (String key : progress.getAllKeys()) data.taskProgress.put(key, progress.getInt(key));
        ListTag given = tag.getList("journalGiven", Tag.TAG_STRING);
        for (int i = 0; i < given.size(); i++) {
            try {
                data.journalGiven.add(UUID.fromString(given.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        data.endingUnlocked = tag.getBoolean("endingUnlocked");
        return data;
    }
}
