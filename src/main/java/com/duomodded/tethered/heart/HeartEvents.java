package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.command.ModCommands;
import com.duomodded.tethered.tether.TetherData;
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
            if (tree.stage() == max && max > 0) {
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
