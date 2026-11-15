package com.duomodded.tethered.tether;

import net.minecraft.network.chat.Component;

/** Rope upgrades. Each tier is longer; Ender adds Ender Call, Golden adds Unhook. */
public enum RopeTier {
    TWINE(1, "twine", 8f, 0xD2333F),
    LEATHER(2, "leather", 12f, 0x8B5A2B),
    CHAIN(3, "chain", 16f, 0x9AA3AD),
    ENDER(4, "ender", 24f, 0x2FB89A),
    GOLDEN(5, "golden", 32f, 0xF2C94C);

    public final int level;
    public final String key;
    public final float length;
    public final int color;

    RopeTier(int level, String key, float length, int color) {
        this.level = level;
        this.key = key;
        this.length = length;
        this.color = color;
    }

    public Component displayName() {
        return Component.translatable("tier.tethered." + key);
    }

    public static RopeTier byLevel(int level) {
        for (RopeTier t : values()) {
            if (t.level == level) return t;
        }
        return level > GOLDEN.level ? GOLDEN : TWINE;
    }
}
