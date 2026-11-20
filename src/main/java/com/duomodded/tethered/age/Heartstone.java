package com.duomodded.tethered.age;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.boon.Boons;
import com.duomodded.tethered.heart.Blessings;
import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.heart.HeartTreeBlock;
import com.duomodded.tethered.heart.TreeShaper;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.util.Advancements;
import com.duomodded.tethered.world.IslandManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Heartstone: a One Block at the core of the Last Stone. Break the block on top of it and it
 * re-weaves a new one from the current Age's memories (blocks, sometimes a mob or a cache chest).
 * Enough memories + an offering + a won Trial moves the whole world into the next Age.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class Heartstone {
    private static boolean pending;
    private static boolean pendingCounted;

    // Trial runtime state (the guardian's UUID itself is saved in TetherData)
    @Nullable
    private static ServerBossEvent trialBar;
    private static final List<UUID> MINIONS = new ArrayList<>();
    private static int trialSeconds;
    private static int missingSeconds;
    private static int awaySeconds;

    // ------------------------------------------------------------------ positions

    @Nullable
    public static BlockPos core(MinecraftServer server) {
        return TetherData.get(server).heartPos();
    }

    @Nullable
    public static BlockPos live(MinecraftServer server) {
        BlockPos core = core(server);
        return core == null ? null : core.above();
    }

    public static int breaksNeeded(int nextAge) {
        AgeDef def = AgeLoader.get(nextAge);
        if (def == null) return Integer.MAX_VALUE;
        return Math.max(1, (int) Math.round(def.breaks() * TetherConfig.MEMORY_MULTIPLIER.get()));
    }

    public static boolean memoriesDone(TetherData data) {
        return data.ageBreaks() >= breaksNeeded(data.age() + 1);
    }

    public static boolean offeringDone(TetherData data) {
        AgeDef next = AgeLoader.get(data.age() + 1);
        if (next == null) return false;
        int[] prog = data.offering(next.offering().size());
        for (int i = 0; i < prog.length; i++) {
            if (prog[i] < next.offering().get(i).count()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ breaking the One Block

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        BlockPos live = live(level.getServer());
        if (live == null || !event.getPos().equals(live)) return;
        pending = true;
        pendingCounted = true;
        if (event.getPlayer() instanceof ServerPlayer p) {
            int echo = Boons.rank(p, "heart_echo");
            if (echo > 0 && level.random.nextFloat() < echo * 0.06f) {
                ItemStack bonus = new ItemStack(event.getState().getBlock().asItem());
                if (!bonus.isEmpty()) Block.popResource(level, live, bonus);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        TetherData data = TetherData.get(server);
        if (data.heartPos() == null) return;
        ServerLevel level = server.overworld();
        if (pending) {
            pending = false;
            regen(level, pendingCounted);
        }
        int tick = server.getTickCount();
        if (tick % 20 == 0) {
            BlockPos live = live(server);
            if (live != null && level.isLoaded(live) && level.getBlockState(live).isAir()) regen(level, false);
            tickTrial(server, level, data);
        }
        if (tick % 10 == 0 && data.age() >= Blessings.GREEN_THUMB && level.isLoaded(data.heartPos())) {
            Blessings.growCrops(level, data.heartPos(), level.random);
        }
    }

    /** Puts a new block on the Heartstone. Counted = a player broke the last one. */
    public static void regen(ServerLevel level, boolean counted) {
        MinecraftServer server = level.getServer();
        TetherData data = TetherData.get(server);
        BlockPos live = live(server);
        if (live == null) return;
        if (!level.getBlockState(live).isAir()) return;
        AgeDef def = AgeLoader.get(data.age());
        RandomSource random = level.random;

        if (counted) {
            boolean wasDone = memoriesDone(data);
            data.addBreak();
            if (data.totalBreaks() == 1) firstMemory(server);
            if (!wasDone && memoriesDone(data)) memoriesComplete(server, data);
            QuestManager.markDirty();
        }

        boolean cache = false;
        BlockState state;
        if (def != null && data.openingIndex() < def.opening().size()) {
            state = def.opening().get(data.openingIndex()).defaultBlockState();
            data.setOpeningIndex(data.openingIndex() + 1);
        } else if (def != null && counted && !def.cache().isEmpty()
                && (random.nextDouble() < def.cacheChance() || (data.totalBreaks() % 100 == 0))) {
            state = Blocks.CHEST.defaultBlockState();
            cache = true;
        } else if (def != null && !def.blocks().isEmpty()) {
            state = AgeDef.pick(def.blocks(), random).defaultBlockState();
        } else {
            state = Blocks.STONE.defaultBlockState();
        }
        if (state.hasProperty(LeavesBlock.PERSISTENT)) state = state.setValue(LeavesBlock.PERSISTENT, true);
        level.setBlock(live, state, 3);
        if (cache && level.getBlockEntity(live) instanceof ChestBlockEntity chest) {
            List<ItemStack> loot = rollCache(data.age(), random, def.cacheRolls());
            for (ItemStack stack : loot) {
                int slot = random.nextInt(chest.getContainerSize());
                for (int i = 0; i < chest.getContainerSize() && !chest.getItem(slot).isEmpty(); i++) {
                    slot = (slot + 1) % chest.getContainerSize();
                }
                chest.setItem(slot, stack);
            }
            Vec3 c = Vec3.atCenterOf(live);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 0.6, c.z, 30, 0.4, 0.4, 0.4, 0.2);
            level.playSound(null, live, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.4f);
        }

        if (counted && def != null && !def.mobs().isEmpty() && random.nextDouble() < def.mobChance()) {
            EntityType<?> type = AgeDef.pick(def.mobs(), random);
            Mob mob = Bosses.spawnMob(level, type, live.getX() + 0.5, live.getY() + 1.05, live.getZ() + 0.5);
            if (mob != null) level.addFreshEntity(mob);
        }

        Vec3 c = Vec3.atCenterOf(live);
        level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.3, c.z, 12, 0.5, 0.5, 0.5, 0.6);
        if (counted) {
            level.playSound(null, live, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 0.7f, 0.8f + random.nextFloat() * 0.4f);
            Component bar = progressLine(data);
            for (ServerPlayer p : level.players()) {
                if (p.blockPosition().distSqr(live) < 24 * 24) p.displayClientMessage(bar, true);
            }
        }
    }

    public static Component progressLine(TetherData data) {
        int next = data.age() + 1;
        AgeDef nextDef = AgeLoader.get(next);
        String age = AgeLoader.nameOf(data.age());
        if (nextDef == null) {
            return Component.literal(age + " · " + data.totalBreaks() + " memories").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (!memoriesDone(data)) {
            return Component.translatable("msg.tethered.heart.progress", age, data.ageBreaks(), breaksNeeded(next))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return Component.translatable(offeringDone(data) ? "msg.tethered.heart.trial_ready" : "msg.tethered.heart.wants_offering")
                .withStyle(ChatFormatting.GOLD);
    }

    /** Items from the current Age's cache list (falls back to earlier Ages if it has none). */
    public static List<ItemStack> rollCache(int age, RandomSource random, int rolls) {
        List<ItemStack> out = new ArrayList<>();
        AgeDef def = null;
        for (int a = age; a >= 0 && (def == null || def.cache().isEmpty()); a--) def = AgeLoader.get(a);
        if (def == null || def.cache().isEmpty()) return out;
        for (int i = 0; i < Math.max(1, rolls); i++) out.add(AgeDef.pickLoot(def.cache(), random).roll(random));
        return out;
    }

    private static void firstMemory(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("weaver.tethered.first_memory").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            Advancements.award(p, "first_memory");
        }
    }

    private static void memoriesComplete(MinecraftServer server, TetherData data) {
        AgeDef next = AgeLoader.get(data.age() + 1);
        if (next == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("msg.tethered.heart.remembers", next.title())
                    .withStyle(ChatFormatting.GOLD));
            p.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.3f);
        }
    }

    // ------------------------------------------------------------------ offering & status

    public static boolean offer(ServerPlayer player, ItemStack stack) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        TetherData data = TetherData.get(server);
        AgeDef next = AgeLoader.get(data.age() + 1);
        if (next == null) return false;
        if (!memoriesDone(data)) {
            player.displayClientMessage(Component.translatable("msg.tethered.heart.not_yet",
                    breaksNeeded(data.age() + 1) - data.ageBreaks()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return false;
        }
        int[] prog = data.offering(next.offering().size());
        for (int i = 0; i < prog.length; i++) {
            AgeDef.Offer req = next.offering().get(i);
            if (prog[i] >= req.count() || !req.matches(stack)) continue;
            int take = Math.min(req.count() - prog[i], stack.getCount());
            prog[i] += take;
            if (!player.getAbilities().instabuild) stack.shrink(take);
            data.setDirty();
            player.displayClientMessage(Component.empty().append(req.displayName())
                    .append(Component.literal("  " + prog[i] + "/" + req.count()))
                    .withStyle(prog[i] >= req.count() ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
            ServerLevel level = player.serverLevel();
            BlockPos core = data.heartPos();
            if (core != null) {
                Vec3 c = Vec3.atCenterOf(core);
                level.sendParticles(ParticleTypes.HEART, c.x, c.y + 1.4, c.z, 3, 0.4, 0.3, 0.4, 0.02);
                level.playSound(null, core, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
            }
            if (offeringDone(data)) offeringComplete(server, data, next);
            QuestManager.markDirty();
            return true;
        }
        return false;
    }

    private static void offeringComplete(MinecraftServer server, TetherData data, AgeDef next) {
        if (next.trial() == null) {
            advance(server);
            return;
        }
        data.setTrialReady(true);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable("msg.tethered.heart.trial_waits", next.trial().name())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Right-click with an empty hand: start the Trial if it's ready, otherwise show what's needed. */
    public static void interact(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        if (data.trialReady() && data.trialGuardian() == null) {
            startTrial(player, data);
            return;
        }
        showStatus(player);
    }

    public static void showStatus(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        AgeDef current = AgeLoader.get(data.age());
        AgeDef next = AgeLoader.get(data.age() + 1);
        String title = current == null ? "" : current.title();
        player.sendSystemMessage(Component.literal("⟡ " + title + " — " + AgeLoader.nameOf(data.age()))
                .withStyle(s -> s.withColor(current == null ? 0xFFB6D5 : current.color())));
        if (next == null) {
            player.sendSystemMessage(Component.translatable("msg.tethered.heart.whole").withStyle(ChatFormatting.GOLD));
            return;
        }
        int need = breaksNeeded(data.age() + 1);
        player.sendSystemMessage(Component.translatable("msg.tethered.heart.memories", Math.min(data.ageBreaks(), need), need)
                .withStyle(memoriesDone(data) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("msg.tethered.heart.offering_for", next.title()).withStyle(ChatFormatting.LIGHT_PURPLE));
        int[] prog = data.offering(next.offering().size());
        for (int i = 0; i < prog.length; i++) {
            AgeDef.Offer req = next.offering().get(i);
            boolean done = prog[i] >= req.count();
            player.sendSystemMessage(Component.literal(done ? "  ✔ " : "  • ").append(req.displayName())
                    .append(Component.literal("  " + prog[i] + "/" + req.count()))
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        if (next.trial() != null) {
            MutableComponent trial = Component.translatable("msg.tethered.heart.trial_line", next.trial().name());
            if (data.trialGuardian() != null) trial.append(Component.translatable("msg.tethered.heart.trial_active"));
            else if (data.trialReady()) trial.append(Component.translatable("msg.tethered.heart.trial_start_hint"));
            player.sendSystemMessage(trial.withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    // ------------------------------------------------------------------ the Trial

    private static void startTrial(ServerPlayer player, TetherData data) {
        MinecraftServer server = player.getServer();
        AgeDef next = AgeLoader.get(data.age() + 1);
        BlockPos core = data.heartPos();
        if (server == null || next == null || next.trial() == null || core == null) return;
        ServerLevel level = server.overworld();
        if (TetherConfig.REQUIRE_BOTH_AT_TREE.get()) {
            ServerPlayer partner = TetherManager.partnerOf(player);
            if (partner != null && (partner.level() != level || partner.blockPosition().distSqr(core) > 24 * 24)) {
                player.displayClientMessage(Component.translatable("msg.tethered.heart.trial_together").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                return;
            }
        }
        BlockPos spot = Bosses.findSpot(level, core.above(), 3, 6, 40);
        if (spot == null) spot = core.above(2).south(3);
        int players = Math.max(1, (int) level.players().stream().filter(p -> p.blockPosition().distSqr(core) < 48 * 48).count());
        Mob boss = Bosses.spawnBoss(level, spot, next.trial(), 1.0 + 0.5 * (players - 1));
        if (boss == null) {
            Tethered.LOGGER.warn("Could not spawn the Trial guardian {}; skipping the Trial", next.trial().entity());
            advance(server);
            return;
        }
        data.setTrialGuardian(boss.getUUID());
        MINIONS.clear();
        trialSeconds = 0;
        missingSeconds = 0;
        awaySeconds = 0;
        level.playSound(null, spot, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.3f);
        Vec3 c = Vec3.atCenterOf(spot);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 120, 1.0, 1.5, 1.0, 0.2);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Ending.title(p, Component.literal(next.trial().name()).withStyle(ChatFormatting.DARK_PURPLE),
                    Component.translatable("msg.tethered.heart.trial_title", next.title()));
            if (!next.trial().intro().isEmpty()) {
                p.sendSystemMessage(Component.literal(next.trial().intro()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        }
    }

    private static void tickTrial(MinecraftServer server, ServerLevel level, TetherData data) {
        UUID id = data.trialGuardian();
        if (id == null) {
            if (trialBar != null) {
                trialBar.removeAllPlayers();
                trialBar = null;
            }
            return;
        }
        AgeDef next = AgeLoader.get(data.age() + 1);
        Entity e = level.getEntity(id);
        if (!(e instanceof LivingEntity guardian) || !guardian.isAlive()) {
            if (++missingSeconds >= 30) failTrial(server, data, "msg.tethered.heart.trial_lost");
            return;
        }
        missingSeconds = 0;
        trialSeconds++;
        if (trialBar == null) {
            trialBar = Bosses.newBar(guardian.getDisplayName(), BossEvent.BossBarColor.PURPLE);
        }
        Bosses.updateBar(trialBar, level, guardian, 64);
        if (guardian instanceof Mob mob) Bosses.keepHunting(level, mob, 48);

        if (next != null && next.trial() != null) {
            MINIONS.removeIf(m -> {
                Entity me = level.getEntity(m);
                return me == null || !me.isAlive();
            });
            for (AgeDef.Minion minion : next.trial().minions()) {
                if (trialSeconds % Math.max(1, minion.every()) != 0 || MINIONS.size() >= minion.max()) continue;
                for (int i = 0; i < minion.count(); i++) {
                    BlockPos spot = Bosses.findSpot(level, guardian.blockPosition(), 2, 5, 10);
                    if (spot == null) continue;
                    Mob m = Bosses.spawnMob(level, minion.entity(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
                    if (m == null) continue;
                    m.addTag(Bosses.HOLLOW_TAG);
                    level.addFreshEntity(m);
                    MINIONS.add(m.getUUID());
                    level.sendParticles(ParticleTypes.SMOKE, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 10, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }

        boolean anyone = level.players().stream().anyMatch(p -> p.isAlive() && !p.isSpectator() && p.distanceTo(guardian) < 96);
        awaySeconds = anyone ? 0 : awaySeconds + 1;
        if (awaySeconds >= 60) {
            guardian.discard();
            failTrial(server, data, "msg.tethered.heart.trial_fled");
        }
    }

    private static void failTrial(MinecraftServer server, TetherData data, String key) {
        data.setTrialGuardian(null);
        if (trialBar != null) {
            trialBar.removeAllPlayers();
            trialBar = null;
        }
        ServerLevel level = server.overworld();
        for (Entity m : Bosses.alive(level, MINIONS)) m.discard();
        MINIONS.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        TetherData data = TetherData.get(server);
        UUID id = data.trialGuardian();
        if (id == null || !id.equals(event.getEntity().getUUID())) return;

        BlockPos at = event.getEntity().blockPosition();
        data.setTrialGuardian(null);
        if (trialBar != null) {
            trialBar.removeAllPlayers();
            trialBar = null;
        }
        for (Entity m : Bosses.alive(level, MINIONS)) m.discard();
        MINIONS.clear();
        RandomSource random = level.random;
        for (ItemStack stack : rollCache(data.age() + 1, random, 6)) {
            ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, stack);
            level.addFreshEntity(item);
        }
        Block.popResource(level, at, new ItemStack(ModRegistry.LOST_THREAD.get(), 8 + data.age() * 2));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 80, 1, 1, 1, 0.4);
        server.execute(() -> advance(server));
    }

    // ------------------------------------------------------------------ the next Age

    /** Moves the world into the next Age. Returns false at the end of the story. */
    public static boolean advance(MinecraftServer server) {
        TetherData data = TetherData.get(server);
        int age = data.age() + 1;
        AgeDef def = AgeLoader.get(age);
        if (def == null) return false;
        data.beginAge(age);

        ServerLevel level = server.overworld();
        growWorldTree(level, data, age);
        for (String island : def.unlocks()) IslandManager.unlock(server, island);
        if (age >= AgeLoader.maxAge()) {
            data.setEndingUnlocked();
        }

        BlockPos core = data.heartPos();
        if (core != null) {
            Vec3 c = Vec3.atCenterOf(core);
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2, c.z, 200, 4, 3, 4, 0.08);
            level.playSound(null, core, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        Component title = Component.literal(def.title()).withStyle(s -> s.withColor(def.color()));
        Component subtitle = Component.literal(def.name()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Ending.title(p, title, subtitle);
            p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
            if (!def.lore().isEmpty()) {
                p.sendSystemMessage(Component.literal(def.lore().get(0)).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            if (!def.weaver().isEmpty()) {
                p.sendSystemMessage(Component.literal("The Weaver: \"" + def.weaver() + "\"").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (age >= 1 && age <= Blessings.COUNT) {
                p.sendSystemMessage(Component.translatable("msg.tethered.tree.blessing",
                        Component.translatable("blessing.tethered." + age).withStyle(ChatFormatting.GOLD),
                        Component.translatable("blessing.tethered." + age + ".desc")).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            p.sendSystemMessage(Component.translatable("msg.tethered.heart.favor_gift").withStyle(ChatFormatting.GOLD));
            ItemStack favor = new ItemStack(ModRegistry.WEAVERS_FAVOR.get());
            if (!p.getInventory().add(favor)) p.drop(favor, false);
            p.sendSystemMessage(Component.translatable("msg.tethered.heart.story_hint").withStyle(ChatFormatting.DARK_GRAY));
            Advancements.award(p, "age/age_" + age);
            TetherManager.sync(p);
        }
        if (data.endingUnlocked()) Ending.offerAll(server);
        QuestManager.markDirty();
        return true;
    }

    /** The World Tree on the summit of the Last Stone grows with every Age. */
    public static void growWorldTree(ServerLevel level, TetherData data, int age) {
        BlockPos tree = data.heartTreePos();
        if (tree == null) return;
        int stage = Math.min(7, Math.max(0, age));
        BlockState state = level.getBlockState(tree);
        if (state.getBlock() instanceof HeartTreeBlock) {
            level.setBlock(tree, state.setValue(HeartTreeBlock.STAGE, stage), 3);
        }
        if (stage > 0) TreeShaper.grow(level, tree, stage, age >= 7);
    }

    /** Operator command / smoke test: skip the memories, offering and Trial. */
    public static boolean forceAdvance(MinecraftServer server) {
        TetherData data = TetherData.get(server);
        if (data.trialGuardian() != null) {
            Entity e = server.overworld().getEntity(data.trialGuardian());
            if (e != null) e.discard();
            data.setTrialGuardian(null);
        }
        return advance(server);
    }

    /** Smoke test: spawn the next Trial guardian once to make sure it works, then remove it. */
    @Nullable
    public static String testTrial(MinecraftServer server, int age) {
        AgeDef def = AgeLoader.get(age);
        BlockPos core = core(server);
        if (def == null || def.trial() == null || core == null) return null;
        Mob mob = Bosses.spawnBoss(server.overworld(), core.above(2).south(3), def.trial(), 1.0);
        if (mob == null) return "could not spawn " + def.trial().entity();
        String result = def.trial().name() + " (" + mob.getType().builtInRegistryHolder().key().location()
                + ", " + (int) mob.getMaxHealth() + " hp)";
        mob.discard();
        return result;
    }

    private Heartstone() {}
}
