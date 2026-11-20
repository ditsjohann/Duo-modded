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
        if (me == null || mc.options.hideGui) return;
        Font font = mc.font;
        if (!s.linked()) {
            int y0 = ageLine(g, font, 6, 6);
            wayfinder(g, font, me, 6, y0);
            return;
        }

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

        if (s.rope()) {
            Component rope = Component.translatable("hud.tethered.rope", tier.displayName(),
                    String.format("%.1f", s.currentLength()), String.format("%.0f", s.maxLength()));
            g.drawString(font, rope, x, y, 0xFF000000 | tier.color, true);
            y += 11;
        }
        y = ageLine(g, font, x, y);

        if (!s.rope()) {
            // no rope: nothing to brace
        } else if (s.slack()) {
            g.drawString(font, Component.translatable("hud.tethered.slack"), x, y, 0xFFF2C94C, true);
            y += 11;
        } else if (me.isShiftKeyDown() && me.onGround()) {
            g.drawString(font, Component.translatable("hud.tethered.braced"), x, y, 0xFF88DDFF, true);
            y += 11;
        } else if (s.partnerBraced()) {
            g.drawString(font, Component.translatable("hud.tethered.partner_braced"), x, y, 0xFF88DDFF, true);
            y += 11;
        }

        wayfinder(g, font, me, x, y);

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

    /** "Age of Roots · 37/80" (or what the Heartstone wants next). */
    private static int ageLine(GuiGraphics g, Font font, int x, int y) {
        com.google.gson.JsonObject data = ClientJournalState.get();
        if (!data.has("ageTitle")) return y;
        String title = data.get("ageTitle").getAsString();
        int breaks = data.has("ageBreaks") ? data.get("ageBreaks").getAsInt() : 0;
        int need = data.has("breaksNeeded") ? data.get("breaksNeeded").getAsInt() : 0;
        String status;
        if (need <= 0) status = "";
        else if (breaks < need) status = "  ·  " + breaks + "/" + need;
        else if (data.has("trialReady") && data.get("trialReady").getAsBoolean()) status = "  ·  " + Component.translatable("hud.tethered.trial").getString();
        else status = "  ·  " + Component.translatable("hud.tethered.offering").getString();
        int color = data.has("ageColor") ? 0xFF000000 | data.get("ageColor").getAsInt() : 0xFFB07AA1;
        g.drawString(font, "⟡ " + title + status, x, y, color, true);
        return y + 11;
    }

    /** "Next: Hearthmeadow 210m ->" for the first unlocked island you haven't attuned yet. */
    private static void wayfinder(GuiGraphics g, Font font, LocalPlayer me, int x, int y) {
        com.google.gson.JsonObject data = ClientJournalState.get();
        if (!data.has("islands") || me.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        for (com.google.gson.JsonElement e : data.getAsJsonArray("islands")) {
            com.google.gson.JsonObject o = e.getAsJsonObject();
            if (!o.get("unlocked").getAsBoolean() || o.get("attuned").getAsBoolean()) continue;
            double dx = o.get("x").getAsDouble() - me.getX();
            double dz = o.get("z").getAsDouble() - me.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
            float rel = Mth.wrapDegrees(targetYaw - me.getYRot());
            String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
            Component text = Component.translatable("hud.tethered.next", o.get("name").getAsString(), (int) dist, arrow);
            g.drawString(font, text, x, y, 0xFF000000 | o.get("color").getAsInt(), true);
            return;
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
