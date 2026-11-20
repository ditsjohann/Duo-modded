package com.duomodded.tethered.network;

import com.duomodded.tethered.Tethered;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> client: the three Boons a player may pick from, with their current and max ranks. */
public record BoonOfferPayload(List<String> ids, List<Integer> ranks, List<Integer> maxRanks) implements CustomPacketPayload {
    public static final Type<BoonOfferPayload> TYPE = new Type<>(Tethered.id("boon_offer"));
    public static final StreamCodec<FriendlyByteBuf, BoonOfferPayload> CODEC =
            CustomPacketPayload.codec(BoonOfferPayload::write, BoonOfferPayload::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            buf.writeUtf(ids.get(i), 64);
            buf.writeVarInt(ranks.get(i));
            buf.writeVarInt(maxRanks.get(i));
        }
    }

    private static BoonOfferPayload read(FriendlyByteBuf buf) {
        int n = Math.min(8, buf.readVarInt());
        List<String> ids = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        List<Integer> max = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(64));
            ranks.add(buf.readVarInt());
            max.add(buf.readVarInt());
        }
        return new BoonOfferPayload(ids, ranks, max);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
