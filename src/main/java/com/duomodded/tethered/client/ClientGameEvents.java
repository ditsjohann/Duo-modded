package com.duomodded.tethered.client;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.network.TetherActionPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Tethered.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static boolean haulWasDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            while (Keys.JOURNAL.consumeClick()) ClientAccess.openJournal();
        }
        if (mc.player == null || mc.level == null || !ClientTetherState.get().linked()) {
            haulWasDown = false;
            return;
        }

        while (Keys.ENDER_CALL.consumeClick()) send(TetherActionPayload.ENDER_CALL, true);
        if (!ClientTetherState.get().rope()) {
            haulWasDown = false;
            if (!mc.isPaused()) RopePhysics.tick(mc);
            return;
        }
        boolean haul = Keys.HAUL.isDown();
        if (haul != haulWasDown) {
            haulWasDown = haul;
            send(TetherActionPayload.HAUL, haul);
        }
        while (Keys.LAUNCH.consumeClick()) send(TetherActionPayload.LAUNCH, true);
        while (Keys.UNHOOK.consumeClick()) send(TetherActionPayload.UNHOOK, true);

        if (!mc.isPaused()) RopePhysics.tick(mc);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            RopeRenderer.render(event);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientTetherState.reset();
        ClientJournalState.reset();
        haulWasDown = false;
    }

    private static void send(int action, boolean down) {
        PacketDistributor.sendToServer(new TetherActionPayload(action, down));
    }

    private ClientGameEvents() {}
}
