package com.duomodded.tethered.client;

import com.duomodded.tethered.network.TetherStatePayload;

/** Latest rope state from the server. Plain holder, safe to load on either side. */
public final class ClientTetherState {
    private static volatile TetherStatePayload state = TetherStatePayload.NONE;

    public static TetherStatePayload get() {
        return state;
    }

    public static void set(TetherStatePayload payload) {
        state = payload;
    }

    public static void reset() {
        state = TetherStatePayload.NONE;
    }

    private ClientTetherState() {}
}
