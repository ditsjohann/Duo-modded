package com.duomodded.tethered.age;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Spawning and running big named mobs: Trial guardians, Heart Tremor champions and Hollow mobs. */
public final class Bosses {
    /** Every mob that belongs to the story (Trial guardians, event mobs) gets this tag. */
    public static final String HOLLOW_TAG = "tethered_hollow";
    public static final String BOSS_TAG = "tethered_boss";

    @Nullable
    public static Mob spawnMob(ServerLevel level, EntityType<?> type, double x, double y, double z) {
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        mob.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        return mob;
    }

    @Nullable
    public static Mob spawnBoss(ServerLevel level, BlockPos pos, AgeDef.Trial t, double healthMultiplier) {
        Mob mob = spawnMob(level, t.entity(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (mob == null) return null;
        setBase(mob, Attributes.MAX_HEALTH, t.health() * healthMultiplier);
        multiply(mob, Attributes.ATTACK_DAMAGE, t.damage());
        if (t.scale() != 1.0) setBase(mob, Attributes.SCALE, t.scale());
        add(mob, Attributes.ARMOR, t.armor());
        multiply(mob, Attributes.MOVEMENT_SPEED, t.speed());
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(follow.getBaseValue(), 48));
        AttributeInstance kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.5));
        mob.setHealth(mob.getMaxHealth());
        for (Map.Entry<EquipmentSlot, Item> e : t.equipment().entrySet()) {
            mob.setItemSlot(e.getKey(), new ItemStack(e.getValue()));
            mob.setDropChance(e.getKey(), 0f);
        }
        for (Holder<MobEffect> effect : t.effects()) {
            mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        mob.setCustomName(Component.literal(t.name()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.addTag(BOSS_TAG);
        mob.addTag(HOLLOW_TAG);
        level.addFreshEntity(mob);
        return mob;
    }

    private static void setBase(Mob mob, Holder<Attribute> attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void multiply(Mob mob, Holder<Attribute> attr, double factor) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null && factor != 1.0) inst.setBaseValue(inst.getBaseValue() * factor);
    }

    private static void add(Mob mob, Holder<Attribute> attr, double amount) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null && amount != 0) inst.setBaseValue(inst.getBaseValue() + amount);
    }

    /** Keeps a mob angry at the nearest player (also works for neutral mobs like golems). */
    public static void keepHunting(ServerLevel level, Mob mob, double range) {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()))) return;
        Player nearest = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), range,
                e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive());
        if (nearest != null) mob.setTarget(nearest);
    }

    /** Shows the bar to players near the entity and hides it from everyone else. */
    public static void updateBar(ServerBossEvent bar, ServerLevel level, @Nullable LivingEntity entity, double range) {
        if (entity != null) bar.setProgress(Math.max(0f, Math.min(1f, entity.getHealth() / entity.getMaxHealth())));
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (entity == null || p.level() != level || p.distanceTo(entity) > range) bar.removePlayer(p);
        }
        if (entity == null) return;
        for (ServerPlayer p : level.players()) {
            if (p.distanceTo(entity) <= range && !bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    public static ServerBossEvent newBar(Component name, BossEvent.BossBarColor color) {
        ServerBossEvent bar = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.NOTCHED_10);
        bar.setDarkenScreen(false);
        return bar;
    }

    /** A free spot near a position: two blocks of air above something solid. */
    @Nullable
    public static BlockPos findSpot(ServerLevel level, BlockPos around, int minR, int maxR, int tries) {
        for (int i = 0; i < tries; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            int r = minR + level.random.nextInt(Math.max(1, maxR - minR + 1));
            int x = around.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = around.getZ() + (int) Math.round(Math.sin(angle) * r);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, around.getY() + dy, z);
                BlockState below = level.getBlockState(pos.below());
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && below.isSolidRender(level, pos.below())) {
                    return pos;
                }
            }
        }
        return null;
    }

    public static List<Entity> alive(ServerLevel level, List<java.util.UUID> ids) {
        List<Entity> out = new ArrayList<>();
        for (java.util.UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) out.add(e);
        }
        return out;
    }

    private Bosses() {}
}
