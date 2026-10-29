package de.knito.billboard;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.knito.billboard.compat.ComputerCraftCompat;
import de.knito.billboard.compat.EngineLuaClient;
import de.knito.billboard.net.ModNetworking;
import de.knito.billboard.wall.BlockColors;
import de.knito.billboard.wall.WallProtection;

public class Billboard implements ModInitializer {
	public static final String MOD_ID = "billboard";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BillboardConfig.load();
		ModComponents.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModBlockEntities.initialize();
		ModMenus.initialize();
		ModNetworking.initialize();
		WallProtection.initialize();
		BillboardCommand.initialize();
		// Picture blocks depend on block tags, so look again after a start or /reload.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> BlockColors.invalidate());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> BlockColors.invalidate());
		if (FabricLoader.getInstance().isModLoaded("computercraft")) {
			ComputerCraftCompat.initialize();
			LOGGER.info("CC: Tweaked found, the engine is a peripheral");
		}
		// Billboard API is optional: without it the engine works by hand, sign, redstone and CC.
		// EngineLuaClient is the only class that touches the API, so it must not load without it.
		if (FabricLoader.getInstance().isModLoaded("billboard_api")) {
			EngineLuaClient.initialize();
			LOGGER.info("Billboard API found, Lua Boxes can drive the engine");
		}

		LOGGER.info("Billboard loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
