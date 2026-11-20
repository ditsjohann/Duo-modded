package com.duomodded.tethered.boon;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.age.Bosses;
import com.duomodded.tethered.network.BoonOfferPayload;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boons: the RPG side. Every Weaver's Favor (from each new Age, Trials, quests and events) lets a
 * player pick one of three random Boons. Each Boon has a few ranks. Some change attributes, the
 * rest are special effects handled by the events below. Boons are per player and saved in the world.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class Boons {

    public record Boon(String id, int maxRank, @Nullable Holder<Attribute> attribute, double perRank,
                       AttributeModifier.Operation op) {
        public ResourceLocation modifierId() {
            return Tethered.id("boon/" + id);
        }
    }

    public static final Map<String, Boon> ALL = new LinkedHashMap<>();

    private static void add(String id, int max, @Nullable Holder<Attribute> attr, double per, AttributeModifier.Operation op) {
        ALL.put(id, new Boon(id, max, attr, per, op));
    }

    private static void special(String id, int max) {
        add(id, max, null, 0, AttributeModifier.Operation.ADD_VALUE);
    }

    static {
        add("vitality", 5, Attributes.MAX_HEALTH, 4, AttributeModifier.Operation.ADD_VALUE);
        add("might", 5, Attributes.ATTACK_DAMAGE, 1, AttributeModifier.Operation.ADD_VALUE);
        add("quarrying", 5, Attributes.BLOCK_BREAK_SPEED, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add("swiftness", 3, Attributes.MOVEMENT_SPEED, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add("long_arms", 3, Attributes.BLOCK_INTERACTION_RANGE, 1, AttributeModifier.Operation.ADD_VALUE);
        add("ironhide", 5, Attributes.ARMOR, 2, AttributeModifier.Operation.ADD_VALUE);
        add("fortune", 3, Attributes.LUCK, 1, AttributeModifier.Operation.ADD_VALUE);
        add("featherfall", 3, Attributes.SAFE_FALL_DISTANCE, 3, AttributeModifier.Operation.ADD_VALUE);
        add("deep_lungs", 3, Attributes.OXYGEN_BONUS, 1, AttributeModifier.Operation.ADD_VALUE);
        add("steadfast", 3, Attributes.KNOCKBACK_RESISTANCE, 0.15, AttributeModifier.Operation.ADD_VALUE);
        add("stride", 1, Attributes.STEP_HEIGHT, 0.5, AttributeModifier.Operation.ADD_VALUE);
        special("twin_strike", 3);
        special("lifeline", 2);
        special("heart_echo", 4);
        special("bloodthread", 3);
        special("nightsight", 1);
        special("hearty", 3);
        special("scholar", 3);
        special("hollowbane", 3);
        special("threadfinder", 2);
    }

    // ------------------------------------------------------------------ ranks

    public static int rank(ServerPlayer p, String id) {
        MinecraftServer server = p.getServer();
        if (server == null) return 0;
        return TetherData.get(server).boons(p.getUUID()).getOrDefault(id, 0);
    }

    /** Puts the attribute Boons on the player (after login, respawn or a new pick). */
    public static void apply(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        Map<String, Integer> ranks = TetherData.get(server).boons(p.getUUID());
        for (Boon boon : ALL.values()) {
            if (boon.attribute() == null) continue;
            AttributeInstance inst = p.getAttribute(boon.attribute());
            if (inst == null) continue;
            int r = ranks.getOrDefault(boon.id(), 0);
            if (r <= 0) {
                inst.removeModifier(boon.modifierId());
            } else {
                inst.addOrUpdateTransientModifier(new AttributeModifier(boon.modifierId(), boon.perRank() * r, boon.op()));
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    // ------------------------------------------------------------------ offers

    /** Using a Weaver's Favor: show three Boons (the same three until one is picked). */
    public static void openOffer(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        List<String> offer = data.boonOffer(p.getUUID());
        if (offer == null || offer.isEmpty()) {
            offer = roll(data.boons(p.getUUID()), p.getRandom());
            if (offer.isEmpty()) {
                p.displayClientMessage(Component.translatable("msg.tethered.boon.all_max").withStyle(ChatFormatting.GOLD), true);
                return;
            }
            data.setBoonOffer(p.getUUID(), offer);
        }
        List<Integer> ranks = new ArrayList<>();
        for (String id : offer) ranks.add(data.boons(p.getUUID()).getOrDefault(id, 0));
        List<Integer> max = new ArrayList<>();
        for (String id : offer) max.add(ALL.containsKey(id) ? ALL.get(id).maxRank() : 1);
        PacketDistributor.sendToPlayer(p, new BoonOfferPayload(offer, ranks, max));
    }

    private static List<String> roll(Map<String, Integer> ranks, RandomSource random) {
        List<String> open = new ArrayList<>();
        for (Boon b : ALL.values()) if (ranks.getOrDefault(b.id(), 0) < b.maxRank()) open.add(b.id());
        Collections.shuffle(open, new java.util.Random(random.nextLong()));
        return new ArrayList<>(open.subList(0, Math.min(3, open.size())));
    }

    /** The player clicked one of the offered Boons. */
    public static void choose(ServerPlayer p, String id) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        List<String> offer = data.boonOffer(p.getUUID());
        Boon boon = ALL.get(id);
        if (offer == null || !offer.contains(id) || boon == null) return;
        if (!takeFavor(p)) {
            p.displayClientMessage(Component.translatable("msg.tethered.boon.no_favor").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Map<String, Integer> ranks = data.boons(p.getUUID());
        int r = Math.min(boon.maxRank(), ranks.getOrDefault(id, 0) + 1);
        ranks.put(id, r);
        data.setBoonOffer(p.getUUID(), null);
        data.setDirty();
        apply(p);
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.5f);
        Component name = Component.translatable("boon.tethered." + id).withStyle(ChatFormatting.GOLD);
        p.sendSystemMessage(Component.translatable("msg.tethered.boon.chosen", name, r).withStyle(ChatFormatting.LIGHT_PURPLE));
        ServerPlayer partner = TetherManager.partnerOf(p);
        if (partner != null) {
            partner.sendSystemMessage(Component.translatable("msg.tethered.boon.partner_chose", p.getDisplayName(), name)
                    .withStyle(ChatFormatting.GRAY));
        }
        Advancements.award(p, "boon");
        QuestManager.markDirty();
    }

    private static boolean takeFavor(ServerPlayer p) {
        if (p.getAbilities().instabuild) return true;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModRegistry.WEAVERS_FAVOR.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) apply(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            apply(p);
            p.setHealth(p.getMaxHealth());
        }
    }

    // ------------------------------------------------------------------ special Boons

    private static boolean partnerNear(ServerPlayer p, float range) {
        ServerPlayer partner = TetherManager.partnerOf(p);
        return partner != null && partner.isAlive() && partner.level() == p.level() && partner.distanceTo(p) <= range;
    }

    /** Twin Strike (partner close by) and Hollowbane (story mobs) make your hits stronger. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        float mult = 1f;
        int twin = rank(p, "twin_strike");
        if (twin > 0 && partnerNear(p, 16f)) mult += 0.15f * twin;
        int bane = rank(p, "hollowbane");
        if (bane > 0 && event.getEntity().getTags().contains(Bosses.HOLLOW_TAG)) mult += 0.2f * bane;
        if (mult != 1f) event.setAmount(event.getAmount() * mult);
    }

    /** Bloodthread: killing a monster heals you (and your partner if close). */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !(event.getEntity() instanceof Enemy)) return;
        int r = rank(p, "bloodthread");
        if (r <= 0) return;
        p.heal(r);
        ServerPlayer partner = TetherManager.partnerOf(p);
        if (partner != null && partnerNear(p, 16f)) partner.heal(r);
    }

    /** Hearty: food fills you up more. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        int r = rank(p, "hearty");
        if (r > 0 && event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            p.getFoodData().eat(r, 0.3f);
        }
    }

    /** Scholar: more experience from orbs. */
    @SubscribeEvent
    public static void onXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        int r = rank(p, "scholar");
        if (r > 0) p.giveExperiencePoints(Math.max(1, Math.round(event.getOrb().getValue() * 0.2f * r)));
    }

    /** Nightsight: see in the dark inside the Last Stone. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 80 != 0) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (rank(p, "nightsight") <= 0) continue;
            Level level = p.level();
            if (level.getMaxLocalRawBrightness(p.blockPosition()) < 7) {
                p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
            }
        }
    }

    /** Lifeline: extra seconds while downed. */
    public static int lifelineTicks(ServerPlayer p) {
        return rank(p, "lifeline") * 10 * 20;
    }

    /** Threadfinder: extra Lost Threads from Thread Ore and Remnants. */
    public static int bonusThreads(@Nullable LivingEntity breaker, RandomSource random) {
        if (!(breaker instanceof ServerPlayer p)) return 0;
        int r = rank(p, "threadfinder");
        int n = 0;
        for (int i = 0; i < r; i++) if (random.nextFloat() < 0.35f) n++;
        return n;
    }

    public static Map<String, Integer> ranksOf(MinecraftServer server, UUID id) {
        return TetherData.get(server).boons(id);
    }

    private Boons() {}
}
