package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.age.AgeLoader;
import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.command.ModCommands;
import com.duomodded.tethered.event.EventDef;
import com.duomodded.tethered.event.EventLoader;
import com.duomodded.tethered.event.WorldEvents;
import com.duomodded.tethered.quest.QuestLoader;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.util.Ids;
import com.duomodded.tethered.world.IslandLoader;
import com.duomodded.tethered.world.IslandManager;
import com.duomodded.tethered.world.LastStone;
import com.duomodded.tethered.world.LastStoneGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = Tethered.MODID)
public final class HeartEvents {
    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new AgeLoader());
        event.addListener(new EventLoader());
        event.addListener(new IslandLoader());
        event.addListener(new QuestLoader());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        LastStone.maybeGenerate(event.getServer());
        if (Boolean.getBoolean("tethered.smokeTest")) smokeTest(event.getServer());
    }

    /**
     * CI only (-Dtethered.smokeTest=true): checks the Last Stone and the Hollow Heart, breaks the
     * Heartstone in every Age, spawns every Trial guardian, runs every event once, builds every island
     * and lists every data problem (unknown ids with close matches). Then it stops the server.
     * The result also goes to tethered-smoke.txt, in case another mod hides the log.
     */
    private static void smokeTest(MinecraftServer server) {
        StringBuilder report = new StringBuilder();
        boolean ok = true;
        try {
            ServerLevel level = server.overworld();
            TetherData data = TetherData.get(server);
            LastStoneGenerator gen = LastStone.generator(level);
            report.append("generator: ").append(gen != null ? "last_stone r=" + gen.radius() : level.getChunkSource().getGenerator().getClass().getSimpleName()).append('\n');
            BlockPos core = data.heartPos();
            if (gen == null || core == null || !level.getBlockState(core).is(ModRegistry.HEARTSTONE.get())) {
                report.append("SMOKE TEST FAILED: no Heartstone (setup done: ").append(data.startIslandGenerated()).append(")\n");
                ok = false;
            } else {
                int solid = 0;
                int[][] samples = {{0, 30, 20}, {40, 64, 0}, {-30, 90, 10}, {0, 64, -60}, {20, 20, 20}};
                for (int[] s : samples) if (!level.getBlockState(new BlockPos(s[0], s[1], s[2])).isAir()) solid++;
                report.append("stone samples solid: ").append(solid).append("/5, live block: ")
                        .append(level.getBlockState(core.above()).getBlock().getDescriptionId()).append('\n');
                report.append("world tree at ").append(data.heartTreePos()).append('\n');
                report.append("loaded: ").append(AgeLoader.all().size()).append(" ages, ").append(EventLoader.all().size())
                        .append(" events, ").append(IslandLoader.all().size()).append(" islands, ")
                        .append(QuestLoader.all().size()).append(" quests\n");
                if (AgeLoader.all().size() < 2 || solid < 3) ok = false;

                // Remnant
                BlockPos test = core.offset(5, 1, 0);
                level.setBlock(test, ModRegistry.REMNANT.get().defaultBlockState(), 3);
                level.destroyBlock(test, true);

                int max = AgeLoader.maxAge();
                for (int age = data.age(); age <= max; age++) {
                    for (int i = 0; i < 25; i++) {
                        level.setBlock(core.above(), Blocks.AIR.defaultBlockState(), 3);
                        Heartstone.regen(level, true);
                    }
                    report.append("age ").append(age).append(" (").append(AgeLoader.nameOf(age)).append("): ")
                            .append(data.totalBreaks()).append(" breaks, top block ")
                            .append(level.getBlockState(core.above()).getBlock().getDescriptionId());
                    String trial = Heartstone.testTrial(server, age + 1);
                    if (trial != null) report.append(", next trial: ").append(trial);
                    report.append('\n');
                    if (age < max && !Heartstone.forceAdvance(server)) {
                        report.append("could not advance from age ").append(age).append('\n');
                        ok = false;
                        break;
                    }
                }
                report.append("reached age ").append(data.age()).append(" of ").append(max).append('\n');
                if (data.age() != max) ok = false;

                for (EventDef def : EventLoader.all()) {
                    boolean started = WorldEvents.start(server, def);
                    WorldEvents.stop(server);
                    report.append("event ").append(def.id()).append(" (").append(def.type()).append("): ")
                            .append(started ? "ok" : "did not start").append('\n');
                }

                IslandManager.unlockAll(server);
                QuestManager.checkAll(server, null);
                QuestManager.syncAll(server);
                report.append("islands built: ").append(data.generatedIslands().size()).append(" of ").append(IslandLoader.all().size()).append('\n');
                if (IslandLoader.all().isEmpty() || data.generatedIslands().size() != IslandLoader.all().size()) ok = false;
            }
            var problems = Ids.problems();
            report.append("data problems: ").append(problems.size()).append('\n');
            for (String p : problems) report.append("  PROBLEM ").append(p).append('\n');
            report.append(ok ? "SMOKE TEST PASSED\n" : "SMOKE TEST FAILED\n");
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
