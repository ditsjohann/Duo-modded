package com.duomodded.tethered.item;

import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Right-click another player to tie yourselves together. */
public class RedThreadItem extends Item {
    public RedThreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Player)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer self) || !(target instanceof ServerPlayer other) || self.getServer() == null) {
            return InteractionResult.PASS;
        }

        TetherData data = TetherData.get(self.getServer());
        if (data.getLink(self.getUUID()) != null || data.getLink(other.getUUID()) != null) {
            self.displayClientMessage(Component.translatable("msg.tethered.already_tied").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        TetherManager.pair(self, other, RopeTier.TWINE);
        if (!self.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tethered.red_thread.desc").withStyle(ChatFormatting.GRAY));
    }
}
