package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.command.ModCommands;
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
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    private HeartEvents() {}
}
