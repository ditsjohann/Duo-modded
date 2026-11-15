package com.duomodded.tethered.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    public static final String CATEGORY = "key.categories.tethered";

    public static final KeyMapping HAUL = new KeyMapping("key.tethered.haul", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping LAUNCH = new KeyMapping("key.tethered.launch", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping ENDER_CALL = new KeyMapping("key.tethered.ender_call", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping JOURNAL = new KeyMapping("key.tethered.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping UNHOOK = new KeyMapping("key.tethered.unhook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);

    private Keys() {}
}
