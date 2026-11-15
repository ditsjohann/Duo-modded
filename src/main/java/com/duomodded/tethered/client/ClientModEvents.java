package com.duomodded.tethered.client;

import com.duomodded.tethered.Tethered;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = Tethered.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(Keys.HAUL);
        event.register(Keys.LAUNCH);
        event.register(Keys.ENDER_CALL);
        event.register(Keys.UNHOOK);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Tethered.id("tether_hud"), TetherHud::render);
    }

    private ClientModEvents() {}
}
