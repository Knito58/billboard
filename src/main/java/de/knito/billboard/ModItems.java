package de.knito.billboard;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

import de.knito.billboard.item.SelectorWandItem;
import de.knito.billboard.item.WandCorners;

public class ModItems {
	public static final Item SELECTOR_WAND = register("selector_wand");

	private static Item register(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Billboard.id(name));
		Item.Properties properties = new Item.Properties().stacksTo(1).component(ModComponents.WAND_CORNERS, WandCorners.EMPTY).setId(key);
		return Registry.register(BuiltInRegistries.ITEM, key, new SelectorWandItem(properties));
	}

	public static void initialize() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((creativeTab) -> {
			creativeTab.accept(SELECTOR_WAND);
		});
	}
}
