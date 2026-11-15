package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a button in the journal (hand in items for a quest, or travel somewhere). */
public record JournalActionPayload(int action, String arg) implements CustomPacketPayload {
    public static final int SUBMIT = 0;
    public static final int TRAVEL = 1;

    public static final Type<JournalActionPayload> TYPE = new Type<>(Tethered.id("journal_action"));
    public static final StreamCodec<FriendlyByteBuf, JournalActionPayload> CODEC =
            CustomPacketPayload.codec(JournalActionPayload::write, JournalActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUtf(arg, 256);
    }

    private static JournalActionPayload read(FriendlyByteBuf buf) {
        return new JournalActionPayload(buf.readVarInt(), buf.readUtf(256));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
