package com.duomodded.tethered;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TetherConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AUTO_PAIR;
    public static final ModConfigSpec.BooleanValue ROPE_PHYSICS;
    public static final ModConfigSpec.DoubleValue PULL_STRENGTH;
    public static final ModConfigSpec.DoubleValue LAUNCH_POWER;
    public static final ModConfigSpec.IntValue ENDER_CALL_COOLDOWN;

    public static final ModConfigSpec.BooleanValue DOWNED_ENABLED;
    public static final ModConfigSpec.IntValue DOWNED_SECONDS;
    public static final ModConfigSpec.IntValue REVIVE_SECONDS;

    public static final ModConfigSpec.BooleanValue GENERATE_START_ISLAND;
    public static final ModConfigSpec.BooleanValue REQUIRE_BOTH_AT_TREE;
    public static final ModConfigSpec.DoubleValue MEMORY_MULTIPLIER;

    public static final ModConfigSpec.BooleanValue EVENTS_ENABLED;
    public static final ModConfigSpec.DoubleValue NIGHT_EVENT_CHANCE;
    public static final ModConfigSpec.DoubleValue DAY_EVENT_CHANCE;
    public static final ModConfigSpec.IntValue EVENT_GAP_MINUTES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("tether");
        AUTO_PAIR = b.comment("Automatically bond two players with the Red Thread when they join.")
                .define("autoPair", true);
        ROPE_PHYSICS = b.comment("Old 'rope mode': a physical rope that pulls you together, lets you haul and launch.",
                        "Off by default; the bond (revive, Thread Call, shared Boons) works either way.")
                .define("ropePhysics", false);
        PULL_STRENGTH = b.comment("Rope mode only: how hard the rope pulls when stretched past its length.")
                .defineInRange("pullStrength", 0.25, 0.01, 2.0);
        LAUNCH_POWER = b.comment("Rope mode only: how far a braced player throws their partner with the Launch key.")
                .defineInRange("launchPower", 1.4, 0.2, 4.0);
        ENDER_CALL_COOLDOWN = b.comment("Seconds between Thread Call teleports to your partner.")
                .defineInRange("enderCallCooldownSeconds", 60, 0, 3600);
        b.pop();

        b.push("downed");
        DOWNED_ENABLED = b.comment("Instead of dying, a bonded player goes down and can be revived by their partner.")
                .define("enabled", true);
        DOWNED_SECONDS = b.comment("How long a downed player can wait for a revive.")
                .defineInRange("downedSeconds", 30, 5, 600);
        REVIVE_SECONDS = b.comment("How long the partner must crouch next to a downed player to revive them.")
                .defineInRange("reviveSeconds", 3, 1, 60);
        b.pop();

        b.push("world");
        GENERATE_START_ISLAND = b.comment("Carve the Hollow Heart and place the Heartstone when a Tethered world is created.")
                .define("generateStartIsland", true);
        REQUIRE_BOTH_AT_TREE = b.comment("Trials only start when both bonded partners are at the Heartstone.")
                .define("requireBothAtTree", true);
        MEMORY_MULTIPLIER = b.comment("Multiplies how many Heartstone breaks each Age needs (0.5 = faster story, 2 = slower).")
                .defineInRange("memoryMultiplier", 1.0, 0.05, 10.0);
        b.pop();

        b.push("events");
        EVENTS_ENABLED = b.comment("Random world events (Hollow Tides, Threadfall, the Wandering Weaver, storms...).")
                .define("enabled", true);
        NIGHT_EVENT_CHANCE = b.comment("Chance that something happens when night falls.")
                .defineInRange("nightChance", 0.55, 0.0, 1.0);
        DAY_EVENT_CHANCE = b.comment("Chance, every 3 minutes of daytime, that something happens.")
                .defineInRange("dayChance", 0.12, 0.0, 1.0);
        EVENT_GAP_MINUTES = b.comment("Minimum real minutes between two events.")
                .defineInRange("minMinutesBetween", 8, 0, 240);
        b.pop();

        SPEC = b.build();
    }

    private TetherConfig() {}
}
