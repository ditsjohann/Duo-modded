package com.duomodded.tethered.heart;

import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.util.Advancements;
import com.duomodded.tethered.world.IslandManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class HeartTreeBlockEntity extends BlockEntity {
    private static final double NEAR = 24.0;

    private int stage;
    private int[] progress = new int[0];
    private long lastWaitMessage;

    public HeartTreeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.HEART_TREE_BE.get(), pos, state);
    }

    public int stage() {
        return stage;
    }

    @Nullable
    public StageDef nextDef() {
        return StageLoader.get(stage + 1);
    }

    private int[] progressFor(StageDef def) {
        if (progress.length != def.requires().size()) {
            progress = Arrays.copyOf(progress, def.requires().size());
        }
        return progress;
    }

    public boolean complete() {
        StageDef next = nextDef();
        if (next == null) return false;
        int[] prog = progressFor(next);
        for (int i = 0; i < prog.length; i++) {
            if (prog[i] < next.requires().get(i).count()) return false;
        }
        return true;
    }

    /** Takes as much of the held stack as an unfinished requirement needs. */
    public boolean offer(ServerPlayer player, ItemStack stack) {
        StageDef next = nextDef();
        if (next == null) return false;
        int[] prog = progressFor(next);
        for (int i = 0; i < prog.length; i++) {
            StageDef.Requirement req = next.requires().get(i);
            if (prog[i] >= req.count() || !req.matches(stack)) continue;

            int take = Math.min(req.count() - prog[i], stack.getCount());
            prog[i] += take;
            if (!player.getAbilities().instabuild) stack.shrink(take);
            setChanged();

            player.displayClientMessage(Component.empty().append(req.displayName())
                    .append(Component.literal("  " + prog[i] + "/" + req.count()))
                    .withStyle(prog[i] >= req.count() ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE), true);
            ServerLevel level = player.serverLevel();
            Vec3 c = Vec3.atCenterOf(worldPosition);
            level.sendParticles(ParticleTypes.HEART, c.x, c.y + 0.8, c.z, 3, 0.4, 0.3, 0.4, 0.02);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);

            if (complete()) tryGrow(level, true);
            return true;
        }
        return false;
    }

    public void showStatus(ServerPlayer player) {
        StageDef next = nextDef();
        if (next == null) {
            player.sendSystemMessage(Component.translatable("msg.tethered.tree.full_bloom").withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }
        player.sendSystemMessage(Component.translatable("msg.tethered.tree.status", StageLoader.nameOf(stage), next.name())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        int[] prog = progressFor(next);
        for (int i = 0; i < prog.length; i++) {
            StageDef.Requirement req = next.requires().get(i);
            boolean done = prog[i] >= req.count();
            player.sendSystemMessage(Component.literal(done ? "  ✔ " : "  • ")
                    .append(req.displayName())
                    .append(Component.literal("  " + prog[i] + "/" + req.count()))
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        if (TetherConfig.REQUIRE_BOTH_AT_TREE.get()) {
            player.sendSystemMessage(Component.translatable("msg.tethered.tree.together_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HeartTreeBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.stage >= Blessings.GREEN_THUMB && level.getGameTime() % 10 == 0) {
            Blessings.growCrops(serverLevel, pos, level.random);
        }
        if (level.getGameTime() % 40 == 0 && be.complete()) be.tryGrow(serverLevel, false);
    }

    /** Grows if every requirement is met and the pair is standing near the tree. */
    public boolean tryGrow(ServerLevel level, boolean notify) {
        StageDef next = nextDef();
        if (next == null || !complete()) return false;
        if (!pairIsNear(level, notify)) return false;
        grow(level, next);
        return true;
    }

    /** Operator command: skip straight to the next stage. */
    public boolean forceGrow(ServerLevel level) {
        StageDef next = nextDef();
        if (next == null) return false;
        grow(level, next);
        return true;
    }

    private boolean pairIsNear(ServerLevel level, boolean notify) {
        Vec3 c = Vec3.atCenterOf(worldPosition);
        List<ServerPlayer> near = level.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(c) < NEAR * NEAR);
        if (near.isEmpty()) return false;
        if (!TetherConfig.REQUIRE_BOTH_AT_TREE.get()) return true;

        MinecraftServer server = level.getServer();
        TetherData data = TetherData.get(server);
        for (ServerPlayer p : near) {
            TetherData.Link link = data.getLink(p.getUUID());
            if (link == null) continue;
            ServerPlayer partner = server.getPlayerList().getPlayer(link.other(p.getUUID()));
            if (partner == null || !near.contains(partner)) {
                long now = level.getGameTime();
                if (notify || now - lastWaitMessage > 1200) {
                    lastWaitMessage = now;
                    for (ServerPlayer x : near) {
                        x.displayClientMessage(Component.translatable("msg.tethered.tree.waits").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    }
                }
                return false;
            }
        }
        return true;
    }

    private void grow(ServerLevel level, StageDef def) {
        stage = def.stage();
        progress = new int[0];
        setChanged();
        BlockState state = getBlockState();
        if (state.hasProperty(HeartTreeBlock.STAGE)) {
            level.setBlock(worldPosition, state.setValue(HeartTreeBlock.STAGE, Math.min(7, Math.max(0, stage))), 3);
        }

        int max = StageLoader.maxStage();
        boolean bloom = stage >= max;
        TreeShaper.grow(level, worldPosition, stage, bloom);

        RandomSource random = RandomSource.create(level.getSeed() ^ worldPosition.asLong() ^ (stage * 7919L));
        for (int i = 0; i < def.rings().size(); i++) {
            IslandBuilder.buildRing(level, worldPosition, def.rings().get(i), stage, i, random);
        }

        MinecraftServer server = level.getServer();
        TetherData.get(server).setHeartStage(stage);
        for (String island : def.unlocks()) IslandManager.unlock(server, island);
        QuestManager.markDirty();

        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.HEART, c.x, c.y + 2, c.z, 40, 3, 3, 3, 0.1);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, c.x, c.y + 6, c.z, 200, 6, 4, 6, 0.05);
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.5f, 0.8f);

        Component title = Component.literal(def.name()).withStyle(ChatFormatting.LIGHT_PURPLE);
        Component subtitle = Component.translatable("msg.tethered.tree.grew");
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Ending.title(p, title, subtitle);
            if (!def.message().isEmpty()) {
                p.sendSystemMessage(Component.literal(def.message()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            Advancements.award(p, "heart_tree/stage_" + stage);
            p.sendSystemMessage(Component.translatable("msg.tethered.tree.blessing",
                    Component.translatable("blessing.tethered." + stage).withStyle(ChatFormatting.GOLD),
                    Component.translatable("blessing.tethered." + stage + ".desc")).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) TetherManager.sync(p);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stage", stage);
        tag.putIntArray("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stage = tag.getInt("stage");
        progress = tag.getIntArray("progress");
    }
}
