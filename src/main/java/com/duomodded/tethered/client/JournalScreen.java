package com.duomodded.tethered.client;

import com.duomodded.tethered.network.JournalActionPayload;
import com.duomodded.tethered.network.TetherStatePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Weaver's Journal. Three tabs:
 * Map (the islands, Rootways and where you are; click an attuned island to travel),
 * Quests (chapters per island, tasks, rewards, hand-in button) and Tree (stage and blessings).
 */
public class JournalScreen extends Screen {
    private static final int BG = 0xF01C1722;
    private static final int BORDER = 0xFFB07AA1;
    private static final int PINK = 0xFFFFB6D5;
    private static final int GOLD = 0xFFF2C94C;
    private static final int GRAY = 0xFF8A8494;
    private static final int GREEN = 0xFF7BD389;
    private static final int WHITE = 0xFFFFFFFF;

    private static int tab = 0;
    @Nullable
    private static String selectedChapter;
    @Nullable
    private static String selectedQuest;
    private static int questScroll;

    private int left;
    private int top;
    private int w;
    private int h;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable
    private List<Component> tooltip;

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean inside(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    public JournalScreen() {
        super(Component.translatable("screen.tethered.journal"));
    }

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(260, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        hits.clear();
        tooltip = null;

        g.fill(left, top, left + w, top + h, BG);
        g.renderOutline(left, top, w, h, BORDER);
        g.drawString(font, title, left + 8, top + 7, PINK, false);

        String[] tabs = {"screen.tethered.tab.map", "screen.tethered.tab.quests", "screen.tethered.tab.tree"};
        int tx = left + w - 6;
        for (int i = tabs.length - 1; i >= 0; i--) {
            Component label = Component.translatable(tabs[i]);
            int tw = font.width(label) + 10;
            tx -= tw + 3;
            int x0 = tx;
            boolean active = tab == i;
            g.fill(x0, top + 4, x0 + tw, top + 17, active ? 0xFF5A3A55 : 0xFF2C2433);
            g.drawString(font, label, x0 + 5, top + 7, active ? WHITE : GRAY, false);
            final int index = i;
            hits.add(new Hit(x0, top + 4, x0 + tw, top + 17, () -> tab = index));
        }

        int cx = left + 6;
        int cy = top + 21;
        int cw = w - 12;
        int ch = h - 27;
        JsonObject data = ClientJournalState.get();
        switch (tab) {
            case 0 -> renderMap(g, data, cx, cy, cw, ch, mouseX, mouseY);
            case 1 -> renderQuests(g, data, cx, cy, cw, ch, mouseX, mouseY);
            default -> renderTree(g, data, cx, cy, cw, ch);
        }

        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ map

    private void renderMap(GuiGraphics g, JsonObject data, int cx, int cy, int cw, int ch, int mx, int my) {
        g.fill(cx, cy, cx + cw, cy + ch, 0xFF101A2B);
        JsonArray islands = arr(data, "islands");
        if (islands.isEmpty() || !bool(data, "tetheredWorld")) {
            drawWrapped(g, Component.translatable("screen.tethered.map.blank"), cx + 10, cy + 10, cw - 20, GRAY);
            return;
        }
        double treeX = num(data, "treeX", 0);
        double treeZ = num(data, "treeZ", 0);
        double minX = treeX - 20, maxX = treeX + 20, minZ = treeZ - 20, maxZ = treeZ + 20;
        for (JsonElement e : islands) {
            JsonObject o = e.getAsJsonObject();
            double x = num(o, "x", 0), z = num(o, "z", 0), r = num(o, "r", 10);
            minX = Math.min(minX, x - r);
            maxX = Math.max(maxX, x + r);
            minZ = Math.min(minZ, z - r);
            maxZ = Math.max(maxZ, z + r);
        }
        int pad = 14;
        double scale = Math.min((cw - pad * 2) / (maxX - minX), (ch - pad * 2 - 10) / (maxZ - minZ));
        double midX = (minX + maxX) / 2;
        double midZ = (minZ + maxZ) / 2;
        int ox = cx + cw / 2;
        int oz = cy + (ch - 10) / 2;
        java.util.function.DoubleUnaryOperator sx = x -> ox + (x - midX) * scale;
        java.util.function.DoubleUnaryOperator sz = z -> oz + (z - midZ) * scale;

        int tX = (int) sx.applyAsDouble(treeX);
        int tZ = (int) sz.applyAsDouble(treeZ);
        boolean travel = bool(data, "travel");

        // Rootways
        for (JsonElement e : islands) {
            JsonObject o = e.getAsJsonObject();
            if (!bool(o, "unlocked")) continue;
            line(g, tX, tZ, (int) sx.applyAsDouble(num(o, "x", 0)), (int) sz.applyAsDouble(num(o, "z", 0)), 0xFF9C6B4E);
        }

        // Islands
        for (JsonElement e : islands) {
            JsonObject o = e.getAsJsonObject();
            int x = (int) sx.applyAsDouble(num(o, "x", 0));
            int z = (int) sz.applyAsDouble(num(o, "z", 0));
            int size = Math.max(3, (int) (num(o, "r", 10) * scale));
            boolean unlocked = bool(o, "unlocked");
            boolean visited = bool(o, "visited");
            boolean attuned = bool(o, "attuned");
            int color = (unlocked || visited) ? 0xFF000000 | (int) num(o, "color", 0xFFB6C1) : 0xFF4A4552;
            g.fill(x - size, z - size, x + size, z + size, color);
            if (attuned) g.renderOutline(x - size - 1, z - size - 1, size * 2 + 2, size * 2 + 2, WHITE);
            String name = (unlocked || visited) ? str(o, "name", "?") : "???";
            small(g, name, x - (int) (font.width(name) * 0.35f), z + size + 2, unlocked || visited ? WHITE : GRAY);

            if (mx >= x - size - 2 && mx <= x + size + 2 && my >= z - size - 2 && my <= z + size + 2) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(name).withStyle(s -> s.withColor(color & 0xFFFFFF)));
                if (unlocked || visited) tip.add(Component.literal(str(o, "desc", "")).withStyle(s -> s.withColor(0xBBBBBB)));
                String status = attuned ? "screen.tethered.map.attuned"
                        : visited ? "screen.tethered.map.visited"
                        : unlocked ? "screen.tethered.map.unlocked" : "screen.tethered.map.locked";
                tip.add(Component.translatable(status));
                if (minecraft != null && minecraft.player != null) {
                    double dx = num(o, "x", 0) - minecraft.player.getX();
                    double dz = num(o, "z", 0) - minecraft.player.getZ();
                    tip.add(Component.translatable("screen.tethered.map.distance", (int) Math.sqrt(dx * dx + dz * dz)));
                }
                if (attuned && travel) tip.add(Component.translatable("screen.tethered.map.click_travel").withStyle(s -> s.withColor(GOLD & 0xFFFFFF)));
                tooltip = tip;
                if (attuned && travel) {
                    String id = str(o, "id", "");
                    hits.add(new Hit(x - size - 2, z - size - 2, x + size + 3, z + size + 3, () -> sendAction(JournalActionPayload.TRAVEL, id)));
                }
            }
        }

        // Heart Tree
        g.fill(tX - 4, tZ - 4, tX + 4, tZ + 4, 0xFFFF6FA5);
        g.drawCenteredString(font, "❤", tX, tZ - 4, WHITE);
        if (Math.abs(mx - tX) <= 6 && Math.abs(my - tZ) <= 6) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("block.tethered.heart_tree").withStyle(s -> s.withColor(0xFF6FA5)));
            tip.add(Component.translatable("screen.tethered.tree.stage", (int) num(data, "stage", 0), str(data, "stageName", "")));
            if (travel) tip.add(Component.translatable("screen.tethered.map.click_travel").withStyle(s -> s.withColor(GOLD & 0xFFFFFF)));
            tooltip = tip;
            if (travel) hits.add(new Hit(tX - 6, tZ - 6, tX + 7, tZ + 7, () -> sendAction(JournalActionPayload.TRAVEL, "tree")));
        }

        // You and your partner
        if (minecraft != null && minecraft.player != null) {
            int px = (int) sx.applyAsDouble(minecraft.player.getX());
            int pz = (int) sz.applyAsDouble(minecraft.player.getZ());
            dot(g, px, pz, WHITE);
            TetherStatePayload s = ClientTetherState.get();
            if (s.linked() && s.partnerOnline() && s.sameLevel()) {
                dot(g, (int) sx.applyAsDouble(s.px()), (int) sz.applyAsDouble(s.pz()), PINK);
            }
        }

        Component legend = Component.translatable(travel ? "screen.tethered.map.legend_travel" : "screen.tethered.map.legend");
        small(g, legend.getString(), cx + 4, cy + ch - 9, GRAY);
    }

    private void dot(GuiGraphics g, int x, int y, int color) {
        g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF000000);
        g.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 2) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(Mth.lerp(t, x0, x1));
            int y = Math.round(Mth.lerp(t, y0, y1));
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    // ------------------------------------------------------------------ quests

    private void renderQuests(GuiGraphics g, JsonObject data, int cx, int cy, int cw, int ch, int mx, int my) {
        JsonArray chapters = arr(data, "chapters");
        if (chapters.isEmpty()) {
            drawWrapped(g, Component.translatable("screen.tethered.quests.none"), cx + 10, cy + 10, cw - 20, GRAY);
            return;
        }
        if (selectedChapter == null || find(chapters, selectedChapter) == null) {
            selectedChapter = str(chapters.get(0).getAsJsonObject(), "id", "");
        }

        // Chapter column
        int colW = 112;
        g.fill(cx, cy, cx + colW, cy + ch, 0xFF221C29);
        int y = cy + 2;
        for (JsonElement e : chapters) {
            JsonObject c = e.getAsJsonObject();
            String id = str(c, "id", "");
            boolean unlocked = bool(c, "unlocked");
            boolean selected = id.equals(selectedChapter);
            if (selected) g.fill(cx, y - 1, cx + colW, y + 17, 0xFF4A3346);
            g.renderItem(stack(str(c, "icon", "minecraft:book")), cx + 2, y);
            String title = unlocked ? str(c, "title", id) : "???";
            g.drawString(font, font.plainSubstrByWidth(title, colW - 24), cx + 21, y + 1, unlocked ? WHITE : GRAY, false);
            int done = (int) num(c, "done", 0), total = (int) num(c, "total", 0);
            small(g, unlocked ? done + "/" + total : "locked", cx + 21, y + 10, done == total && total > 0 ? GREEN : GRAY);
            hits.add(new Hit(cx, y - 1, cx + colW, y + 17, () -> {
                selectedChapter = id;
                selectedQuest = null;
                questScroll = 0;
            }));
            y += 19;
            if (y > cy + ch - 18) break;
        }

        JsonObject chapter = find(chapters, selectedChapter);
        if (chapter == null) return;
        int qx = cx + colW + 4;
        int qw = 128;
        g.fill(qx, cy, qx + qw, cy + ch, 0xFF221C29);

        if (!bool(chapter, "unlocked")) {
            drawWrapped(g, Component.translatable("screen.tethered.quests.locked"), qx + qw + 8, cy + 6, cw - colW - qw - 16, GRAY);
            return;
        }

        JsonArray quests = arr(chapter, "quests");
        int visible = (ch - 4) / 19;
        questScroll = Mth.clamp(questScroll, 0, Math.max(0, quests.size() - visible));
        if (selectedQuest == null || find(quests, selectedQuest) == null) {
            for (JsonElement e : quests) {
                if ("AVAILABLE".equals(str(e.getAsJsonObject(), "status", ""))) {
                    selectedQuest = str(e.getAsJsonObject(), "id", "");
                    break;
                }
            }
            if (selectedQuest == null && !quests.isEmpty()) selectedQuest = str(quests.get(0).getAsJsonObject(), "id", "");
        }
        y = cy + 2;
        for (int i = questScroll; i < quests.size() && i < questScroll + visible; i++) {
            JsonObject q = quests.get(i).getAsJsonObject();
            String id = str(q, "id", "");
            String status = str(q, "status", "LOCKED");
            boolean selected = id.equals(selectedQuest);
            if (selected) g.fill(qx, y - 1, qx + qw, y + 17, 0xFF4A3346);
            g.renderItem(stack(str(q, "icon", "minecraft:book")), qx + 2, y);
            int color = switch (status) {
                case "DONE" -> GREEN;
                case "AVAILABLE" -> WHITE;
                default -> GRAY;
            };
            String title = str(q, "title", id);
            g.drawString(font, font.plainSubstrByWidth(title, qw - 24), qx + 21, y + 1, color, false);
            String sub = "DONE".equals(status) ? "✔" : "LOCKED".equals(status) ? "…" : (bool(q, "optional") ? "optional" : "");
            small(g, sub, qx + 21, y + 10, color);
            hits.add(new Hit(qx, y - 1, qx + qw, y + 17, () -> selectedQuest = id));
            y += 19;
        }

        JsonObject quest = selectedQuest == null ? null : find(quests, selectedQuest);
        if (quest != null) renderQuestDetails(g, quest, qx + qw + 6, cy + 2, cx + cw - (qx + qw + 6), ch - 4);
    }

    private void renderQuestDetails(GuiGraphics g, JsonObject q, int x, int y, int width, int height) {
        String status = str(q, "status", "LOCKED");
        g.drawString(font, font.plainSubstrByWidth(str(q, "title", ""), width), x, y, GOLD, false);
        y += 11;
        if (q.has("mod")) {
            small(g, "[" + str(q, "mod", "") + "]", x, y, 0xFF9A86C8);
            y += 8;
        }
        y = drawWrapped(g, Component.literal(str(q, "desc", "")), x, y + 1, width, 0xFFCFC8D6) + 4;

        g.drawString(font, Component.translatable("screen.tethered.quests.tasks"), x, y, PINK, false);
        y += 11;
        boolean needsHandIn = false;
        for (JsonElement e : arr(q, "tasks")) {
            JsonObject t = e.getAsJsonObject();
            int value = (int) num(t, "value", 0);
            int count = (int) num(t, "count", 1);
            boolean done = value >= count;
            ItemStack icon = taskIcon(t);
            int textX = x;
            if (!icon.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x, y - 2, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(icon, 0, 0);
                g.pose().popPose();
                textX += 14;
            }
            String text = (done ? "✔ " : "") + taskText(t) + (count > 1 ? "  " + value + "/" + count : "");
            for (FormattedCharSequence line : font.split(Component.literal(text), width - (textX - x))) {
                g.drawString(font, line, textX, y, done ? GREEN : WHITE, false);
                y += 10;
            }
            if (!done && "item".equals(str(t, "type", "")) && bool(t, "consume")) needsHandIn = true;
        }

        if (!arr(q, "rewards").isEmpty()) {
            y += 3;
            g.drawString(font, Component.translatable("screen.tethered.quests.rewards"), x, y, PINK, false);
            y += 11;
            for (JsonElement e : arr(q, "rewards")) {
                for (FormattedCharSequence line : font.split(Component.literal("• " + rewardText(e.getAsJsonObject())), width)) {
                    g.drawString(font, line, x, y, 0xFFE6D6A0, false);
                    y += 10;
                }
            }
        }

        if ("AVAILABLE".equals(status) && needsHandIn) {
            Component label = Component.translatable("screen.tethered.quests.hand_in");
            int bw = font.width(label) + 12;
            int bx = x;
            int by = Math.min(y + 4, top + h - 20);
            g.fill(bx, by, bx + bw, by + 14, 0xFF6B3F63);
            g.renderOutline(bx, by, bw, 14, BORDER);
            g.drawString(font, label, bx + 6, by + 3, WHITE, false);
            String id = str(q, "id", "");
            hits.add(new Hit(bx, by, bx + bw, by + 14, () -> sendAction(JournalActionPayload.SUBMIT, id)));
        } else if ("LOCKED".equals(status)) {
            small(g, Component.translatable("screen.tethered.quests.locked_quest").getString(), x, Math.min(y + 4, top + h - 14), GRAY);
        }
    }

    // ------------------------------------------------------------------ tree

    private void renderTree(GuiGraphics g, JsonObject data, int cx, int cy, int cw, int ch) {
        int stage = (int) num(data, "stage", 0);
        int x = cx + 8;
        int y = cy + 6;
        g.drawString(font, Component.translatable("screen.tethered.tree.stage", stage, str(data, "stageName", "Seed")), x, y, PINK, false);
        y += 12;
        String next = str(data, "nextStageName", "");
        if (!next.isEmpty()) {
            y = drawWrapped(g, Component.translatable("screen.tethered.tree.next", next), x, y, cw - 16, 0xFFCFC8D6) + 6;
        } else {
            y = drawWrapped(g, Component.translatable("msg.tethered.tree.full_bloom"), x, y, cw - 16, GOLD) + 6;
        }
        g.drawString(font, Component.translatable("screen.tethered.tree.blessings"), x, y, GOLD, false);
        y += 12;
        for (int i = 1; i <= 7; i++) {
            boolean active = stage >= i;
            Component name = Component.translatable("blessing.tethered." + i);
            Component desc = Component.translatable("blessing.tethered." + i + ".desc");
            g.drawString(font, Component.literal(active ? "✔ " : i + ". ").append(name), x, y, active ? GREEN : GRAY, false);
            y = drawWrapped(g, desc, x + 14, y + 10, cw - 30, active ? 0xFFCFC8D6 : 0xFF6E6878) + 3;
            if (y > cy + ch - 10) break;
        }
    }

    // ------------------------------------------------------------------ text helpers

    private String taskText(JsonObject t) {
        if (t.has("label")) return str(t, "label", "");
        int count = (int) num(t, "count", 1);
        String type = str(t, "type", "");
        return switch (type) {
            case "item" -> Component.translatable(bool(t, "consume") ? "task.tethered.bring" : "task.tethered.have",
                    count, itemName(t)).getString();
            case "kill" -> Component.translatable("task.tethered.kill", count, entityName(str(t, "id", ""))).getString();
            case "advancement" -> Component.translatable("task.tethered.advancement", advancementName(str(t, "id", ""))).getString();
            case "visit" -> Component.translatable("task.tethered.visit", islandName(str(t, "id", ""))).getString();
            case "attune" -> Component.translatable("task.tethered.attune", islandName(str(t, "id", ""))).getString();
            case "tree_stage" -> Component.translatable("task.tethered.tree_stage", count).getString();
            case "together" -> Component.translatable("task.tethered.together").getString();
            default -> type;
        };
    }

    private String rewardText(JsonObject r) {
        int count = (int) num(r, "count", 1);
        return switch (str(r, "type", "")) {
            case "item" -> count + " × " + itemName(r);
            case "shard" -> Component.translatable("item.tethered.heart_shard").getString() + (count > 1 ? " × " + count : "");
            case "xp" -> Component.translatable("reward.tethered.xp", count).getString();
            case "unlock" -> Component.translatable("reward.tethered.unlock", islandName(str(r, "island", ""))).getString();
            case "ending" -> Component.translatable("reward.tethered.ending").getString();
            default -> "?";
        };
    }

    private ItemStack taskIcon(JsonObject t) {
        String type = str(t, "type", "");
        if (!"item".equals(type)) return ItemStack.EMPTY;
        if (t.has("item")) return stack(str(t, "item", ""));
        if (t.has("tag")) {
            ResourceLocation id = ResourceLocation.tryParse(str(t, "tag", ""));
            if (id != null) {
                return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                        .flatMap(set -> set.stream().findFirst())
                        .map(holder -> new ItemStack(holder.value()))
                        .orElse(ItemStack.EMPTY);
            }
        }
        return ItemStack.EMPTY;
    }

    private String itemName(JsonObject o) {
        if (o.has("item")) {
            ItemStack s = stack(str(o, "item", ""));
            return s.getHoverName().getString();
        }
        ItemStack s = taskIcon(o);
        String name = s.isEmpty() ? str(o, "tag", "?") : s.getHoverName().getString();
        return Component.translatable("msg.tethered.tree.any", name).getString();
    }

    private static String entityName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(rl).map(EntityType::getDescription)
                .map(Component::getString).orElse(id);
    }

    private String advancementName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (rl == null || connection == null) return id;
        AdvancementHolder holder = connection.getAdvancements().get(rl);
        if (holder == null) return id;
        return holder.value().display().map(d -> d.getTitle().getString()).orElse(id);
    }

    private static String islandName(String id) {
        for (JsonElement e : arr(ClientJournalState.get(), "islands")) {
            JsonObject o = e.getAsJsonObject();
            if (id.equals(str(o, "id", ""))) return str(o, "name", id);
        }
        return id;
    }

    private static ItemStack stack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return new ItemStack(Items.BOOK);
        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.BOOK);
        return new ItemStack(item);
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.7f, 0.7f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ json helpers

    private static JsonArray arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key, String def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : def;
    }

    private static double num(JsonObject o, String key, double def) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : def;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    @Nullable
    private static JsonObject find(JsonArray array, String id) {
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            if (id.equals(str(o, "id", ""))) return o;
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    private void sendAction(int action, String arg) {
        PacketDistributor.sendToServer(new JournalActionPayload(action, arg));
        if (action == JournalActionPayload.TRAVEL) onClose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : new ArrayList<>(hits)) {
                if (hit.inside(mouseX, mouseY)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == 1) {
            questScroll = Math.max(0, questScroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keys.JOURNAL.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
