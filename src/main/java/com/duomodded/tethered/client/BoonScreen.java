package com.duomodded.tethered.client;

import com.duomodded.tethered.network.BoonChoosePayload;
import com.duomodded.tethered.network.BoonOfferPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/** "The Weaver offers you a Boon": three cards, click one. */
public class BoonScreen extends Screen {
    private static final int CARD_W = 118;
    private static final int CARD_H = 150;
    private final BoonOfferPayload offer;

    public BoonScreen(BoonOfferPayload offer) {
        super(Component.translatable("screen.tethered.boons"));
        this.offer = offer;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int cardX(int i) {
        int n = offer.ids().size();
        int total = n * CARD_W + (n - 1) * 10;
        return (width - total) / 2 + i * (CARD_W + 10);
    }

    private int cardY() {
        return (height - CARD_H) / 2 + 10;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, cardY() - 28, 0xFFF2C94C);
        g.drawCenteredString(font, Component.translatable("screen.tethered.boons.hint"), width / 2, cardY() - 16, 0xFFCFC8D6);
        for (int i = 0; i < offer.ids().size(); i++) {
            String id = offer.ids().get(i);
            int x = cardX(i);
            int y = cardY();
            boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            g.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xF04A3346 : 0xF01C1722);
            g.renderOutline(x, y, CARD_W, CARD_H, hover ? 0xFFF2C94C : 0xFFB07AA1);
            int rank = offer.ranks().get(i);
            int max = offer.maxRanks().get(i);
            Component name = Component.translatable("boon.tethered." + id);
            int ty = y + 10;
            for (FormattedCharSequence line : font.split(name, CARD_W - 12)) {
                g.drawCenteredString(font, line, x + CARD_W / 2, ty, 0xFFF2C94C);
                ty += 10;
            }
            String stars = "★".repeat(rank) + "☆".repeat(Math.max(0, max - rank));
            g.drawCenteredString(font, stars, x + CARD_W / 2, ty + 2, 0xFFFFB6D5);
            ty += 18;
            for (FormattedCharSequence line : font.split(Component.translatable("boon.tethered." + id + ".desc"), CARD_W - 14)) {
                g.drawString(font, line, x + 7, ty, 0xFFCFC8D6, false);
                ty += 10;
            }
            Component rankText = Component.translatable("screen.tethered.boons.rank", rank, rank + 1, max);
            g.drawCenteredString(font, rankText, x + CARD_W / 2, y + CARD_H - 14, hover ? 0xFFFFFFFF : 0xFF8A8494);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < offer.ids().size(); i++) {
                int x = cardX(i);
                int y = cardY();
                if (mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    PacketDistributor.sendToServer(new BoonChoosePayload(offer.ids().get(i)));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
