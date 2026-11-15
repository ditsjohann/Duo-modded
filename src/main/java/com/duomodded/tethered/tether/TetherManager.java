package com.duomodded.tethered.tether;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.network.TetherActionPayload;
import com.duomodded.tethered.network.TetherStatePayload;
import com.duomodded.tethered.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the rope. Movement is client-authoritative in Minecraft, so the actual pulling
 * happens on each client (see RopePhysics). The server handles: reeling (Haul), catching falls,
 * dimension travel, the downed/revive system, special moves and syncing state to clients.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class TetherManager {
    private static final Map<UUID, Integer> DOWNED = new HashMap<>();
    private static final Map<UUID, Integer> REVIVE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_CAUGHT = new HashMap<>();
    private static final Map<UUID, Integer> LAUNCHED = new HashMap<>();
    private static final Map<UUID, Integer> ENDER_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> DANGLE = new HashMap<>();

    private TetherManager() {}

    // ---------------------------------------------------------------- helpers

    public static boolean isDowned(UUID id) {
        return DOWNED.containsKey(id);
    }

    @Nullable
    public static ServerPlayer partnerOf(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        TetherData.Link link = TetherData.get(server).getLink(player.getUUID());
        return link == null ? null : server.getPlayerList().getPlayer(link.other(player.getUUID()));
    }

    public static void pair(ServerPlayer a, ServerPlayer b, RopeTier tier) {
        MinecraftServer server = a.getServer();
        if (server == null || a == b) return;
        TetherData data = TetherData.get(server);
        data.link(a.getUUID(), b.getUUID(), tier.level);
        data.rememberName(a.getUUID(), a.getGameProfile().getName());
        data.rememberName(b.getUUID(), b.getGameProfile().getName());
        a.sendSystemMessage(Component.translatable("msg.tethered.paired", b.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        b.sendSystemMessage(Component.translatable("msg.tethered.paired", a.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        a.sendSystemMessage(Component.translatable("msg.tethered.paired_hint").withStyle(ChatFormatting.GRAY));
        b.sendSystemMessage(Component.translatable("msg.tethered.paired_hint").withStyle(ChatFormatting.GRAY));
        Advancements.award(a, "paired");
        Advancements.award(b, "paired");
        sync(a);
        sync(b);
    }

    public static void cut(MinecraftServer server, UUID id) {
        TetherData.Link link = TetherData.get(server).unlink(id);
        if (link == null) return;
        for (UUID uuid : new UUID[]{link.a, link.b}) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) sync(p);
        }
    }

    private static int now(MinecraftServer server) {
        return server.getTickCount();
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        TetherData data = TetherData.get(server);
        int now = now(server);

        for (TetherData.Link link : data.uniqueLinks()) {
            ServerPlayer a = server.getPlayerList().getPlayer(link.a);
            ServerPlayer b = server.getPlayerList().getPlayer(link.b);
            if (a == null || b == null) {
                link.hauling.clear();
                continue;
            }
            tickLink(link, a, b, now);
        }

        tickDowned(server);

        if (now % 4 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                sync(p);
            }
        }
        if (now % 200 == 0) {
            LAUNCHED.values().removeIf(t -> now - t > 200);
            LAST_CAUGHT.values().removeIf(t -> now - t > 200);
        }
    }

    private static void tickLink(TetherData.Link link, ServerPlayer a, ServerPlayer b, int now) {
        float max = link.maxLength();

        // Reel in while someone standing on the ground holds Haul, otherwise slowly let out.
        boolean hauling = false;
        for (UUID id : link.hauling) {
            ServerPlayer h = id.equals(a.getUUID()) ? a : b;
            if (h.onGround() && !isDowned(id)) hauling = true;
        }
        if (hauling) {
            link.currentLength = Math.max(2f, link.currentLength - 0.15f);
        } else {
            link.currentLength = Math.min(max, link.currentLength + 0.08f);
        }
        if (link.currentLength > max) link.currentLength = max;

        if (link.slack || a.isSpectator() || b.isSpectator()) return;

        // Different dimensions (e.g. logged in elsewhere): bring b to a.
        if (a.level() != b.level()) {
            b.teleportTo(a.serverLevel(), a.getX(), a.getY(), a.getZ(), b.getYRot(), b.getXRot());
            b.resetFallDistance();
            return;
        }

        double dist = a.position().distanceTo(b.position());

        // Way too far (lag, ender pearl, teleport command): snap the airborne one back.
        if (dist > link.currentLength + 12) {
            ServerPlayer mover = (!b.onGround() || a.onGround()) ? b : a;
            ServerPlayer anchor = mover == b ? a : b;
            mover.teleportTo(anchor.serverLevel(), anchor.getX(), anchor.getY(), anchor.getZ(), mover.getYRot(), mover.getXRot());
            mover.resetFallDistance();
            return;
        }

        boolean taut = dist >= link.currentLength - 0.5;
        checkCaught(a, b, taut, now);
        checkCaught(b, a, taut, now);
    }

    /** If the rope is holding p up from above, cancel their fall damage. */
    private static void checkCaught(ServerPlayer p, ServerPlayer holder, boolean taut, int now) {
        if (taut && !p.onGround() && holder.getY() > p.getY() + 0.5) {
            p.resetFallDistance();
            LAST_CAUGHT.put(p.getUUID(), now);
            if (holder.onGround() && holder.getY() - p.getY() > 2) {
                int ticks = DANGLE.merge(p.getUUID(), 1, Integer::sum);
                if (ticks == 40) Advancements.award(p, "hanging_by_a_thread");
            }
        } else if (p.onGround()) {
            DANGLE.remove(p.getUUID());
        }
    }

    // ---------------------------------------------------------------- downed / revive

    private static void tickDowned(MinecraftServer server) {
        for (UUID id : new ArrayList<>(DOWNED.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) {
                DOWNED.remove(id);
                REVIVE.remove(id);
                continue;
            }
            int left = DOWNED.get(id) - 1;
            ServerPlayer partner = partnerOf(p);
            boolean reviving = partner != null && partner.isAlive() && !isDowned(partner.getUUID())
                    && partner.level() == p.level() && partner.distanceTo(p) < 2.5f && partner.isShiftKeyDown();

            if (reviving) {
                left++; // timer pauses while being revived
                int progress = REVIVE.merge(id, 1, Integer::sum);
                if (progress >= TetherConfig.REVIVE_SECONDS.get() * 20) {
                    revive(p);
                    partner.sendSystemMessage(Component.translatable("msg.tethered.you_revived", p.getDisplayName()).withStyle(ChatFormatting.GREEN));
                    Advancements.award(partner, "saved_you");
                    continue;
                }
            } else {
                REVIVE.remove(id);
            }

            if (left <= 0) {
                p.kill(); // our death handler sees the downed entry and lets the death happen
                DOWNED.remove(id);
                REVIVE.remove(id);
                continue;
            }
            DOWNED.put(id, left);
            if (left % 20 == 0) applyDownedEffects(p);
        }
    }

    private static void applyDownedEffects(ServerPlayer p) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 9, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 4, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
    }

    private static void revive(ServerPlayer p) {
        DOWNED.remove(p.getUUID());
        REVIVE.remove(p.getUUID());
        p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        p.removeEffect(MobEffects.WEAKNESS);
        p.removeEffect(MobEffects.DIG_SLOWDOWN);
        p.removeEffect(MobEffects.GLOWING);
        p.setHealth(Math.min(p.getMaxHealth(), 6f));
        p.sendSystemMessage(Component.translatable("msg.tethered.revived").withStyle(ChatFormatting.GREEN));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();

        if (DOWNED.remove(id) != null) {
            // Dying for real while downed.
            REVIVE.remove(id);
            return;
        }
        if (!TetherConfig.DOWNED_ENABLED.get()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // void, /kill

        ServerPlayer partner = partnerOf(p);
        if (partner == null || !partner.isAlive() || partner.isSpectator() || isDowned(partner.getUUID())) return;

        event.setCanceled(true);
        p.setHealth(1f);
        DOWNED.put(id, TetherConfig.DOWNED_SECONDS.get() * 20);
        applyDownedEffects(p);
        p.sendSystemMessage(Component.translatable("msg.tethered.you_down").withStyle(ChatFormatting.RED));
        partner.sendSystemMessage(Component.translatable("msg.tethered.partner_down", p.getDisplayName()).withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Integer left = DOWNED.get(p.getUUID());
        if (left == null || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        // While downed, hits drain the timer (1 second per point of damage) instead of health.
        event.setCanceled(true);
        DOWNED.put(p.getUUID(), Math.max(1, left - (int) (event.getAmount() * 20)));
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isDowned(event.getEntity().getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        int now = now(p.getServer());
        Integer launched = LAUNCHED.remove(p.getUUID());
        Integer caught = LAST_CAUGHT.get(p.getUUID());
        if ((launched != null && now - launched < 200) || (caught != null && now - caught < 15)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- player lifecycle

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        MinecraftServer server = p.getServer();
        TetherData data = TetherData.get(server);
        data.rememberName(p.getUUID(), p.getGameProfile().getName());
        Advancements.award(p, "root");

        if (data.getLink(p.getUUID()) == null && TetherConfig.AUTO_PAIR.get()) {
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (other != p && data.getLink(other.getUUID()) == null) {
                    pair(p, other, RopeTier.TWINE);
                    break;
                }
            }
        }
        if (data.getLink(p.getUUID()) == null) {
            p.sendSystemMessage(Component.translatable("msg.tethered.waiting").withStyle(ChatFormatting.GRAY));
        }
        Ending.offerAll(server);
        sync(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        TetherData.Link link = TetherData.get(p.getServer()).getLink(p.getUUID());
        if (link != null) {
            link.hauling.remove(p.getUUID());
            link.choices.clear();
        }
        DANGLE.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        TetherData.Link link = TetherData.get(p.getServer()).getLink(p.getUUID());
        ServerPlayer partner = partnerOf(p);
        if (link == null || link.slack || partner == null || partner.isSpectator()) return;
        if (partner.level().dimension() == event.getTo()) return;

        // Pull the partner through on the next tick (teleporting inside the event is risky).
        p.getServer().execute(() -> {
            if (p.isAlive() && partner.isAlive() && partner.level() != p.level()) {
                partner.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), partner.getYRot(), partner.getXRot());
                partner.resetFallDistance();
                partner.displayClientMessage(Component.translatable("msg.tethered.pulled_through", p.getDisplayName()), true);
            }
        });
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        ServerPlayer partner = partnerOf(p);
        if (partner != null && partner.isAlive() && !isDowned(partner.getUUID()) && !partner.isSpectator()) {
            p.teleportTo(partner.serverLevel(), partner.getX(), partner.getY(), partner.getZ(), p.getYRot(), p.getXRot());
            p.resetFallDistance();
        }
    }

    // ---------------------------------------------------------------- key actions

    public static void onAction(ServerPlayer p, TetherActionPayload action) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        TetherData.Link link = data.getLink(p.getUUID());
        if (link == null) return;
        ServerPlayer partner = server.getPlayerList().getPlayer(link.other(p.getUUID()));

        switch (action.action()) {
            case TetherActionPayload.HAUL -> {
                if (action.down() && !isDowned(p.getUUID())) link.hauling.add(p.getUUID());
                else link.hauling.remove(p.getUUID());
            }
            case TetherActionPayload.LAUNCH -> launch(p, partner, server);
            case TetherActionPayload.ENDER_CALL -> enderCall(p, partner, link, server);
            case TetherActionPayload.UNHOOK -> unhook(p, partner, link, data);
            default -> { }
        }
    }

    private static void launch(ServerPlayer p, @Nullable ServerPlayer partner, MinecraftServer server) {
        if (partner == null || isDowned(p.getUUID())) return;
        if (!p.isShiftKeyDown() || !p.onGround()) {
            p.displayClientMessage(Component.translatable("msg.tethered.launch_brace"), true);
            return;
        }
        if (partner.level() != p.level() || partner.distanceTo(p) > 4f) {
            p.displayClientMessage(Component.translatable("msg.tethered.launch_far"), true);
            return;
        }
        Vec3 velocity = p.getLookAngle().scale(TetherConfig.LAUNCH_POWER.get()).add(0, 0.45, 0);
        partner.setDeltaMovement(velocity);
        partner.hurtMarked = true;
        partner.connection.send(new ClientboundSetEntityMotionPacket(partner));
        LAUNCHED.put(partner.getUUID(), now(server));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
        Advancements.award(p, "slingshot");
    }

    private static void enderCall(ServerPlayer p, @Nullable ServerPlayer partner, TetherData.Link link, MinecraftServer server) {
        if (link.tier < RopeTier.ENDER.level) {
            p.displayClientMessage(Component.translatable("msg.tethered.ender_needed"), true);
            return;
        }
        if (partner == null || !partner.isAlive()) return;
        int now = now(server);
        int cooldown = TetherConfig.ENDER_CALL_COOLDOWN.get() * 20;
        Integer last = ENDER_COOLDOWN.get(p.getUUID());
        if (last != null && now - last < cooldown) {
            int secs = (cooldown - (now - last)) / 20 + 1;
            p.displayClientMessage(Component.translatable("msg.tethered.ender_cooldown", secs), true);
            return;
        }
        ENDER_COOLDOWN.put(p.getUUID(), now);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        p.teleportTo(partner.serverLevel(), partner.getX(), partner.getY(), partner.getZ(), p.getYRot(), p.getXRot());
        p.resetFallDistance();
        partner.serverLevel().playSound(null, partner.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
    }

    private static void unhook(ServerPlayer p, @Nullable ServerPlayer partner, TetherData.Link link, TetherData data) {
        if (link.tier < RopeTier.GOLDEN.level) {
            p.displayClientMessage(Component.translatable("msg.tethered.unhook_needed"), true);
            return;
        }
        link.slack = !link.slack;
        data.setDirty();
        Component msg = Component.translatable(link.slack ? "msg.tethered.unhooked" : "msg.tethered.rehooked").withStyle(ChatFormatting.GOLD);
        p.displayClientMessage(msg, true);
        if (partner != null) partner.displayClientMessage(msg, true);
    }

    // ---------------------------------------------------------------- sync

    public static void sync(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        TetherData.Link link = data.getLink(p.getUUID());
        if (link == null) {
            PacketDistributor.sendToPlayer(p, TetherStatePayload.NONE);
            return;
        }
        UUID otherId = link.other(p.getUUID());
        ServerPlayer q = server.getPlayerList().getPlayer(otherId);
        boolean online = q != null;
        boolean same = online && q.level() == p.level();
        boolean selfDowned = isDowned(p.getUUID());
        boolean partnerDowned = online && isDowned(otherId);
        int revive = selfDowned ? REVIVE.getOrDefault(p.getUUID(), 0) : (partnerDowned ? REVIVE.getOrDefault(otherId, 0) : 0);

        TetherStatePayload state = new TetherStatePayload(
                true,
                otherId,
                online ? q.getGameProfile().getName() : data.nameOf(otherId),
                online,
                link.tier,
                link.currentLength,
                link.maxLength(),
                link.slack,
                online && q.isShiftKeyDown() && q.onGround(),
                partnerDowned,
                online ? q.getHealth() : 0f,
                online ? q.getMaxHealth() : 20f,
                same,
                online ? q.getX() : 0,
                online ? q.getY() : 0,
                online ? q.getZ() : 0,
                selfDowned,
                DOWNED.getOrDefault(p.getUUID(), 0),
                revive,
                Math.max(1, TetherConfig.REVIVE_SECONDS.get() * 20));
        PacketDistributor.sendToPlayer(p, state);
    }
}
