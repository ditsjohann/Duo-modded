package com.duomodded.tethered.network;

import com.duomodded.tethered.client.ClientTetherState;
import com.duomodded.tethered.tether.TetherManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ClientTetherState only stores the payload (no client-only classes), so this is safe on servers.
        registrar.playToClient(TetherStatePayload.TYPE, TetherStatePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientTetherState.set(payload)));

        registrar.playToServer(TetherActionPayload.TYPE, TetherActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        TetherManager.onAction(player, payload);
                    }
                }));
    }

    private ModNetwork() {}
}
