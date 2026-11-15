package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.command.ModCommands;
import com.duomodded.tethered.quest.QuestLoader;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.world.IslandLoader;
import com.duomodded.tethered.world.IslandManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Tethered.MODID)
public final class HeartEvents {
    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new StageLoader());
        event.addListener(new IslandLoader());
        event.addListener(new QuestLoader());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        StartIsland.maybeGenerate(event.getServer());
        if (Boolean.getBoolean("tethered.smokeTest")) smokeTest(event.getServer());
    }

    /** CI only (-Dtethered.smokeTest=true): grow the tree through every stage, then stop the server. */
    private static void smokeTest(MinecraftServer server) {
        BlockPos pos = TetherData.get(server).heartTreePos();
        ServerLevel level = server.overworld();
        if (pos == null || !(level.getBlockEntity(pos) instanceof HeartTreeBlockEntity tree)) {
            Tethered.LOGGER.error("SMOKE TEST FAILED: no Heart Tree found");
        } else {
            int max = StageLoader.maxStage();
            while (tree.forceGrow(level)) {
                Tethered.LOGGER.info("SMOKE TEST: Heart Tree grew to stage {} of {}", tree.stage(), max);
            }
            IslandManager.unlockAll(server);
            TetherData data = TetherData.get(server);
            Tethered.LOGGER.info("SMOKE TEST: {} islands unlocked, {} built, {} quest chapters, {} quests",
                    data.unlockedIslands().size(), data.generatedIslands().size(),
                    QuestLoader.chapters().size(), QuestLoader.all().size());
            QuestManager.checkAll(server, null);
            QuestManager.syncAll(server);
            boolean islandsOk = !IslandLoader.all().isEmpty() && data.generatedIslands().size() == IslandLoader.all().size();
            if (tree.stage() == max && max > 0 && islandsOk && !QuestLoader.all().isEmpty()) {
                Tethered.LOGGER.info("SMOKE TEST PASSED");
            } else {
                Tethered.LOGGER.error("SMOKE TEST FAILED: stopped at stage {} of {}", tree.stage(), max);
            }
        }
        server.halt(false);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private HeartEvents() {}
}
