package com.homestead.mover.registry;

import com.homestead.mover.BuildMoverMod;
import com.homestead.mover.item.MoverCaseItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, BuildMoverMod.MOD_ID);

    public static final RegistryObject<Item> MOVER_CASE = ITEMS.register("mover_case",
            () -> new MoverCaseItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
