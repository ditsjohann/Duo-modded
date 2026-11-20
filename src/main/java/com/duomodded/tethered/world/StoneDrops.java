package com.duomodded.tethered.world;

import com.duomodded.tethered.Tethered;
import com.duomodded.tethered.boon.Boons;
import com.duomodded.tethered.registry.ModRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Threadfinder Boon: extra Lost Threads from Thread Ore. */
@EventBusSubscriber(modid = Tethered.MODID)
public final class StoneDrops {
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!event.getState().is(ModRegistry.THREAD_ORE.get()) && !event.getState().is(ModRegistry.DEEPSLATE_THREAD_ORE.get())) return;
        int bonus = Boons.bonusThreads(event.getPlayer(), level.random);
        if (bonus > 0) Block.popResource(level, event.getPos(), new ItemStack(ModRegistry.LOST_THREAD.get(), bonus));
    }

    private StoneDrops() {}
}
