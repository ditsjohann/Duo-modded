package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: the journal's contents (map, quests, tree) as a JSON string. */
public record JournalSyncPayload(String json) implements CustomPacketPayload {
    private static final int MAX = 1 << 20;

    public static final Type<JournalSyncPayload> TYPE = new Type<>(Tethered.id("journal"));
    public static final StreamCodec<FriendlyByteBuf, JournalSyncPayload> CODEC =
            CustomPacketPayload.codec(JournalSyncPayload::write, JournalSyncPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(json, MAX);
    }

    private static JournalSyncPayload read(FriendlyByteBuf buf) {
        return new JournalSyncPayload(buf.readUtf(MAX));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
