package com.duomodded.tethered.network;

import com.duomodded.tethered.boon.Boons;
import com.duomodded.tethered.client.ClientJournalState;
import com.duomodded.tethered.client.ClientTetherState;
import com.duomodded.tethered.quest.QuestManager;
import com.duomodded.tethered.tether.TetherManager;
import com.duomodded.tethered.world.IslandManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        // ClientTetherState only stores the payload (no client-only classes), so this is safe on servers.
        registrar.playToClient(TetherStatePayload.TYPE, TetherStatePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientTetherState.set(payload)));
        registrar.playToClient(JournalSyncPayload.TYPE, JournalSyncPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientJournalState.set(payload.json())));
        // Only ever handled on a client, so the client-only screen class is never loaded on a server.
        registrar.playToClient(BoonOfferPayload.TYPE, BoonOfferPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.duomodded.tethered.client.ClientAccess.openBoons(payload)));

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
                        case JournalActionPayload.BOONS -> Boons.openOffer(player);
                        default -> { }
                    }
                }));
        registrar.playToServer(BoonChoosePayload.TYPE, BoonChoosePayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Boons.choose(player, payload.id());
                }));
    }

    private ModNetwork() {}
}
