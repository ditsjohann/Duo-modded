package com.duomodded.tethered.heart;

import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.util.Advancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * When the Heart Tree blooms, the Weaver asks the pair whether to cut the thread. Both must
 * answer. Both keep: the rope becomes the Golden Thread. Both cut: the rope is removed.
 * Mixed: talk it over and choose again.
 */
public final class Ending {
    public static void offerAll(MinecraftServer server) {
        TetherData data = TetherData.get(server);
        if (!data.endingUnlocked()) return;
        for (TetherData.Link link : data.uniqueLinks()) {
            offer(server, data, link);
        }
    }

    private static void offer(MinecraftServer server, TetherData data, TetherData.Link link) {
        if (link.endingOffered || link.tier >= RopeTier.GOLDEN.level) return;
        ServerPlayer a = server.getPlayerList().getPlayer(link.a);
        ServerPlayer b = server.getPlayerList().getPlayer(link.b);
        if (a == null || b == null) return;
        link.endingOffered = true;
        data.setDirty();
        sendChoice(a);
        sendChoice(b);
    }

    private static void sendChoice(ServerPlayer p) {
        p.sendSystemMessage(Component.translatable("weaver.tethered.ending.1").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("weaver.tethered.ending.2").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        MutableComponent keep = Component.translatable("weaver.tethered.ending.keep").withStyle(style -> style
                .withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tether choice keep"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("weaver.tethered.ending.keep_hover"))));
        MutableComponent cut = Component.translatable("weaver.tethered.ending.cut").withStyle(style -> style
                .withColor(ChatFormatting.GRAY).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tether choice cut"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("weaver.tethered.ending.cut_hover"))));
        p.sendSystemMessage(Component.literal("   ").append(keep).append(Component.literal("    ")).append(cut));
    }

    /** Called from /tether choice keep|cut. Returns false if there is nothing to choose. */
    public static boolean choose(ServerPlayer p, boolean keep) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        TetherData data = TetherData.get(server);
        TetherData.Link link = data.getLink(p.getUUID());
        if (link == null || !link.endingOffered || link.tier >= RopeTier.GOLDEN.level) return false;

        link.choices.put(p.getUUID(), keep);
        ServerPlayer partner = server.getPlayerList().getPlayer(link.other(p.getUUID()));
        if (link.choices.size() < 2 || partner == null) {
            p.sendSystemMessage(Component.translatable("weaver.tethered.ending.waiting").withStyle(ChatFormatting.GRAY));
            if (partner != null) {
                partner.sendSystemMessage(Component.translatable("weaver.tethered.ending.partner_chose", p.getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
            return true;
        }

        boolean keepA = link.choices.getOrDefault(link.a, false);
        boolean keepB = link.choices.getOrDefault(link.b, false);
        link.choices.clear();
        ServerPlayer[] pair = {p, partner};

        if (keepA && keepB) {
            link.setTier(RopeTier.GOLDEN.level);
            data.setDirty();
            for (ServerPlayer x : pair) {
                title(x, Component.translatable("weaver.tethered.ending.kept_title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("weaver.tethered.ending.kept_sub"));
                x.sendSystemMessage(Component.translatable("weaver.tethered.ending.kept").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                give(x, new ItemStack(ModRegistry.GOLDEN_THREAD.get()));
                Advancements.award(x, "bound_forever");
                x.serverLevel().playSound(null, x.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
                TetherManager.sync(x);
            }
        } else if (!keepA && !keepB) {
            data.unlink(p.getUUID());
            for (ServerPlayer x : pair) {
                title(x, Component.translatable("weaver.tethered.ending.cut_title"),
                        Component.translatable("weaver.tethered.ending.cut_sub"));
                x.sendSystemMessage(Component.translatable("weaver.tethered.ending.cut_done").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                give(x, new ItemStack(ModRegistry.RED_THREAD.get()));
                Advancements.award(x, "cut_free");
                TetherManager.sync(x);
            }
        } else {
            for (ServerPlayer x : pair) {
                x.sendSystemMessage(Component.translatable("weaver.tethered.ending.disagree").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
                sendChoice(x);
            }
        }
        return true;
    }

    public static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) p.drop(stack, false);
    }

    private Ending() {}
}
