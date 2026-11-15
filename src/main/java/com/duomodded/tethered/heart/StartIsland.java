package com.duomodded.tethered.heart;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.util.List;

/** The small island you both wake up on, with the Heart Tree sapling and a starter chest. */
public final class StartIsland {
    public static final BlockPos CENTER = new BlockPos(0, 64, 0);

    /** Called when the server starts: builds the island once, only in a void world. */
    public static void maybeGenerate(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TetherData data = TetherData.get(server);
        if (data.startIslandGenerated() || !TetherConfig.GENERATE_START_ISLAND.get() || !isVoidWorld(level)) return;
        generate(level, CENTER);
        data.setStartIslandGenerated();
        Tethered.LOGGER.info("Built the Tethered starting island at {}", CENTER);
    }

    public static boolean isVoidWorld(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof FlatLevelSource flat
                && flat.settings().getLayers().stream().allMatch(s -> s == null || s.isAir()); // air layers are stored as null
    }

    public static void generate(ServerLevel level, BlockPos center) {
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x7E7E7EL);
        IslandBuilder.buildIsland(level, center, RingSpec.starter(), 6, random);

        // Make the middle flat and solid so the tree and spawn are safe
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                level.setBlock(center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(center.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), 3);
                for (int y = 1; y <= 3; y++) level.setBlock(center.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        BlockPos heart = center.above();
        level.setBlock(heart, ModRegistry.HEART_TREE.get().defaultBlockState(), 3);
        TetherData.get(level.getServer()).setHeartTreePos(heart);

        // One oak tree for wood
        ConfiguredFeature<?, ?> oak = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.withDefaultNamespace("oak"));
        BlockPos treeSpot = center.offset(4, 1, -2);
        if (!level.getBlockState(treeSpot.below()).is(Blocks.GRASS_BLOCK)) {
            level.setBlock(treeSpot.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        }
        if (oak != null) oak.place(level, level.getChunkSource().getGenerator(), random, treeSpot);

        // Starter chest
        BlockPos chestPos = center.offset(-2, 1, 2);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            List<ItemStack> kit = List.of(
                    weaverNote(),
                    new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(Items.LAVA_BUCKET),
                    new ItemStack(Items.ICE, 2),
                    new ItemStack(Items.BONE_MEAL, 16),
                    new ItemStack(Items.WHEAT_SEEDS, 4),
                    new ItemStack(Items.OAK_SAPLING, 2),
                    new ItemStack(Items.BREAD, 8),
                    new ItemStack(Items.TORCH, 8),
                    new ItemStack(Items.SUGAR_CANE, 1),
                    new ItemStack(Items.MELON_SEEDS, 1),
                    new ItemStack(Items.PUMPKIN_SEEDS, 1),
                    new ItemStack(ModRegistry.RED_THREAD.get(), 1));
            for (int i = 0; i < kit.size(); i++) chest.setItem(i, kit.get(i));
        }

        BlockPos spawn = center.offset(0, 1, 2);
        level.setDefaultSpawnPos(spawn, 0f);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
    }

    private static ItemStack weaverNote() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
                page("The Weaver's Note\n\nTwo hearts, one thread.\n\nI have tied you together and set you on this little island in the sky. Do not let go of each other."),
                page("THE ROPE\n\nYou can never be far apart. If one of you falls, the other holds on.\n\nCrouch to BRACE.\nHold R to HAUL your partner up.\nCrouch + G throws your partner across a gap."),
                page("THE HEART TREE\n\nThe glowing heart in the middle is a seed. Right-click it to see what it wants.\n\nIt only grows when you are BOTH beside it."),
                page("THE ISLANDS\n\nEach time it grows, a root bridge reaches out to a faraway island. Some are very far.\n\nFinish an island's tasks together and it gives you a Heart Shard. Bring it home."),
                page("YOUR JOURNAL\n\nPress J for the map, your quests and the tree's blessings.\n\nAttune the Anchor Stone on each island together, and the tree can carry you back and forth."),
                page("IF YOU FALL\n\nIf one of you goes down, the other has a few seconds to crouch next to them and help them up.\n\nThe void forgives no one."),
                page("Water and lava make stone.\nIce melts into water.\nTrees give more trees.\n\nWhen the tree blooms, I will ask you one question.\n\n- The Weaver"));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("The Weaver's Note"), "The Weaver", 0, pages, true));
        return book;
    }

    private static Filterable<Component> page(String text) {
        return Filterable.passThrough(Component.literal(text));
    }

    private StartIsland() {}
}
