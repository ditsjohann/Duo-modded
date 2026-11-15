package com.duomodded.tethered.network;

import com.duomodded.tethered.client.ClientJournalState;
import com.duomodded.tethered.client.ClientTetherState;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.world.IslandManager;
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
        registrar.playToClient(JournalSyncPayload.TYPE, JournalSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientJournalState.set(payload.json())));

        registrar.playToServer(TetherActionPayload.TYPE, TetherActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        TetherManager.onAction(player, payload);
                    }
                }));
        registrar.playToServer(JournalActionPayload.TYPE, JournalActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    switch (payload.action()) {
                        case JournalActionPayload.SUBMIT -> QuestManager.submit(player, payload.arg());
                        case JournalActionPayload.TRAVEL -> IslandManager.travel(player, payload.arg());
                        default -> { }
                    }
                }));
    }

    private ModNetwork() {}
}
