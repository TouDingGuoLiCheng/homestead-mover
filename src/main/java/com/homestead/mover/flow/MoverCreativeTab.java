package com.homestead.mover.flow;

import com.homestead.mover.BuildMoverMod;
import com.homestead.mover.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BuildMoverMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MoverCreativeTab {
    private MoverCreativeTab() {
    }

    @SubscribeEvent
    public static void onBuildTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.MOVER_CASE);
        }
    }
}
