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

    /**
     * CI only (-Dtethered.smokeTest=true): grow the tree through every stage, build every island,
     * then stop the server. The result also goes to tethered-smoke.txt, in case another mod hides the log.
     */
    private static void smokeTest(MinecraftServer server) {
        StringBuilder report = new StringBuilder();
        try {
            BlockPos pos = TetherData.get(server).heartTreePos();
            ServerLevel level = server.overworld();
            if (pos == null || !(level.getBlockEntity(pos) instanceof HeartTreeBlockEntity tree)) {
                report.append("SMOKE TEST FAILED: no Heart Tree found (start island built: ")
                        .append(TetherData.get(server).startIslandGenerated()).append(")\n");
            } else {
                int max = StageLoader.maxStage();
                while (tree.forceGrow(level)) {
                    report.append("grew to stage ").append(tree.stage()).append(" of ").append(max).append('\n');
                }
                IslandManager.unlockAll(server);
                TetherData data = TetherData.get(server);
                QuestManager.checkAll(server, null);
                QuestManager.syncAll(server);
                report.append("islands built: ").append(data.generatedIslands().size()).append(" of ")
                        .append(IslandLoader.all().size()).append(", quests: ").append(QuestLoader.all().size()).append('\n');
                boolean islandsOk = !IslandLoader.all().isEmpty() && data.generatedIslands().size() == IslandLoader.all().size();
                report.append(tree.stage() == max && max > 0 && islandsOk && !QuestLoader.all().isEmpty()
                        ? "SMOKE TEST PASSED\n" : "SMOKE TEST FAILED\n");
            }
        } catch (Throwable t) {
            report.append("SMOKE TEST FAILED: ").append(t).append('\n');
            for (StackTraceElement e : t.getStackTrace()) report.append("  at ").append(e).append('\n');
        }
        for (String line : report.toString().split("\n")) Tethered.LOGGER.info("SMOKE: {}", line);
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("tethered-smoke.txt"), report.toString());
        } catch (java.io.IOException ignored) {
            // nothing else we can do
        }
        server.halt(false);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private HeartEvents() {}
}
