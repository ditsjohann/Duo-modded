package com.duomodded.tethered.client;

import net.minecraft.client.Minecraft;

/** Client-only entry points called from common code (only on the client side). */
public final class ClientAccess {
    public static void openJournal() {
        Minecraft.getInstance().setScreen(new JournalScreen());
    }

    private ClientAccess() {}
}
