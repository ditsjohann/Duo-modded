package com.duomodded.tethered.world;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.heart.Blessings;
import com.duomodded.tethered.heart.IslandBuilder;
import com.duomodded.tethered.heart.StartIsland;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the world map: builds islands when someone comes near (or when the tree unlocks them),
 * grows Rootways, notices visits, attunes Anchor Stones and handles fast travel.
 */
@EventBusSubscriber(modid = Tethered.MODID)
public final class IslandManager {
    /** Islands are built when a player gets this close (beyond render distance, so they're ready in time). */
    private static final int BUILD_DISTANCE = 240;
    public static final int HOME_RADIUS = 7;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel level = server.overworld();
        TetherData data = TetherData.get(server);
        if (!data.startIslandGenerated()) return; // not a Tethered Sky world

        for (ServerPlayer p : level.players()) {
            for (IslandDef island : IslandLoader.all().values()) {
                double d = island.horizontalDistance(p.getX(), p.getZ());
                if (d < BUILD_DISTANCE && !data.generatedIslands().contains(island.id())) {
                    generate(level, island);
                }
                if (d <= island.radius() + 6 && data.visitedIslands().add(island.id())) {
                    data.setDirty();
                    arrive(server, island);
                    QuestManager.markDirty();
                }
            }
        }
    }

    private static void arrive(MinecraftServer server, IslandDef island) {
        Component title = Component.literal(island.name()).withStyle(s -> s.withColor(island.color()));
        Component subtitle = Component.literal(island.description()).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            Advancements.award(p, "first_landing");
        }
    }

    public static void generate(ServerLevel level, IslandDef island) {
        TetherData data = TetherData.get(level.getServer());
        if (!data.generatedIslands().add(island.id())) return;
        data.setDirty();
        RandomSource random = RandomSource.create(level.getSeed() ^ island.id().hashCode() * 31L);
        IslandBuilder.buildIsland(level, island.center(), island.spec(), island.radius(), random);
        BlockPos anchor = anchorPos(level, island);
        level.setBlock(anchor.below(), island.spec().surface(), 3);
        level.setBlock(anchor, ModRegistry.ANCHOR_STONE.get().defaultBlockState(), 3);
        for (int y = 1; y <= 3; y++) {
            if (!level.getBlockState(anchor.above(y)).isAir()) level.setBlock(anchor.above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        Tethered.LOGGER.info("Built island {} at {}", island.id(), island.center());
    }

    /** Where the Anchor Stone sits: on top of the island's middle. */
    public static BlockPos anchorPos(ServerLevel level, IslandDef island) {
        return surfaceAbove(level, island.center().getX(), island.center().getZ(), island.center().getY());
    }

    private static BlockPos surfaceAbove(ServerLevel level, int x, int z, int yHint) {
        for (int y = yHint + 16; y >= yHint - 16; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.canBeReplaced() && !state.is(ModRegistry.ANCHOR_STONE.get())) {
                return pos.above();
            }
        }
        return new BlockPos(x, yHint + 1, z);
    }

    /** The Heart Tree's roots reach out to an island: build it now and grow a Rootway to it. */
    public static void unlock(MinecraftServer server, String id) {
        IslandDef island = IslandLoader.get(id);
        if (island == null) {
            Tethered.LOGGER.warn("Tried to unlock unknown island {}", id);
            return;
        }
        TetherData data = TetherData.get(server);
        if (!data.unlockedIslands().add(id)) return;
        data.setDirty();
        ServerLevel level = server.overworld();
        generate(level, island);
        BlockPos tree = data.heartTreePos();
        if (island.rootway() && tree != null) {
            RootwayBuilder.build(level, tree, HOME_RADIUS, island);
        }
        Component msg = Component.translatable("msg.tethered.island.unlocked",
                Component.literal(island.name()).withStyle(s -> s.withColor(island.color())));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        QuestManager.markDirty();
    }

    @Nullable
    public static IslandDef islandAt(BlockPos pos, int margin) {
        for (IslandDef island : IslandLoader.all().values()) {
            if (island.horizontalDistance(pos.getX() + 0.5, pos.getZ() + 0.5) <= island.radius() + margin) return island;
        }
        return null;
    }

    /** Right-click on an Anchor Stone: attune it together, or travel home if already attuned. */
    public static void useAnchor(ServerPlayer player, BlockPos pos) {
        IslandDef island = islandAt(pos, 6);
        MinecraftServer server = player.getServer();
        if (island == null || server == null) return;
        TetherData data = TetherData.get(server);

        if (data.attunedIslands().contains(island.id())) {
            player.sendSystemMessage(Component.translatable("msg.tethered.anchor.attuned_already", island.name())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.translatable("msg.tethered.anchor.go_home").withStyle(style -> style
                    .withColor(ChatFormatting.GOLD).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tether travel tree"))));
            return;
        }
        ServerPlayer partner = TetherManager.partnerOf(player);
        if (partner != null && (partner.level() != player.level() || partner.distanceTo(player) > 8f)) {
            player.displayClientMessage(Component.translatable("msg.tethered.anchor.together").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return;
        }
        data.attunedIslands().add(island.id());
        data.visitedIslands().add(island.id());
        data.setDirty();
        Component msg = Component.translatable("msg.tethered.anchor.attuned",
                Component.literal(island.name()).withStyle(s -> s.withColor(island.color())));
        ServerLevel level = player.serverLevel();
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1f, 1.2f);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
            Advancements.award(p, "attuned");
        }
        QuestManager.markDirty();
    }

    /**
     * Fast travel for the pair (Heart Paths blessing). You must stand at the Heart Tree or an attuned
     * Anchor Stone, your partner must be with you, and the target must be attuned (or "tree").
     */
    public static void travel(ServerPlayer player, String target) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        TetherData data = TetherData.get(server);
        if (!Blessings.has(server, Blessings.HEART_PATHS)) {
            player.displayClientMessage(Component.translatable("msg.tethered.travel.locked"), true);
            return;
        }
        ServerLevel level = server.overworld();
        if (player.level() != level || !atTravelPoint(player, data)) {
            player.displayClientMessage(Component.translatable("msg.tethered.travel.where"), true);
            return;
        }
        BlockPos dest;
        if ("tree".equals(target)) {
            BlockPos tree = data.heartTreePos();
            if (tree == null) return;
            dest = surfaceAbove(level, tree.getX(), tree.getZ() + 2, tree.getY());
        } else {
            IslandDef island = IslandLoader.get(target);
            if (island == null || !data.attunedIslands().contains(target)) {
                player.displayClientMessage(Component.translatable("msg.tethered.travel.not_attuned"), true);
                return;
            }
            BlockPos anchor = anchorPos(level, island);
            dest = surfaceAbove(level, anchor.getX() + 2, anchor.getZ(), anchor.getY());
        }
        ServerPlayer partner = TetherManager.partnerOf(player);
        if (partner != null && (partner.level() != player.level() || partner.distanceTo(player) > 10f)) {
            player.displayClientMessage(Component.translatable("msg.tethered.travel.together"), true);
            return;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1f);
        teleport(player, level, dest, 0);
        if (partner != null) teleport(partner, level, dest, 1);
        level.playSound(null, dest, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.3f);
    }

    private static void teleport(ServerPlayer p, ServerLevel level, BlockPos dest, int offset) {
        p.teleportTo(level, dest.getX() + 0.5 + offset, dest.getY(), dest.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
    }

    private static boolean atTravelPoint(ServerPlayer player, TetherData data) {
        BlockPos tree = data.heartTreePos();
        if (tree != null && player.blockPosition().distSqr(tree) < 10 * 10) return true;
        for (String id : data.attunedIslands()) {
            IslandDef island = IslandLoader.get(id);
            if (island == null) continue;
            if (island.horizontalDistance(player.getX(), player.getZ()) < 8
                    && Math.abs(player.getY() - island.center().getY()) < 16) {
                return true;
            }
        }
        return false;
    }

    /** CI smoke test: build every island and Rootway. */
    public static void unlockAll(MinecraftServer server) {
        for (String id : IslandLoader.all().keySet()) unlock(server, id);
    }

    public static boolean isTetheredWorld(MinecraftServer server) {
        return TetherData.get(server).startIslandGenerated() || StartIsland.isVoidWorld(server.overworld());
    }

    private IslandManager() {}
}
