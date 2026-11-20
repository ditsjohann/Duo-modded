package com.duomodded.tethered.world;

import com.duomodded.tethered.age.Heartstone;
import com.duomodded.tethered.registry.ModRegistry;
import com.duomodded.tethered.tether.TetherData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Remnant: a fossilised scrap of the old world hidden in the Last Stone. Breaking one spills a
 * few things the Heartstone remembers from the current Age, plus some Lost Threads.
 */
public class RemnantBlock extends Block {
    public RemnantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        int age = TetherData.get(level.getServer()).age();
        for (ItemStack stack : Heartstone.rollCache(age, level.random, 2 + level.random.nextInt(2))) {
            popResource(level, pos, stack);
        }
        popResource(level, pos, new ItemStack(ModRegistry.LOST_THREAD.get(), 1 + level.random.nextInt(3)));
        if (dropExperience) popExperience(level, pos, 4 + level.random.nextInt(6));
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.3, 0.3, 0.3, 0.25);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1f, 1.4f);
    }
}
