package com.duomodded.tethered.command;

import com.duomodded.tethered.age.AgeLoader;
import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.event.EventDef;
import com.duomodded.tethered.event.EventLoader;
import com.duomodded.tethered.event.WorldEvents;
import com.duomodded.tethered.heart.Ending;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.world.IslandManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /tether status | travel | choice | pair | cut | tier | event | favor
 * /heart info | advance | breaks | tp
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
                                            ctx.getSource().sendSuccess(() -> Component.literal("Bonded " + a.getGameProfile().getName() + " and " + b.getGameProfile().getName()), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("cut").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    TetherManager.cut(ctx.getSource().getServer(), p.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cut the thread of " + p.getGameProfile().getName()), true);
                                    return 1;
                                })))
                .then(Commands.literal("tier").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                        .executes(ctx -> setTier(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "tier"))))))
                .then(Commands.literal("event").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("start")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                EventLoader.all().stream().map(EventDef::id), b))
                                        .executes(ctx -> startEvent(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(Commands.literal("stop").executes(ctx -> {
                            WorldEvents.stop(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Stopped the current event"), true);
                            return 1;
                        })))
                .then(Commands.literal("favor").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> favor(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> favor(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "count")))))));

        dispatcher.register(Commands.literal("heart")
                .then(Commands.literal("info").executes(ctx -> {
                    Heartstone.showStatus(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("advance").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    if (!Heartstone.forceAdvance(server)) {
                        ctx.getSource().sendFailure(Component.translatable("msg.tethered.heart.whole"));
                        return 0;
                    }
                    int age = TetherData.get(server).age();
                    ctx.getSource().sendSuccess(() -> Component.literal("The world moved into " + AgeLoader.nameOf(age)), true);
                    return 1;
                }))
                .then(Commands.literal("breaks").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> {
                                    int n = IntegerArgumentType.getInteger(ctx, "count");
                                    TetherData.get(ctx.getSource().getServer()).addBreaks(n);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Added " + n + " memories"), true);
                                    return 1;
                                })))
                .then(Commands.literal("tp").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    BlockPos core = Heartstone.core(ctx.getSource().getServer());
                    if (core == null) return 0;
                    p.teleportTo(ctx.getSource().getServer().overworld(), core.getX() + 0.5, core.getY() + 1, core.getZ() + 4.5, p.getYRot(), p.getXRot());
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

    private static int startEvent(CommandSourceStack source, String id) {
        EventDef def = EventLoader.get(id);
        if (def == null) {
            source.sendFailure(Component.literal("No event called " + id));
            return 0;
        }
        if (!WorldEvents.start(source.getServer(), def)) {
            source.sendFailure(Component.literal("Another event is running (/tether event stop)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Started " + def.name()), true);
        return 1;
    }

    private static int favor(CommandSourceStack source, ServerPlayer p, int count) {
        ItemStack stack = new ItemStack(ModRegistry.WEAVERS_FAVOR.get(), count);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        source.sendSuccess(() -> Component.literal("Gave " + count + " Weaver's Favor to " + p.getGameProfile().getName()), true);
        return 1;
    }

    private ModCommands() {}
}
