package com.duomodded.tethered.command;

import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.heart.HeartTreeBlockEntity;
import com.duomodded.tethered.heart.StageLoader;
import com.duomodded.tethered.heart.StartIsland;
import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.duomodded.tethered.world.IslandManager;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * /tether status | pair | cut | tier | choice
 * /hearttree info | grow | here
 */
public final class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tether")
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("travel")
                        .then(Commands.argument("where", StringArgumentType.word())
                                .executes(ctx -> {
                                    IslandManager.travel(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "where"));
                                    return 1;
                                })))
                .then(Commands.literal("choice")
                        .then(Commands.literal("keep").executes(ctx -> choice(ctx.getSource(), true)))
                        .then(Commands.literal("cut").executes(ctx -> choice(ctx.getSource(), false))))
                .then(Commands.literal("pair").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("a", EntityArgument.player())
                                .then(Commands.argument("b", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer a = EntityArgument.getPlayer(ctx, "a");
                                            ServerPlayer b = EntityArgument.getPlayer(ctx, "b");
                                            if (a == b) {
                                                ctx.getSource().sendFailure(Component.literal("Pick two different players."));
                                                return 0;
                                            }
                                            TetherManager.pair(a, b, RopeTier.TWINE);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Tethered " + a.getGameProfile().getName() + " and " + b.getGameProfile().getName()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("cut").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    TetherManager.cut(ctx.getSource().getServer(), p.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cut the rope of " + p.getGameProfile().getName()), true);
                                    return 1;
                                })))
                .then(Commands.literal("tier").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> setTier(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "tier")))))));

        dispatcher.register(Commands.literal("hearttree")
                .then(Commands.literal("info").executes(ctx -> treeInfo(ctx.getSource())))
                .then(Commands.literal("grow").requires(s -> s.hasPermission(2)).executes(ctx -> treeGrow(ctx.getSource())))
                .then(Commands.literal("unlockall").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    IslandManager.unlockAll(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Unlocked every island"), true);
                    return 1;
                }))
                .then(Commands.literal("startisland").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos at = p.blockPosition().below();
                    StartIsland.generate(p.serverLevel(), at);
                    ctx.getSource().sendSuccess(() -> Component.literal("Built a starting island at " + at.toShortString()), true);
                    return 1;
                })));
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        TetherData data = TetherData.get(source.getServer());
        TetherData.Link link = data.getLink(p.getUUID());
        if (link == null) {
            source.sendSuccess(() -> Component.translatable("msg.tethered.not_tied"), false);
            return 0;
        }
        String partner = data.nameOf(link.other(p.getUUID()));
        RopeTier tier = link.ropeTier();
        source.sendSuccess(() -> Component.translatable("msg.tethered.status", partner, tier.displayName(), (int) tier.length), false);
        return 1;
    }

    private static int choice(CommandSourceStack source, boolean keep) throws CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (!Ending.choose(p, keep)) {
            source.sendFailure(Component.translatable("weaver.tethered.ending.not_yet"));
            return 0;
        }
        return 1;
    }

    private static int setTier(CommandSourceStack source, ServerPlayer p, int tier) {
        TetherData data = TetherData.get(source.getServer());
        TetherData.Link link = data.getLink(p.getUUID());
        if (link == null) {
            source.sendFailure(Component.translatable("msg.tethered.not_tied"));
            return 0;
        }
        link.setTier(tier);
        data.setDirty();
        TetherManager.sync(p);
        ServerPlayer partner = TetherManager.partnerOf(p);
        if (partner != null) TetherManager.sync(partner);
        source.sendSuccess(() -> Component.literal("Rope set to ").append(RopeTier.byLevel(tier).displayName()), true);
        return 1;
    }

    private static HeartTreeBlockEntity findTree(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        BlockPos pos = TetherData.get(server).heartTreePos();
        if (pos == null) return null;
        ServerLevel level = server.overworld();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof HeartTreeBlockEntity tree ? tree : null;
    }

    private static int treeInfo(CommandSourceStack source) throws CommandSyntaxException {
        HeartTreeBlockEntity tree = findTree(source);
        if (tree == null) {
            source.sendFailure(Component.translatable("msg.tethered.tree.none"));
            return 0;
        }
        BlockPos pos = tree.getBlockPos();
        source.sendSuccess(() -> Component.translatable("msg.tethered.tree.where", pos.toShortString(),
                StageLoader.nameOf(tree.stage()), tree.stage(), StageLoader.maxStage()), false);
        if (source.getEntity() instanceof ServerPlayer p) tree.showStatus(p);
        return 1;
    }

    private static int treeGrow(CommandSourceStack source) {
        HeartTreeBlockEntity tree = findTree(source);
        if (tree == null || !(tree.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.translatable("msg.tethered.tree.none"));
            return 0;
        }
        if (!tree.forceGrow(level)) {
            source.sendFailure(Component.translatable("msg.tethered.tree.full_bloom"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("The Heart Tree grew to stage " + tree.stage()), true);
        return 1;
    }

    private ModCommands() {}
}
