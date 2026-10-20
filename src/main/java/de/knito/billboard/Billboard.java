package de.knito.billboard;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.knito.billboard.net.ModNetworking;

public class Billboard implements ModInitializer {
	public static final String MOD_ID = "billboard";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModComponents.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModBlockEntities.initialize();
		ModMenus.initialize();
		ModNetworking.initialize();

		LOGGER.info("Billboard loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
