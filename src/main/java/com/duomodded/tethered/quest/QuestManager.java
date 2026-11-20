package com.duomodded.tethered.quest;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.heart.Blessings;
import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.age.AgeDef;
import com.duomodded.tethered.age.AgeLoader;
import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.event.WorldEvents;
import com.duomodded.tethered.network.JournalSyncPayload;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.world.IslandDef;
import com.duomodded.tethered.world.IslandLoader;
import com.duomodded.tethered.world.IslandManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Quest progress is shared by the whole world (it's a duo world: one story for the two of you).
 * Checks tasks every 2 seconds and on events, hands out rewards, and keeps the journal in sync.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class QuestManager {
    private static volatile boolean dirty = true;

    public static void markDirty() {
        dirty = true;
    }

    public enum Status { LOCKED, AVAILABLE, DONE, HIDDEN }

    // ------------------------------------------------------------------ status

    public static Status status(MinecraftServer server, QuestDefs.Quest quest) {
        if (quest.hidden()) return Status.HIDDEN;
        TetherData data = TetherData.get(server);
        if (data.completedQuests().contains(quest.id())) return Status.DONE;
        QuestDefs.Chapter chapter = chapterOf(quest);
        if (chapter != null && !chapterOpen(data, chapter)) return Status.LOCKED;
        for (String req : quest.requires()) {
            QuestDefs.Quest r = QuestLoader.quest(req);
            if (r == null || r.hidden()) continue;
            if (!data.completedQuests().contains(req)) return Status.LOCKED;
        }
        return Status.AVAILABLE;
    }

    private static boolean chapterOpen(TetherData data, QuestDefs.Chapter chapter) {
        if (chapter.island() != null && !data.unlockedIslands().contains(chapter.island())) return false;
        return data.age() >= chapter.age();
    }

    @Nullable
    private static QuestDefs.Chapter chapterOf(QuestDefs.Quest quest) {
        for (QuestDefs.Chapter c : QuestLoader.chapters()) {
            if (c.quests().contains(quest)) return c;
        }
        return null;
    }

    private static String key(QuestDefs.Quest quest, int task) {
        return quest.id() + "#" + task;
    }

    private static int progress(TetherData data, QuestDefs.Quest quest, int task) {
        return data.taskProgress().getOrDefault(key(quest, task), 0);
    }

    /** How far along a task is (0..count). */
    public static int taskValue(MinecraftServer server, QuestDefs.Quest quest, int index) {
        TetherData data = TetherData.get(server);
        QuestDefs.Task task = quest.tasks().get(index);
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return switch (task.type()) {
            case "item" -> {
                if (task.consume()) yield progress(data, quest, index);
                int best = 0;
                for (ServerPlayer p : players) best = Math.max(best, countIn(p.getInventory(), task));
                yield Math.min(task.count(), best);
            }
            case "kill" -> progress(data, quest, index);
            case "advancement" -> {
                AdvancementHolder holder = server.getAdvancements().get(task.id());
                if (holder == null) yield 0;
                for (ServerPlayer p : players) {
                    if (p.getAdvancements().getOrStartProgress(holder).isDone()) yield 1;
                }
                yield 0;
            }
            case "visit" -> data.visitedIslands().contains(task.id().getPath()) ? 1 : 0;
            case "attune" -> data.attunedIslands().contains(task.id().getPath()) ? 1 : 0;
            case "age" -> Math.min(task.count(), data.age());
            case "heart" -> Math.min(task.count(), data.totalBreaks());
            case "event" -> Math.min(task.count(), data.eventStats().getOrDefault(task.id().getPath(), 0));
            case "dimension" -> data.dimensions().contains(task.id().toString()) ? 1 : 0;
            case "boons" -> Math.min(task.count(), data.boonsChosen());
            case "together" -> pairTogether(server) ? 1 : 0;
            default -> 0;
        };
    }

    public static int taskTarget(QuestDefs.Task task) {
        return switch (task.type()) {
            case "item", "kill", "age", "heart", "event", "boons" -> task.count();
            default -> 1;
        };
    }

    private static boolean pairTogether(MinecraftServer server) {
        TetherData data = TetherData.get(server);
        for (TetherData.Link link : data.uniqueLinks()) {
            ServerPlayer a = server.getPlayerList().getPlayer(link.a);
            ServerPlayer b = server.getPlayerList().getPlayer(link.b);
            if (a != null && b != null && a.level() == b.level() && a.distanceTo(b) <= 10f) return true;
        }
        // Playing alone (no partner tethered): don't block the story.
        return data.uniqueLinks().isEmpty() && !server.getPlayerList().getPlayers().isEmpty();
    }

    private static int countIn(Inventory inv, QuestDefs.Task task) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (task.matches(s)) n += s.getCount();
        }
        return n;
    }

    private static boolean allDone(MinecraftServer server, QuestDefs.Quest quest) {
        for (int i = 0; i < quest.tasks().size(); i++) {
            if (taskValue(server, quest, i) < taskTarget(quest.tasks().get(i))) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ ticking & events

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 40 == 0) checkAll(server, null);
        if (tick % 100 == 0) {
            for (TetherData.Link link : TetherData.get(server).uniqueLinks()) {
                ServerPlayer a = server.getPlayerList().getPlayer(link.a);
                ServerPlayer b = server.getPlayerList().getPlayer(link.b);
                if (a != null && b != null) Blessings.tickPair(server, a, b);
            }
        }
        if (dirty && tick % 10 == 0) {
            dirty = false;
            syncAll(server);
        }
    }

    /** Completes every available quest whose tasks are all done. */
    public static void checkAll(MinecraftServer server, @Nullable ServerPlayer trigger) {
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 10) { // completing one quest can unlock the next
            changed = false;
            for (QuestDefs.Quest quest : QuestLoader.all().values()) {
                if (status(server, quest) == Status.AVAILABLE && allDone(server, quest)) {
                    complete(server, quest, trigger);
                    changed = true;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.getServer() == null) return;
        MinecraftServer server = killer.getServer();
        var typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        TetherData data = TetherData.get(server);
        for (QuestDefs.Quest quest : QuestLoader.all().values()) {
            if (status(server, quest) != Status.AVAILABLE) continue;
            for (int i = 0; i < quest.tasks().size(); i++) {
                QuestDefs.Task task = quest.tasks().get(i);
                if ("kill".equals(task.type()) && typeId.equals(task.id())) {
                    int now = progress(data, quest, i);
                    if (now < task.count()) {
                        data.taskProgress().put(key(quest, i), now + 1);
                        data.setDirty();
                        markDirty();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        TetherData data = TetherData.get(p.getServer());
        if (data.dimensions().add(event.getTo().location().toString())) {
            data.setDirty();
            markDirty();
        }
    }

    /** "Hand in" button: takes the needed items from the player's inventory. */
    public static void submit(ServerPlayer player, String questId) {
        MinecraftServer server = player.getServer();
        QuestDefs.Quest quest = QuestLoader.quest(questId);
        if (server == null || quest == null || status(server, quest) != Status.AVAILABLE) return;
        TetherData data = TetherData.get(server);
        Inventory inv = player.getInventory();
        int taken = 0;
        for (int i = 0; i < quest.tasks().size(); i++) {
            QuestDefs.Task task = quest.tasks().get(i);
            if (!"item".equals(task.type()) || !task.consume()) continue;
            int need = task.count() - progress(data, quest, i);
            for (int slot = 0; slot < inv.getContainerSize() && need > 0; slot++) {
                ItemStack s = inv.getItem(slot);
                if (!task.matches(s)) continue;
                int take = Math.min(need, s.getCount());
                s.shrink(take);
                need -= take;
                taken += take;
                data.taskProgress().merge(key(quest, i), take, Integer::sum);
            }
        }
        if (taken > 0) {
            data.setDirty();
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            player.displayClientMessage(Component.translatable("msg.tethered.quest.handed_in", taken).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else {
            player.displayClientMessage(Component.translatable("msg.tethered.quest.nothing_to_hand_in").withStyle(ChatFormatting.GRAY), true);
        }
        checkAll(server, player);
        markDirty();
    }

    private static void complete(MinecraftServer server, QuestDefs.Quest quest, @Nullable ServerPlayer trigger) {
        TetherData data = TetherData.get(server);
        if (!data.completedQuests().add(quest.id())) return;
        data.setDirty();
        markDirty();

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        ServerPlayer receiver = trigger != null ? trigger : (players.isEmpty() ? null : players.get(0));
        Component done = Component.translatable("msg.tethered.quest.complete",
                Component.literal(quest.title()).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : players) {
            p.sendSystemMessage(done.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
        }

        for (QuestDefs.Reward reward : quest.rewards()) {
            switch (reward.type()) {
                case "item" -> {
                    if (receiver != null && reward.item() != null) give(receiver, new ItemStack(reward.item(), reward.count()));
                }
                case "shard" -> {
                    if (receiver != null) {
                        give(receiver, new ItemStack(ModRegistry.HEART_SHARD.get(), Math.max(1, reward.count())));
                        for (ServerPlayer p : players) {
                            p.sendSystemMessage(Component.translatable("msg.tethered.quest.shard", receiver.getDisplayName())
                                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                        }
                    }
                }
                case "xp" -> {
                    for (ServerPlayer p : players) p.giveExperiencePoints(reward.count());
                }
                case "unlock" -> {
                    if (reward.island() != null) IslandManager.unlock(server, reward.island());
                }
                case "ending" -> {
                    data.setEndingUnlocked();
                    Ending.offerAll(server);
                }
                case "favor" -> {
                    for (ServerPlayer p : players) give(p, new ItemStack(ModRegistry.WEAVERS_FAVOR.get(), Math.max(1, reward.count())));
                }
                case "threads" -> {
                    if (receiver != null) give(receiver, new ItemStack(ModRegistry.LOST_THREAD.get(), Math.max(1, reward.count())));
                }
                default -> { }
            }
        }
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    // ------------------------------------------------------------------ journal sync

    public static void syncAll(MinecraftServer server) {
        String json = buildJson(server).toString();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new JournalSyncPayload(json));
        }
    }

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) PacketDistributor.sendToPlayer(player, new JournalSyncPayload(buildJson(server).toString()));
    }

    /** Everything the journal shows. Names of items/mobs are resolved on the client (it has the language files). */
    private static JsonObject buildJson(MinecraftServer server) {
        TetherData data = TetherData.get(server);
        JsonObject root = new JsonObject();
        int age = data.age();
        root.addProperty("stage", age);
        root.addProperty("age", age);
        root.addProperty("maxAge", AgeLoader.maxAge());
        AgeDef current = AgeLoader.get(age);
        root.addProperty("ageTitle", current == null ? "" : current.title());
        root.addProperty("ageName", AgeLoader.nameOf(age));
        root.addProperty("ageColor", current == null ? 0xB07AA1 : current.color());
        root.addProperty("ageBreaks", data.ageBreaks());
        root.addProperty("totalBreaks", data.totalBreaks());
        AgeDef next = AgeLoader.get(age + 1);
        root.addProperty("breaksNeeded", next == null ? 0 : Heartstone.breaksNeeded(age + 1));
        root.addProperty("trialReady", data.trialReady());
        root.addProperty("trialActive", data.trialGuardian() != null);
        if (next != null) {
            root.addProperty("nextTitle", next.title());
            root.addProperty("nextName", next.name());
            if (next.trial() != null) root.addProperty("trialName", next.trial().name());
            JsonArray offering = new JsonArray();
            int[] prog = data.offering(next.offering().size());
            for (int i = 0; i < next.offering().size(); i++) {
                AgeDef.Offer req = next.offering().get(i);
                JsonObject o = new JsonObject();
                if (req.item() != null) o.addProperty("item", BuiltInRegistries.ITEM.getKey(req.item()).toString());
                if (req.tag() != null) o.addProperty("tag", req.tag().location().toString());
                if (!req.label().isEmpty()) o.addProperty("label", req.label());
                o.addProperty("count", req.count());
                o.addProperty("value", prog[i]);
                offering.add(o);
            }
            root.add("offering", offering);
        }
        JsonArray ages = new JsonArray();
        for (AgeDef def : AgeLoader.all()) {
            JsonObject a = new JsonObject();
            a.addProperty("order", def.order());
            a.addProperty("title", def.title());
            a.addProperty("name", def.name());
            a.addProperty("color", def.color());
            a.addProperty("icon", def.icon());
            a.addProperty("unlocked", def.order() <= age);
            JsonArray lore = new JsonArray();
            if (def.order() <= age) for (String line : def.lore()) lore.add(line);
            a.add("lore", lore);
            ages.add(a);
        }
        root.add("ages", ages);
        JsonObject boons = new JsonObject();
        data.allBoons().forEach((id, ranks) -> {
            JsonObject b = new JsonObject();
            ranks.forEach(b::addProperty);
            boons.add(id.toString(), b);
        });
        root.add("boons", boons);
        JsonObject events = new JsonObject();
        data.eventStats().forEach(events::addProperty);
        root.add("events", events);
        String activeEvent = WorldEvents.activeId();
        if (activeEvent != null) root.addProperty("activeEvent", activeEvent);
        BlockPos tree = data.heartTreePos();
        if (tree != null) {
            root.addProperty("treeX", tree.getX());
            root.addProperty("treeZ", tree.getZ());
        }
        BlockPos core = data.heartPos();
        if (core != null) {
            root.addProperty("heartX", core.getX());
            root.addProperty("heartZ", core.getZ());
        }
        root.addProperty("tetheredWorld", data.startIslandGenerated());

        JsonArray islands = new JsonArray();
        for (IslandDef island : IslandLoader.all().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", island.id());
            o.addProperty("name", island.name());
            o.addProperty("desc", island.description());
            o.addProperty("color", island.color());
            o.addProperty("x", island.center().getX());
            o.addProperty("y", island.center().getY());
            o.addProperty("z", island.center().getZ());
            o.addProperty("r", island.radius());
            o.addProperty("unlocked", data.unlockedIslands().contains(island.id()));
            o.addProperty("visited", data.visitedIslands().contains(island.id()));
            o.addProperty("attuned", data.attunedIslands().contains(island.id()));
            islands.add(o);
        }
        root.add("islands", islands);

        JsonArray chapters = new JsonArray();
        for (QuestDefs.Chapter chapter : QuestLoader.chapters()) {
            JsonObject c = new JsonObject();
            c.addProperty("id", chapter.id());
            c.addProperty("title", chapter.title());
            c.addProperty("icon", chapter.icon());
            if (chapter.island() != null) c.addProperty("island", chapter.island());
            c.addProperty("unlocked", chapterOpen(data, chapter));
            c.addProperty("age", chapter.age());
            if (!chapter.description().isEmpty()) c.addProperty("desc", chapter.description());
            JsonArray quests = new JsonArray();
            int done = 0;
            int total = 0;
            for (QuestDefs.Quest quest : chapter.quests()) {
                Status status = status(server, quest);
                if (status == Status.HIDDEN) continue;
                total++;
                if (status == Status.DONE) done++;
                JsonObject q = new JsonObject();
                q.addProperty("id", quest.id());
                q.addProperty("title", quest.title());
                q.addProperty("desc", quest.description());
                q.addProperty("icon", quest.icon());
                q.addProperty("status", status.name());
                q.addProperty("optional", quest.optional());
                if (quest.mod() != null) q.addProperty("mod", quest.mod());
                JsonArray tasks = new JsonArray();
                for (int i = 0; i < quest.tasks().size(); i++) {
                    QuestDefs.Task task = quest.tasks().get(i);
                    JsonObject t = new JsonObject();
                    t.addProperty("type", task.type());
                    if (task.item() != null) t.addProperty("item", BuiltInRegistries.ITEM.getKey(task.item()).toString());
                    if (task.tag() != null) t.addProperty("tag", task.tag().location().toString());
                    if (task.id() != null) t.addProperty("id", task.type().equals("visit") || task.type().equals("attune")
                            || task.type().equals("event") ? task.id().getPath() : task.id().toString());
                    t.addProperty("count", taskTarget(task));
                    t.addProperty("value", status == Status.DONE ? taskTarget(task) : taskValue(server, quest, i));
                    t.addProperty("consume", task.consume());
                    if (!task.label().isEmpty()) t.addProperty("label", task.label());
                    tasks.add(t);
                }
                q.add("tasks", tasks);
                JsonArray rewards = new JsonArray();
                for (QuestDefs.Reward reward : quest.rewards()) {
                    JsonObject r = new JsonObject();
                    r.addProperty("type", reward.type());
                    if (reward.item() != null) r.addProperty("item", BuiltInRegistries.ITEM.getKey(reward.item()).toString());
                    r.addProperty("count", reward.count());
                    if (reward.island() != null) r.addProperty("island", reward.island());
                    rewards.add(r);
                }
                q.add("rewards", rewards);
                quests.add(q);
            }
            c.addProperty("done", done);
            c.addProperty("total", total);
            c.add("quests", quests);
            chapters.add(c);
        }
        root.add("chapters", chapters);

        root.addProperty("travel", Blessings.has(server, Blessings.HEART_PATHS));
        return root;
    }

    /** Small helper so other systems can trigger a check right away (e.g. after the tree grows). */
    public static void checkSoon() {
        markDirty();
    }

    private QuestManager() {}
}
