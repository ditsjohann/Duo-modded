package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** Server -> client: everything the client needs for rope physics, rendering and the HUD. */
public record TetherStatePayload(
        boolean linked,
        UUID partner,
        String partnerName,
        boolean partnerOnline,
        int tier,
        float currentLength,
        float maxLength,
        boolean slack,
        boolean partnerBraced,
        boolean partnerDowned,
        float partnerHealth,
        float partnerMaxHealth,
        boolean sameLevel,
        double px,
        double py,
        double pz,
        boolean selfDowned,
        int downedTicks,
        int reviveProgress,
        int reviveTotal
) implements CustomPacketPayload {

    public static final Type<TetherStatePayload> TYPE = new Type<>(Tethered.id("state"));
    public static final StreamCodec<FriendlyByteBuf, TetherStatePayload> CODEC =
            CustomPacketPayload.codec(TetherStatePayload::write, TetherStatePayload::read);

    public static final TetherStatePayload NONE = new TetherStatePayload(false, new UUID(0L, 0L), "", false,
            0, 0f, 0f, false, false, false, 0f, 20f, false, 0, 0, 0, false, 0, 0, 1);

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(linked);
        buf.writeUUID(partner);
        buf.writeUtf(partnerName);
        buf.writeBoolean(partnerOnline);
        buf.writeVarInt(tier);
        buf.writeFloat(currentLength);
        buf.writeFloat(maxLength);
        buf.writeBoolean(slack);
        buf.writeBoolean(partnerBraced);
        buf.writeBoolean(partnerDowned);
        buf.writeFloat(partnerHealth);
        buf.writeFloat(partnerMaxHealth);
        buf.writeBoolean(sameLevel);
        buf.writeDouble(px);
        buf.writeDouble(py);
        buf.writeDouble(pz);
        buf.writeBoolean(selfDowned);
        buf.writeVarInt(downedTicks);
        buf.writeVarInt(reviveProgress);
        buf.writeVarInt(reviveTotal);
    }

    private static TetherStatePayload read(FriendlyByteBuf buf) {
        return new TetherStatePayload(
                buf.readBoolean(),
                buf.readUUID(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
