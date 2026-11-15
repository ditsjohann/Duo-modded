package com.duomodded.tethered.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Latest journal contents from the server. Plain holder, safe to load on either side. */
public final class ClientJournalState {
    private static volatile JsonObject data = new JsonObject();

    public static JsonObject get() {
        return data;
    }

    public static void set(String json) {
        try {
            data = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            data = new JsonObject();
        }
    }

    public static void reset() {
        data = new JsonObject();
    }

    private ClientJournalState() {}
}
