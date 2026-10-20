package de.knito.billboard.client;

import net.minecraft.client.gui.screens.MenuScreens;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.billboard.ModMenus;
import de.knito.billboard.net.EngineStatusPayload;

public class BillboardClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.BILLBOARD_ENGINE, BillboardEngineScreen::new);

		ClientPlayNetworking.registerGlobalReceiver(EngineStatusPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof BillboardEngineScreen screen) {
				screen.setStatus(payload.message());
			} else {
				context.player().sendOverlayMessage(payload.message());
			}
		});
	}
}
