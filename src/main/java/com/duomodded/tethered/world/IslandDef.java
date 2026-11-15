package com.duomodded.tethered.world;

import com.duomodded.tethered.heart.RingSpec;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;

/**
 * One named island on the world map, loaded from data/[namespace]/tethered_islands/[id].json.
 * Islands are built the first time someone comes near, or when the Heart Tree unlocks them.
 */
public record IslandDef(
        String id,
        String name,
        String description,
        int color,
        int order,
        BlockPos center,
        int radius,
        boolean rootway,
        RingSpec spec
) {
    public static IslandDef parse(String id, JsonObject o) {
        JsonArray pos = GsonHelper.getAsJsonArray(o, "pos");
        BlockPos center = new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt());
        String color = GsonHelper.getAsString(o, "color", "#FFB6C1").replace("#", "");
        JsonObject island = GsonHelper.getAsJsonObject(o, "island", new JsonObject());
        return new IslandDef(
                id,
                GsonHelper.getAsString(o, "name", id),
                GsonHelper.getAsString(o, "description", ""),
                Integer.parseInt(color, 16),
                GsonHelper.getAsInt(o, "order", 0),
                center,
                Math.max(6, Math.min(30, GsonHelper.getAsInt(o, "radius", 16))),
                GsonHelper.getAsBoolean(o, "rootway", true),
                RingSpec.parse(island));
    }

    public double horizontalDistance(double x, double z) {
        double dx = x - center.getX();
        double dz = z - center.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
