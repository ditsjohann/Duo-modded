package com.duomodded.tethered;

import com.duomodded.tethered.network.ModNetwork;
import com.duomodded.tethered.registry.ModRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Tethered: two players tied together by a rope on a sky island, growing a Heart Tree that
 * raises new rings of islands. Game-bus listeners live in classes annotated with
 * {@code @EventBusSubscriber}, so they register themselves.
 */
@Mod(Tethered.MODID)
public final class Tethered {
    public static final String MODID = "tethered";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Tethered(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(ModNetwork::register);
        container.registerConfig(ModConfig.Type.COMMON, TetherConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
