package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: a rope key was pressed (or released, for Haul). */
public record TetherActionPayload(int action, boolean down) implements CustomPacketPayload {
    public static final int HAUL = 0;
    public static final int LAUNCH = 1;
    public static final int ENDER_CALL = 2;
    public static final int UNHOOK = 3;

    public static final Type<TetherActionPayload> TYPE = new Type<>(Tethered.id("action"));
    public static final StreamCodec<FriendlyByteBuf, TetherActionPayload> CODEC =
            CustomPacketPayload.codec(TetherActionPayload::write, TetherActionPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeBoolean(down);
    }

    private static TetherActionPayload read(FriendlyByteBuf buf) {
        return new TetherActionPayload(buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
