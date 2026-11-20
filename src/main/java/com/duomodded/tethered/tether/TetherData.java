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

/**
 * World-wide save data, stored on the overworld: who is tied to whom, and the shared story progress
 * (the Heartstone's Age, its memories, the offering, trials, events, quests and everyone's Boons).
 */
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

    // The Heartstone (One Block) and the Ages
    @Nullable
    private BlockPos heartPos;
    private int ageBreaks;
    private int totalBreaks;
    private int openingIndex;
    private int[] offering = new int[0];
    private boolean trialReady;
    @Nullable
    private UUID trialGuardian;
    private final Map<String, Integer> eventStats = new HashMap<>();
    private final Set<String> dimensions = new HashSet<>();
    private final Map<UUID, Map<String, Integer>> boons = new HashMap<>();
    private final Map<UUID, List<String>> boonOffers = new HashMap<>();
    private long lastEventEnd;
    private long lastNightRolled = -1;

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

    // ---- the Heartstone and the Ages (heartStage is the current Age)
    @Nullable public BlockPos heartPos() { return heartPos; }
    public void setHeartPos(BlockPos pos) { heartPos = pos.immutable(); setDirty(); }
    public int age() { return heartStage; }
    public int ageBreaks() { return ageBreaks; }
    public int totalBreaks() { return totalBreaks; }
    public void addBreak() { ageBreaks++; totalBreaks++; setDirty(); }
    public void addBreaks(int n) { ageBreaks += n; totalBreaks += n; setDirty(); }
    public int openingIndex() { return openingIndex; }
    public void setOpeningIndex(int i) { openingIndex = i; setDirty(); }
    public int[] offering(int size) {
        if (offering.length != size) offering = java.util.Arrays.copyOf(offering, size);
        return offering;
    }
    public boolean trialReady() { return trialReady; }
    public void setTrialReady(boolean ready) { trialReady = ready; setDirty(); }
    @Nullable public UUID trialGuardian() { return trialGuardian; }
    public void setTrialGuardian(@Nullable UUID id) { trialGuardian = id; setDirty(); }

    /** Moves the story into the next Age and resets everything that belongs to the old one. */
    public void beginAge(int age) {
        setHeartStage(age);
        ageBreaks = 0;
        openingIndex = 0;
        offering = new int[0];
        trialReady = false;
        trialGuardian = null;
        setDirty();
    }

    public Map<String, Integer> eventStats() { return eventStats; }
    public void countEvent(String type) { eventStats.merge(type, 1, Integer::sum); setDirty(); }
    public Set<String> dimensions() { return dimensions; }
    public long lastEventEnd() { return lastEventEnd; }
    public void setLastEventEnd(long t) { lastEventEnd = t; setDirty(); }
    public long lastNightRolled() { return lastNightRolled; }
    public void setLastNightRolled(long day) { lastNightRolled = day; setDirty(); }

    // ---- Boons (per player)
    public Map<String, Integer> boons(UUID id) { return boons.computeIfAbsent(id, k -> new HashMap<>()); }
    public Map<UUID, Map<String, Integer>> allBoons() { return boons; }
    @Nullable public List<String> boonOffer(UUID id) { return boonOffers.get(id); }
    public void setBoonOffer(UUID id, @Nullable List<String> offer) {
        if (offer == null) boonOffers.remove(id); else boonOffers.put(id, new ArrayList<>(offer));
        setDirty();
    }
    public int boonsChosen() {
        int n = 0;
        for (Map<String, Integer> m : boons.values()) for (int r : m.values()) n += r;
        return n;
    }

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

        if (heartPos != null) tag.putLong("heartPos", heartPos.asLong());
        tag.putInt("ageBreaks", ageBreaks);
        tag.putInt("totalBreaks", totalBreaks);
        tag.putInt("openingIndex", openingIndex);
        tag.putIntArray("offering", offering);
        tag.putBoolean("trialReady", trialReady);
        if (trialGuardian != null) tag.putUUID("trialGuardian", trialGuardian);
        CompoundTag events = new CompoundTag();
        eventStats.forEach(events::putInt);
        tag.put("eventStats", events);
        tag.put("dimensions", strings(dimensions));
        CompoundTag boonTag = new CompoundTag();
        boons.forEach((id, ranks) -> {
            CompoundTag t = new CompoundTag();
            ranks.forEach(t::putInt);
            boonTag.put(id.toString(), t);
        });
        tag.put("boons", boonTag);
        CompoundTag offerTag = new CompoundTag();
        boonOffers.forEach((id, offer) -> offerTag.put(id.toString(), strings(new LinkedHashSet<>(offer))));
        tag.put("boonOffers", offerTag);
        tag.putLong("lastEventEnd", lastEventEnd);
        tag.putLong("lastNightRolled", lastNightRolled);
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

        if (tag.contains("heartPos")) data.heartPos = BlockPos.of(tag.getLong("heartPos"));
        data.ageBreaks = tag.getInt("ageBreaks");
        data.totalBreaks = tag.getInt("totalBreaks");
        data.openingIndex = tag.getInt("openingIndex");
        data.offering = tag.getIntArray("offering");
        data.trialReady = tag.getBoolean("trialReady");
        if (tag.hasUUID("trialGuardian")) data.trialGuardian = tag.getUUID("trialGuardian");
        CompoundTag events = tag.getCompound("eventStats");
        for (String key : events.getAllKeys()) data.eventStats.put(key, events.getInt(key));
        readStrings(tag, "dimensions", data.dimensions);
        CompoundTag boonTag = tag.getCompound("boons");
        for (String key : boonTag.getAllKeys()) {
            try {
                Map<String, Integer> ranks = new HashMap<>();
                CompoundTag t = boonTag.getCompound(key);
                for (String b : t.getAllKeys()) ranks.put(b, t.getInt(b));
                data.boons.put(UUID.fromString(key), ranks);
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        CompoundTag offerTag = tag.getCompound("boonOffers");
        for (String key : offerTag.getAllKeys()) {
            try {
                ListTag l = offerTag.getList(key, Tag.TAG_STRING);
                List<String> offer = new ArrayList<>();
                for (int i = 0; i < l.size(); i++) offer.add(l.getString(i));
                data.boonOffers.put(UUID.fromString(key), offer);
            } catch (IllegalArgumentException ignored) {
                // skip bad entry
            }
        }
        data.lastEventEnd = tag.getLong("lastEventEnd");
        data.lastNightRolled = tag.contains("lastNightRolled") ? tag.getLong("lastNightRolled") : -1;
        return data;
    }
}
