package com.duomodded.tethered.item;

import com.duomodded.tethered.tether.RopeTier;
import com.duomodded.tethered.tether.TetherData;
import com.duomodded.tethered.tether.TetherManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Use it to upgrade the rope you share with your partner. */
public class TetherUpgradeItem extends Item {
    private final RopeTier tier;

    public TetherUpgradeItem(RopeTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp) || sp.getServer() == null) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        TetherData data = TetherData.get(sp.getServer());
        TetherData.Link link = data.getLink(sp.getUUID());
        if (link == null) {
            sp.displayClientMessage(Component.translatable("msg.tethered.not_tied").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        int needed = tier.level; // Leather needs tree stage 2, Chain 3, Ender 4 (Golden comes from the ending)
        if (tier != RopeTier.GOLDEN && data.heartStage() < needed) {
            sp.displayClientMessage(Component.translatable("msg.tethered.tree_too_young", needed).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        if (link.tier >= tier.level) {
            sp.displayClientMessage(Component.translatable("msg.tethered.already_strong").withStyle(ChatFormatting.YELLOW), true);
            return InteractionResultHolder.fail(stack);
        }
        link.setTier(tier.level);
        data.setDirty();
        Component msg = Component.translatable("msg.tethered.upgraded", tier.displayName(), (int) tier.length).withStyle(ChatFormatting.LIGHT_PURPLE);
        sp.sendSystemMessage(msg);
        ServerPlayer partner = TetherManager.partnerOf(sp);
        if (partner != null) partner.sendSystemMessage(msg);
        level.playSound(null, sp.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1f);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        TetherManager.sync(sp);
        if (partner != null) TetherManager.sync(partner);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tethered.upgrade.desc", (int) tier.length).withStyle(ChatFormatting.GRAY));
        if (tier == RopeTier.ENDER) {
            tooltip.add(Component.translatable("item.tethered.ender_thread.desc").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (tier == RopeTier.GOLDEN) {
            tooltip.add(Component.translatable("item.tethered.golden_thread.desc").withStyle(ChatFormatting.GOLD));
        }
    }
}
