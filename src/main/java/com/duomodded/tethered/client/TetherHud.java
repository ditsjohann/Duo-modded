package com.duomodded.tethered.client;

import com.duomodded.tethered.network.TetherStatePayload;
import com.duomodded.tethered.tether.RopeTier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Top-left partner panel, plus a big banner while you or your partner are down. */
public final class TetherHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        TetherStatePayload s = ClientTetherState.get();
        if (me == null || mc.options.hideGui || !s.linked()) return;

        Font font = mc.font;
        RopeTier tier = RopeTier.byLevel(s.tier());
        int x = 6;
        int y = 6;

        // Line 1: partner, distance and direction
        Component who;
        if (!s.partnerOnline()) {
            who = Component.translatable("hud.tethered.offline", s.partnerName());
        } else if (!s.sameLevel()) {
            who = Component.translatable("hud.tethered.other_dim", s.partnerName());
        } else {
            double dx = s.px() - me.getX();
            double dy = s.py() - me.getY();
            double dz = s.pz() - me.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
            float rel = Mth.wrapDegrees(targetYaw - me.getYRot());
            String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
            who = Component.literal("❤ " + s.partnerName() + "  " + String.format("%.1fm ", dist) + arrow);
        }
        g.drawString(font, who, x, y, 0xFFFFB6C1, true);
        y += 11;

        if (s.partnerOnline()) {
            String hp = String.format("♥ %.0f / %.0f", s.partnerHealth(), s.partnerMaxHealth());
            g.drawString(font, hp, x, y, 0xFFFF5555, true);
            y += 11;
        }

        Component rope = Component.translatable("hud.tethered.rope", tier.displayName(),
                String.format("%.1f", s.currentLength()), String.format("%.0f", s.maxLength()));
        g.drawString(font, rope, x, y, 0xFF000000 | tier.color, true);
        y += 11;

        if (s.slack()) {
            g.drawString(font, Component.translatable("hud.tethered.slack"), x, y, 0xFFF2C94C, true);
            y += 11;
        } else if (me.isShiftKeyDown() && me.onGround()) {
            g.drawString(font, Component.translatable("hud.tethered.braced"), x, y, 0xFF88DDFF, true);
            y += 11;
        } else if (s.partnerBraced()) {
            g.drawString(font, Component.translatable("hud.tethered.partner_braced"), x, y, 0xFF88DDFF, true);
            y += 11;
        }

        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        if (s.partnerDowned()) {
            Component msg = Component.translatable("hud.tethered.partner_down", s.partnerName());
            g.drawCenteredString(font, msg, cx, cy + 20, 0xFFFF5555);
            bar(g, cx, cy + 32, s.reviveProgress(), s.reviveTotal(), 0xFF55FF55);
        }
        if (s.selfDowned()) {
            int secs = s.downedTicks() / 20 + 1;
            g.drawCenteredString(font, Component.translatable("hud.tethered.you_down", secs), cx, cy - 40, 0xFFFF5555);
            g.drawCenteredString(font, Component.translatable("hud.tethered.you_down_hint", s.partnerName()), cx, cy - 28, 0xFFFFFFFF);
            bar(g, cx, cy - 16, s.reviveProgress(), s.reviveTotal(), 0xFF55FF55);
        }
    }

    private static void bar(GuiGraphics g, int cx, int y, int value, int total, int color) {
        if (value <= 0) return;
        int width = 80;
        int filled = (int) (width * Math.min(1.0, value / (double) Math.max(1, total)));
        g.fill(cx - width / 2 - 1, y - 1, cx + width / 2 + 1, y + 4, 0xAA000000);
        g.fill(cx - width / 2, y, cx - width / 2 + filled, y + 3, color);
    }

    private TetherHud() {}
}
