package de.knito.billboard;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import de.knito.billboard.menu.BillboardEngineMenu;

public class ModMenus {
	public static final MenuType<BillboardEngineMenu> BILLBOARD_ENGINE = Registry.register(
			BuiltInRegistries.MENU,
			Billboard.id("billboard_engine"),
			new ExtendedMenuType<>(BillboardEngineMenu::new, BillboardEngineMenu.OpenData.STREAM_CODEC)
	);

	public static void initialize() {
	}
}
