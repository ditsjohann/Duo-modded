package com.duomodded.tethered.event;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.age.AgeDef;
import com.duomodded.tethered.age.Bosses;
import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Unraveler doesn't sleep. Every night (and sometimes by day) something can happen: a Hollow
 * Tide climbs out of the dark, threads fall from the cracks, the Wandering Weaver visits, the
 * Crimson Weave makes monsters vicious, the Heartstone trembles and spits out a champion, or an
 * Unraveling Storm splits the sky. One event at a time; the boss bar shows how it's going.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class WorldEvents {
    public static final String CRIMSON_TAG = "tethered_crimson";

    @Nullable
    private static Active active;

    private static final class Active {
        final EventDef def;
        final int age;
        int ticks;
        int wave;
        int spawnedThisWave;
        final List<UUID> mobs = new ArrayList<>();
        final ServerBossEvent bar;

        Active(EventDef def, int age) {
            this.def = def;
            this.age = age;
            this.bar = Bosses.newBar(Component.literal(def.name()).withStyle(s -> s.withColor(def.color())),
                    BossEvent.BossBarColor.PURPLE);
        }
    }

    @Nullable
    public static String activeId() {
        return active == null ? null : active.def.id();
    }

    // ------------------------------------------------------------------ scheduling

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        TetherData data = TetherData.get(server);
        if (data.heartPos() == null) return; // not a Tethered world
        ServerLevel level = server.overworld();
        if (active != null) {
            tick(server, level, active);
            return;
        }
        if (!TetherConfig.EVENTS_ENABLED.get() || level.players().isEmpty()) return;
        long now = level.getGameTime();
        if (now - data.lastEventEnd() < TetherConfig.EVENT_GAP_MINUTES.get() * 1200L) return;

        long dayTime = level.getDayTime() % 24000L;
        long day = level.getDayTime() / 24000L;
        RandomSource random = level.random;
        if (dayTime >= 12600 && dayTime < 13600 && data.lastNightRolled() != day) {
            data.setLastNightRolled(day);
            if (random.nextDouble() < TetherConfig.NIGHT_EVENT_CHANCE.get()) startRandom(server, "night");
        } else if (dayTime < 11500 && server.getTickCount() % 3600 == 0
                && random.nextDouble() < TetherConfig.DAY_EVENT_CHANCE.get()) {
            startRandom(server, "day");
        }
    }

    private static void startRandom(MinecraftServer server, String time) {
        int age = TetherData.get(server).age();
        List<EventDef> options = new ArrayList<>();
        int total = 0;
        for (EventDef def : EventLoader.all()) {
            if (def.minAge() > age || def.weight() <= 0) continue;
            if (!def.time().equals("any") && !def.time().equals(time)) continue;
            if (!canRun(server, def)) continue;
            options.add(def);
            total += def.weight();
        }
        if (options.isEmpty()) return;
        int r = server.overworld().random.nextInt(total);
        for (EventDef def : options) {
            r -= def.weight();
            if (r < 0) {
                start(server, def);
                return;
            }
        }
    }

    private static boolean canRun(MinecraftServer server, EventDef def) {
        if ("heart_tremor".equals(def.type())) {
            BlockPos core = Heartstone.core(server);
            return core != null && server.overworld().players().stream().anyMatch(p -> p.blockPosition().distSqr(core) < 48 * 48);
        }
        return true;
    }

    /** Starts an event now (also used by /tether event). */
    public static boolean start(MinecraftServer server, EventDef def) {
        if (active != null) return false;
        TetherData data = TetherData.get(server);
        ServerLevel level = server.overworld();
        Active a = new Active(def, data.age());
        active = a;
        Component title = Component.literal(def.name()).withStyle(s -> s.withColor(def.color()));
        Component sub = Component.literal(def.warning()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Ending.title(p, title, sub);
            p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.5f, 0.7f);
            if (!def.warning().isEmpty()) p.sendSystemMessage(Component.literal(def.warning()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        switch (def.type()) {
            case "hollow_tide" -> spawnWave(level, a);
            case "wandering_weaver" -> {
                spawnTrader(level, a);
                finish(server, a, true);
            }
            case "heart_tremor" -> {
                if (!spawnChampion(server, level, a)) finish(server, a, false);
            }
            case "unraveling_storm" -> {
                level.setWeatherParameters(0, Math.max(200, def.duration() * 20), true, true);
                if (a.age >= 3) {
                    for (ServerPlayer p : level.players()) {
                        Mob m = Bosses.spawnMob(level, EntityType.PHANTOM, p.getX(), p.getY() + 12, p.getZ());
                        if (m != null) {
                            hollow(m, a.age);
                            level.addFreshEntity(m);
                        }
                    }
                }
            }
            case "threadfall", "crimson_weave" -> { }
            default -> {
                Tethered.LOGGER.warn("Unknown event type {}", def.type());
                finish(server, a, false);
            }
        }
        return true;
    }

    public static void stop(MinecraftServer server) {
        if (active != null) finish(server, active, false);
    }

    // ------------------------------------------------------------------ running

    private static void tick(MinecraftServer server, ServerLevel level, Active a) {
        a.ticks += 20;
        int seconds = a.ticks / 20;
        if (level.players().isEmpty()) {
            finish(server, a, false);
            return;
        }
        for (ServerPlayer p : level.players()) if (!a.bar.getPlayers().contains(p)) a.bar.addPlayer(p);
        RandomSource random = level.random;
        switch (a.def.type()) {
            case "hollow_tide" -> {
                List<Entity> alive = Bosses.alive(level, a.mobs);
                for (Entity e : alive) if (e instanceof Mob m) Bosses.keepHunting(level, m, 40);
                a.bar.setName(Component.literal(a.def.name() + "  ·  " + (a.wave) + "/" + a.def.waves()));
                a.bar.setProgress(a.spawnedThisWave == 0 ? 0f : alive.size() / (float) a.spawnedThisWave);
                if (alive.isEmpty()) {
                    if (a.wave >= a.def.waves()) {
                        finish(server, a, true);
                    } else {
                        spawnWave(level, a);
                    }
                } else if (seconds > Math.max(120, a.def.duration())) {
                    for (Entity e : alive) e.discard();
                    finish(server, a, false);
                }
            }
            case "threadfall" -> {
                a.bar.setProgress(1f - Math.min(1f, seconds / (float) a.def.duration()));
                if (seconds % 2 == 0) {
                    for (ServerPlayer p : level.players()) dropThread(level, p, a, random);
                }
                if (seconds >= a.def.duration()) finish(server, a, true);
            }
            case "crimson_weave" -> {
                a.bar.setColor(BossEvent.BossBarColor.RED);
                a.bar.setProgress(1f - Math.min(1f, seconds / (float) a.def.duration()));
                if (seconds % 20 == 0) {
                    for (ServerPlayer p : level.players()) {
                        for (int i = 0; i < 1 + random.nextInt(2); i++) spawnHollow(level, a, p, true);
                    }
                }
                for (Entity e : Bosses.alive(level, a.mobs)) if (e instanceof Mob m) Bosses.keepHunting(level, m, 32);
                if (seconds >= a.def.duration()) finish(server, a, true);
            }
            case "heart_tremor" -> {
                List<Entity> alive = Bosses.alive(level, a.mobs);
                if (alive.isEmpty()) {
                    finish(server, a, true);
                    return;
                }
                if (alive.get(0) instanceof LivingEntity champ) {
                    a.bar.setProgress(champ.getHealth() / champ.getMaxHealth());
                    if (champ instanceof Mob m) Bosses.keepHunting(level, m, 48);
                }
                if (seconds > Math.max(120, a.def.duration())) {
                    for (Entity e : alive) e.discard();
                    finish(server, a, false);
                }
            }
            case "unraveling_storm" -> {
                a.bar.setProgress(1f - Math.min(1f, seconds / (float) a.def.duration()));
                if (seconds % 6 == 0) {
                    for (ServerPlayer p : level.players()) strike(level, p, random);
                }
                if (seconds >= a.def.duration()) {
                    level.setWeatherParameters(6000, 0, false, false);
                    finish(server, a, true);
                }
            }
            default -> finish(server, a, false);
        }
    }

    private static void finish(MinecraftServer server, Active a, boolean success) {
        if (active == a) active = null;
        a.bar.removeAllPlayers();
        TetherData data = TetherData.get(server);
        ServerLevel level = server.overworld();
        data.setLastEventEnd(level.getGameTime());
        if (!success) {
            if (!a.def.fail().isEmpty()) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.sendSystemMessage(Component.literal(a.def.fail()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                }
            }
            return;
        }
        data.countEvent(a.def.type());
        RandomSource random = level.random;
        for (ServerPlayer p : level.players()) {
            if (!a.def.success().isEmpty()) {
                p.sendSystemMessage(Component.literal(a.def.success()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            int threads = a.def.threadsMin() + (a.def.threadsMax() > a.def.threadsMin()
                    ? random.nextInt(a.def.threadsMax() - a.def.threadsMin() + 1) : 0);
            if (threads > 0) give(p, new ItemStack(ModRegistry.LOST_THREAD.get(), threads));
            for (ItemStack stack : Heartstone.rollCache(a.age, random, a.def.cacheRolls())) {
                if (a.def.cacheRolls() > 0) give(p, stack);
            }
            if (random.nextDouble() < a.def.favorChance()) {
                give(p, new ItemStack(ModRegistry.WEAVERS_FAVOR.get()));
                p.sendSystemMessage(Component.translatable("msg.tethered.event.favor").withStyle(ChatFormatting.GOLD));
            }
            if (!"wandering_weaver".equals(a.def.type())) Advancements.award(p, "event");
        }
        QuestManager.markDirty();
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    // ------------------------------------------------------------------ pieces

    @Nullable
    private static EntityType<?> pickMob(Active a, RandomSource random) {
        int total = 0;
        for (EventDef.Mob m : a.def.mobs()) if (m.minAge() <= a.age) total += m.weight();
        if (total <= 0) return null;
        int r = random.nextInt(total);
        for (EventDef.Mob m : a.def.mobs()) {
            if (m.minAge() > a.age) continue;
            r -= m.weight();
            if (r < 0) return m.type();
        }
        return null;
    }

    private static void hollow(Mob mob, int age) {
        mob.addTag(Bosses.HOLLOW_TAG);
        mob.setPersistenceRequired();
        var hp = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * (1.0 + 0.12 * age));
            mob.setHealth(mob.getMaxHealth());
        }
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.setCustomName(Component.translatable("event.tethered.hollow", mob.getType().getDescription())
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static boolean spawnHollow(ServerLevel level, Active a, ServerPlayer p, boolean crimson) {
        EntityType<?> type = pickMob(a, level.random);
        if (type == null) return false;
        BlockPos spot = Bosses.findSpot(level, p.blockPosition(), 6, 14, 30);
        if (spot == null) return false;
        Mob mob = Bosses.spawnMob(level, type, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        if (mob == null) return false;
        hollow(mob, a.age);
        if (crimson) {
            mob.addTag(CRIMSON_TAG);
            crimsonBuff(mob);
        }
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 20, 0.3, 0.8, 0.3, 0.05);
        a.mobs.add(mob.getUUID());
        return true;
    }

    private static void spawnWave(ServerLevel level, Active a) {
        a.wave++;
        a.mobs.clear();
        a.spawnedThisWave = 0;
        int perPlayer = a.def.perWave() + (int) Math.floor(a.age * a.def.perAge());
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            for (int i = 0; i < perPlayer; i++) if (spawnHollow(level, a, p, false)) a.spawnedThisWave++;
        }
        for (ServerPlayer p : level.players()) {
            p.displayClientMessage(Component.translatable("msg.tethered.event.wave", a.wave, a.def.waves()).withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    private static void dropThread(ServerLevel level, ServerPlayer p, Active a, RandomSource random) {
        ItemStack stack;
        if (random.nextFloat() < 0.6f) {
            stack = new ItemStack(ModRegistry.LOST_THREAD.get(), 1 + random.nextInt(2));
        } else {
            List<ItemStack> roll = Heartstone.rollCache(a.age, random, 1);
            stack = roll.isEmpty() ? new ItemStack(ModRegistry.LOST_THREAD.get()) : roll.get(0);
        }
        double x = p.getX() + random.nextInt(13) - 6;
        double z = p.getZ() + random.nextInt(13) - 6;
        double y = p.getY() + 3 + random.nextInt(3);
        ItemEntity item = new ItemEntity(level, x, y, z, stack);
        item.setGlowingTag(true);
        level.addFreshEntity(item);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.1, 0.4, 0.1, 0.02);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_CLUSTER_FALL, SoundSource.AMBIENT, 0.6f, 1.6f);
    }

    private static void spawnTrader(ServerLevel level, Active a) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        ServerPlayer p = players.get(level.random.nextInt(players.size()));
        BlockPos spot = Bosses.findSpot(level, p.blockPosition(), 2, 5, 30);
        if (spot == null) spot = p.blockPosition();
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null) return;
        trader.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
        trader.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        trader.getOffers().clear();
        for (EventDef.Trade t : a.def.trades()) {
            if (t.minAge() > a.age) continue;
            trader.getOffers().add(new MerchantOffer(new ItemCost(t.cost(), t.costCount()),
                    new ItemStack(t.result(), t.count()), t.maxUses(), 2, 0.05f));
        }
        trader.setDespawnDelay(Math.max(2400, a.def.duration() * 20));
        trader.setCustomName(Component.translatable("event.tethered.weaver_name").withStyle(ChatFormatting.LIGHT_PURPLE));
        level.addFreshEntity(trader);
        level.sendParticles(ParticleTypes.ENCHANT, spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 60, 0.5, 1, 0.5, 0.5);
    }

    private static boolean spawnChampion(MinecraftServer server, ServerLevel level, Active a) {
        BlockPos core = Heartstone.core(server);
        AgeDef.Trial base = a.def.champion();
        EntityType<?> type = pickMob(a, level.random);
        if (core == null || base == null || type == null) return false;
        AgeDef.Trial t = new AgeDef.Trial(base.name(), type, base.health() * (1 + 0.25 * a.age), base.damage(),
                base.scale(), base.armor() + a.age, base.speed(), List.of(), base.effects(), base.equipment(), base.intro());
        BlockPos spot = Bosses.findSpot(level, core.above(), 3, 7, 40);
        if (spot == null) spot = core.above(2);
        Mob mob = Bosses.spawnBoss(level, spot, t, 1.0);
        if (mob == null) return false;
        level.playSound(null, core, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1f, 1.2f);
        a.mobs.add(mob.getUUID());
        return true;
    }

    private static void strike(ServerLevel level, ServerPlayer p, RandomSource random) {
        if (!level.canSeeSky(p.blockPosition())) return;
        int x = p.getBlockX() + random.nextInt(25) - 12;
        int z = p.getBlockZ() + random.nextInt(25) - 12;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight()) y = p.getBlockY();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(x + 0.5, y, z + 0.5);
        level.addFreshEntity(bolt);
        if (random.nextFloat() < 0.5f) {
            ItemEntity item = new ItemEntity(level, x + 0.5, y + 0.5, z + 0.5, new ItemStack(ModRegistry.LOST_THREAD.get()));
            item.setGlowingTag(true);
            item.setInvulnerable(true);
            level.addFreshEntity(item);
        }
    }

    // ------------------------------------------------------------------ Crimson Weave hooks

    private static void crimsonBuff(LivingEntity mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, true));
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        Active a = active;
        if (a == null || !"crimson_weave".equals(a.def.type())) return;
        if (!(event.getEntity() instanceof Enemy) || event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (event.getLevel().getLevel().dimension() != Level.OVERWORLD) return;
        event.getEntity().addTag(CRIMSON_TAG);
        crimsonBuff(event.getEntity());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !dead.getTags().contains(CRIMSON_TAG)) return;
        if (level.random.nextFloat() < 0.3f) {
            dead.spawnAtLocation(new ItemStack(ModRegistry.LOST_THREAD.get()));
        }
    }

    private WorldEvents() {}
}
