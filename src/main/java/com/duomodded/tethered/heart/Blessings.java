package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * What the Heart Tree gives back. Each stage unlocks one blessing for the whole world:
 * 1 Green Thumb, 2 Together, 3 Heart Paths, 4 Deep Roots, 5 Shared Table, 6 Heartward, 7 Bloom.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class Blessings {
    public static final int GREEN_THUMB = 1;   // crops near the tree grow faster
    public static final int TOGETHER = 2;      // regeneration while you stay close
    public static final int HEART_PATHS = 3;   // fast travel between the tree and attuned anchors
    public static final int DEEP_ROOTS = 4;    // +4 rope length
    public static final int SHARED_TABLE = 5;  // eating also feeds your partner a little
    public static final int HEARTWARD = 6;     // no monsters spawn near the tree
    public static final int BLOOM = 7;         // the way to Bloomreach and the Weaver's question

    public static final int COUNT = 7;

    public static float ropeBonus(int stage) {
        return stage >= DEEP_ROOTS ? 4f : 0f;
    }

    public static boolean has(MinecraftServer server, int blessing) {
        return TetherData.get(server).heartStage() >= blessing;
    }

    /** Green Thumb: called from the tree's ticker. Gives crops and saplings nearby extra growth ticks. */
    public static void growCrops(ServerLevel level, BlockPos tree, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            BlockPos pos = tree.offset(random.nextInt(49) - 24, random.nextInt(9) - 4, random.nextInt(49) - 24);
            BlockState state = level.getBlockState(pos);
            if (state.isRandomlyTicking() && state.getBlock() instanceof BonemealableBlock) {
                state.randomTick(level, pos, random);
            }
        }
    }

    /** Together: called every 100 ticks for each online pair. */
    public static void tickPair(MinecraftServer server, ServerPlayer a, ServerPlayer b) {
        if (!has(server, TOGETHER) || a.level() != b.level() || a.distanceTo(b) > 12f) return;
        if (TetherManager.isDowned(a.getUUID()) || TetherManager.isDowned(b.getUUID())) return;
        a.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 110, 0, true, false));
        b.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 110, 0, true, false));
    }

    /** Shared Table: when you finish eating, your partner (within 32 blocks) gets half of it. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        if (!has(p.getServer(), SHARED_TABLE)) return;
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null) return;
        ServerPlayer partner = TetherManager.partnerOf(p);
        if (partner == null || partner.level() != p.level() || partner.distanceTo(p) > 32f) return;
        int nutrition = Math.max(1, food.nutrition() / 2);
        float saturation = food.saturation() / 2f;
        partner.getFoodData().eat(nutrition, saturation / Math.max(1f, nutrition * 2f));
    }

    /** Heartward: no monsters spawn naturally within 48 blocks of the Heart Tree. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL) return;
        if (!(event.getLevel().getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        TetherData data = TetherData.get(level.getServer());
        BlockPos tree = data.heartTreePos();
        if (tree == null || data.heartStage() < HEARTWARD) return;
        if (event.getEntity().blockPosition().distSqr(tree) < 48 * 48) {
            event.setSpawnCancelled(true);
        }
    }

    private Blessings() {}
}
