package com.duomodded.tethered.registry;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.heart.HeartTreeBlock;
import com.duomodded.tethered.heart.HeartTreeBlockEntity;
import com.duomodded.tethered.item.JournalItem;
import com.duomodded.tethered.item.RedThreadItem;
import com.duomodded.tethered.item.TetherUpgradeItem;
import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.world.AnchorStoneBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Tethered.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Tethered.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Tethered.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tethered.MODID);

    // Blocks
    public static final DeferredBlock<HeartTreeBlock> HEART_TREE = BLOCKS.register("heart_tree",
            () -> new HeartTreeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PINK)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.CHERRY_WOOD)
                    .lightLevel(state -> 10)
                    .noLootTable()));
    public static final DeferredBlock<RotatedPillarBlock> HEARTWOOD_LOG = BLOCKS.register("heartwood_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LOG)));
    public static final DeferredBlock<LeavesBlock> HEART_LEAVES = BLOCKS.register("heart_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES)));

    public static final DeferredBlock<AnchorStoneBlock> ANCHOR_STONE = BLOCKS.register("anchor_stone",
            () -> new AnchorStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 12)
                    .noLootTable()));

    // Block items
    public static final DeferredItem<BlockItem> HEART_TREE_ITEM = ITEMS.registerSimpleBlockItem(HEART_TREE);
    public static final DeferredItem<BlockItem> HEARTWOOD_LOG_ITEM = ITEMS.registerSimpleBlockItem(HEARTWOOD_LOG);
    public static final DeferredItem<BlockItem> HEART_LEAVES_ITEM = ITEMS.registerSimpleBlockItem(HEART_LEAVES);
    public static final DeferredItem<BlockItem> ANCHOR_STONE_ITEM = ITEMS.registerSimpleBlockItem(ANCHOR_STONE);

    // Items
    public static final DeferredItem<Item> RED_THREAD = ITEMS.register("red_thread",
            () -> new RedThreadItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> LEATHER_TETHER = ITEMS.register("leather_tether",
            () -> new TetherUpgradeItem(RopeTier.LEATHER, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHAIN_TETHER = ITEMS.register("chain_tether",
            () -> new TetherUpgradeItem(RopeTier.CHAIN, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ENDER_THREAD = ITEMS.register("ender_thread",
            () -> new TetherUpgradeItem(RopeTier.ENDER, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> GOLDEN_THREAD = ITEMS.register("golden_thread",
            () -> new TetherUpgradeItem(RopeTier.GOLDEN, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredItem<Item> HEART_SHARD = ITEMS.register("heart_shard",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
    public static final DeferredItem<Item> WEAVERS_JOURNAL = ITEMS.register("weavers_journal",
            () -> new JournalItem(new Item.Properties().stacksTo(1)));

    // Block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeartTreeBlockEntity>> HEART_TREE_BE =
            BLOCK_ENTITIES.register("heart_tree",
                    () -> BlockEntityType.Builder.of(HeartTreeBlockEntity::new, HEART_TREE.get()).build(null));

    // Creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tethered"))
                    .icon(() -> new ItemStack(RED_THREAD.get()))
                    .displayItems((params, output) -> {
                        output.accept(WEAVERS_JOURNAL.get());
                        output.accept(HEART_SHARD.get());
                        output.accept(RED_THREAD.get());
                        output.accept(LEATHER_TETHER.get());
                        output.accept(CHAIN_TETHER.get());
                        output.accept(ENDER_THREAD.get());
                        output.accept(GOLDEN_THREAD.get());
                        output.accept(HEART_TREE_ITEM.get());
                        output.accept(HEARTWOOD_LOG_ITEM.get());
                        output.accept(HEART_LEAVES_ITEM.get());
                        output.accept(ANCHOR_STONE_ITEM.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }

    private ModRegistry() {}
}
