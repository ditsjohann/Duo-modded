package com.duomodded.tethered.world;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sets up a new Tethered world once: carves the Hollow Heart (the chamber at the core where you both
 * wake up), places the Heartstone, a starter chest and lights, and plants the World Tree's heart on
 * the summit of the Last Stone.
 */
public final class LastStone {
    public static final int CHAMBER_RADIUS = 9;

    @Nullable
    public static LastStoneGenerator generator(ServerLevel level) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        return gen instanceof LastStoneGenerator g ? g : null;
    }

    public static boolean isLastStoneWorld(ServerLevel level) {
        return generator(level) != null;
    }

    public static void maybeGenerate(MinecraftServer server) {
        ServerLevel level = server.overworld();
        TetherData data = TetherData.get(server);
        LastStoneGenerator gen = generator(level);
        if (data.startIslandGenerated() || !TetherConfig.GENERATE_START_ISLAND.get() || gen == null) return;
        BlockPos core = new BlockPos(0, gen.centerY() - 1, 0);
        generate(level, gen, core);
        data.setStartIslandGenerated();
        Tethered.LOGGER.info("Carved the Hollow Heart at {} (Heartstone)", core);
    }

    public static void generate(ServerLevel level, LastStoneGenerator gen, BlockPos core) {
        TetherData data = TetherData.get(level.getServer());
        carveChamber(level, core);
        level.setBlock(core, ModRegistry.HEARTSTONE.get().defaultBlockState(), 3);
        data.setHeartPos(core);
        data.dimensions().add("minecraft:overworld");

        // Starter chest
        BlockPos chestPos = core.offset(-3, 1, 3);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            List<ItemStack> kit = List.of(
                    weaverNote(),
                    new ItemStack(Items.WATER_BUCKET),
                    new ItemStack(Items.TORCH, 12),
                    new ItemStack(Items.BREAD, 8),
                    new ItemStack(Items.ICE, 2),
                    new ItemStack(Items.BONE_MEAL, 8),
                    new ItemStack(Items.OAK_SAPLING, 1),
                    new ItemStack(Items.WHEAT_SEEDS, 4),
                    new ItemStack(ModRegistry.RED_THREAD.get(), 1));
            for (int i = 0; i < kit.size(); i++) chest.setItem(i, kit.get(i));
        }

        // The World Tree's heart on the summit
        int top = gen.topAt(0, 0);
        if (top > level.getMinBuildHeight()) {
            BlockPos summit = new BlockPos(0, top, 0);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx * dx + dz * dz > 18) continue;
                    BlockPos surface = topSolid(level, dx, dz, top + 4);
                    if (surface == null) continue;
                    level.setBlock(surface, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                    level.setBlock(surface.below(), Blocks.DIRT.defaultBlockState(), 3);
                }
            }
            BlockPos tree = topSolid(level, 0, 0, top + 4);
            if (tree == null) tree = summit;
            level.setBlock(tree.above(), ModRegistry.HEART_TREE.get().defaultBlockState(), 3);
            data.setHeartTreePos(tree.above());
        }

        level.setDefaultSpawnPos(core.offset(0, 1, 4), 0f);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());

        Heartstone.regen(level, false); // the first block (the first memory)
    }

    @Nullable
    private static BlockPos topSolid(ServerLevel level, int x, int z, int fromY) {
        for (int y = fromY; y > fromY - 24; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(pos);
            if (!s.isAir() && s.isSolidRender(level, pos)) return pos;
        }
        return null;
    }

    /** A dome-shaped room around the core, with a patterned floor and some light. */
    private static void carveChamber(ServerLevel level, BlockPos core) {
        int r = CHAMBER_RADIUS;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double h = (dx * dx + dz * dz) / (r * r + 0.5);
                if (h >= 1) continue;
                // floor
                int ring = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                BlockState floor = ring <= 1 ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                        : ring % 3 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                level.setBlock(core.offset(dx, 0, dz), floor, 2);
                level.setBlock(core.offset(dx, -1, dz), Blocks.DEEPSLATE.defaultBlockState(), 2);
                for (int dy = 1; dy <= 9; dy++) {
                    double d = h + ((dy - 1) * (dy - 1)) / 64.0;
                    if (d < 1) level.setBlock(core.offset(dx, dy, dz), air, 2);
                }
            }
        }
        // Light: a ring of froglights in the floor and lanterns hanging from the ceiling
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            BlockPos p = core.offset((int) Math.round(Math.cos(a) * 7), 0, (int) Math.round(Math.sin(a) * 7));
            level.setBlock(p, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState(), 2);
        }
        for (int[] o : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            for (int dy = 2; dy <= 12; dy++) {
                BlockPos p = core.offset(o[0], dy, o[1]);
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p.below(), Blocks.LANTERN.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
                    break;
                }
            }
        }
        // Somewhere to sit
        level.setBlock(core.offset(3, 1, -3), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
    }

    private static ItemStack weaverNote() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
                page("The Weaver's Last Note\n\nIf you are reading this, my thread held.\n\nThe world is gone. The Unraveler cut it apart. This stone is all that's left, and you two are inside it."),
                page("THE HEARTSTONE\n\nThe glowing stone in the middle is the last knot of the world. Break the block on top of it.\n\nIt will remember something and weave it back. Again, and again."),
                page("THE AGES\n\nRemember enough and the Heartstone will ask for an offering. Give it, then face its Trial together.\n\nEach Trial you win brings back an Age of the world."),
                page("THE STONE\n\nMine out from here. Deeper rock hides richer ore, Thread Ore and old Remnants.\n\nThe tunnels are dark. Things live there. Bring light."),
                page("THE HOLLOW\n\nAt night the Unraveler sends his Hollow after the thread you're re-weaving.\n\nStay close. If one of you falls, the other can crouch beside them and help them up."),
                page("FAVORS\n\nEvery Age I'll send you a Weaver's Favor. Use it to choose a Boon.\n\nPress J for your journal: the Heartstone, your quests, the map and the story so far."),
                page("At the very top of this stone my Tree still sleeps. It will wake as the world comes back.\n\nRemember the world for me.\n\n- The Weaver"));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("The Weaver's Last Note"), "The Weaver", 0, pages, true));
        return book;
    }

    private static Filterable<Component> page(String text) {
        return Filterable.passThrough(Component.literal(text));
    }

    private LastStone() {}
}
