package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the Boon the player picked. */
public record BoonChoosePayload(String id) implements CustomPacketPayload {
    public static final Type<BoonChoosePayload> TYPE = new Type<>(Tethered.id("boon_choose"));
    public static final StreamCodec<FriendlyByteBuf, BoonChoosePayload> CODEC =
            CustomPacketPayload.codec(BoonChoosePayload::write, BoonChoosePayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 64);
    }

    private static BoonChoosePayload read(FriendlyByteBuf buf) {
        return new BoonChoosePayload(buf.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
