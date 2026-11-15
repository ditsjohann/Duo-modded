package com.duomodded.tethered;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TetherConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AUTO_PAIR;
    public static final ModConfigSpec.DoubleValue PULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue LAUNCH_POWER;
    public static final ModConfigSpec.IntValue ENDER_CALL_COOLDOWN;

    public static final ModConfigSpec.BooleanValue DOWNED_ENABLED;
    public static final ModConfigSpec.IntValue DOWNED_SECONDS;
    public static final ModConfigSpec.IntValue REVIVE_SECONDS;

    public static final ModConfigSpec.BooleanValue GENERATE_START_ISLAND;
    public static final ModConfigSpec.BooleanValue REQUIRE_BOTH_AT_TREE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("tether");
        AUTO_PAIR = b.comment("Automatically tie two untethered players together when they join.")
                .define("autoPair", true);
        PULL_STRENGTH = b.comment("How hard the rope pulls when stretched past its length.")
                .defineInRange("pullStrength", 0.25, 0.01, 2.0);
        LAUNCH_POWER = b.comment("How far a braced player throws their partner with the Launch key.")
                .defineInRange("launchPower", 1.4, 0.2, 4.0);
        ENDER_CALL_COOLDOWN = b.comment("Seconds between Ender Call teleports (Ender Thread and up).")
                .defineInRange("enderCallCooldownSeconds", 60, 0, 3600);
        b.pop();

        b.push("downed");
        DOWNED_ENABLED = b.comment("Instead of dying, a tethered player goes down and can be revived by their partner.")
                .define("enabled", true);
        DOWNED_SECONDS = b.comment("How long a downed player can wait for a revive.")
                .defineInRange("downedSeconds", 30, 5, 600);
        REVIVE_SECONDS = b.comment("How long the partner must crouch next to a downed player to revive them.")
                .defineInRange("reviveSeconds", 3, 1, 60);
        b.pop();

        b.push("world");
        GENERATE_START_ISLAND = b.comment("Build the starting island when a Tethered Sky (void) world is created.")
                .define("generateStartIsland", true);
        REQUIRE_BOTH_AT_TREE = b.comment("The Heart Tree only grows when both tethered partners are near it.")
                .define("requireBothAtTree", true);
        b.pop();

        SPEC = b.build();
    }

    private TetherConfig() {}
}
