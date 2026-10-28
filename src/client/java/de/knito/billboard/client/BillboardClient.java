package de.knito.billboard.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import de.knito.billboard.ModBlocks;
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

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!stack.is(ModBlocks.BILLBOARD_ENGINE.asItem())) {
				return;
			}
			if (Minecraft.getInstance().hasShiftDown()) {
				for (int i = 1; i <= 4; i++) {
					lines.add(Component.translatable("tooltip.billboard.engine." + i).withStyle(ChatFormatting.GRAY));
				}
			} else {
				lines.add(Component.translatable("tooltip.billboard.engine.hint").withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}
}
