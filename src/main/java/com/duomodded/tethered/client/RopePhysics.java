package com.duomodded.tethered.client;

import com.duomodded.tethered.TetherConfig;
import com.duomodded.tethered.network.TetherStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Each client moves only its own player (Minecraft movement is client-side). When the rope is
 * stretched, we cancel the velocity that moves away from the partner and add a spring pull
 * toward them. Who moves depends on who is braced (crouching on the ground) and who is in the air,
 * which gives dangling, swinging and dragging for free.
 */
public final class RopePhysics {
    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        TetherStatePayload state = ClientTetherState.get();
        if (me == null || mc.level == null || !state.linked() || !state.rope() || state.slack() || !state.sameLevel()) return;
        if (me.isSpectator() || me.getAbilities().flying || !me.isAlive()) return;

        Player partner = mc.level.getPlayerByUUID(state.partner());
        if (partner == null || partner.isSpectator()) return;

        Vec3 myPos = me.position().add(0, 0.9, 0);
        Vec3 theirPos = partner.position().add(0, 0.9, 0);
        Vec3 delta = theirPos.subtract(myPos);
        double dist = delta.length();
        double length = state.currentLength();
        if (dist <= length || dist < 1.0E-4) return;

        Vec3 dir = delta.scale(1.0 / dist);
        boolean meBraced = me.isShiftKeyDown() && me.onGround();
        boolean theyBraced = state.partnerBraced();

        double share;
        if (meBraced && !theyBraced) {
            share = 0.0;
        } else if (theyBraced && !meBraced) {
            share = 1.0;
        } else {
            boolean meAir = !me.onGround();
            boolean theyAir = !partner.onGround();
            if (meAir && !theyAir) share = 0.8;
            else if (!meAir && theyAir) share = 0.2;
            else share = 0.5;
        }
        if (share <= 0) return;

        Vec3 v = me.getDeltaMovement();
        double outward = -v.dot(dir);
        if (outward > 0) v = v.add(dir.scale(outward * share));

        double excess = dist - length;
        double pull = Math.min(excess * TetherConfig.PULL_STRENGTH.get(), 0.6) * share;
        v = v.add(dir.scale(pull));
        me.setDeltaMovement(v);

        if (dir.y > 0.3) me.resetFallDistance();
    }

    private RopePhysics() {}
}
