package com.homestead.mover;

import com.homestead.mover.network.MoverNetwork;
import com.homestead.mover.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BuildMoverMod.MOD_ID)
public final class BuildMoverMod {
    public static final String MOD_ID = "homestead_mover";
    private static final Logger LOGGER = LogUtils.getLogger();

    public BuildMoverMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.register(modEventBus);
        MoverNetwork.register();
        LOGGER.info("Homestead Mover loaded");
    }
}
