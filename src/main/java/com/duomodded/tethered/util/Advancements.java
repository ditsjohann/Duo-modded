package com.duomodded.tethered.util;

import com.duomodded.tethered.Tethered;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Our advancements all use the "impossible" trigger and are granted from code. Quest mods
 * (FTB Quests) can use them as advancement tasks.
 */
public final class Advancements {
    public static void award(ServerPlayer player, String path) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        AdvancementHolder holder = server.getAdvancements().get(Tethered.id(path));
        if (holder != null) {
            player.getAdvancements().award(holder, "done");
        }
    }

    private Advancements() {}
}
