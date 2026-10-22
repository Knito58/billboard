package de.knito.billboard.net;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.block.EngineController;
import de.knito.billboard.menu.BillboardEngineMenu;
import de.knito.billboard.script.ScriptUploads;

public class ModNetworking {
	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(EngineActionPayload.TYPE, EngineActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PixelSamplePayload.TYPE, PixelSamplePayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EngineStatusPayload.TYPE, EngineStatusPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScriptBoxActionPayload.TYPE, ScriptBoxActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ScriptUploadPayload.TYPE, ScriptUploadPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ScriptBoxStatePayload.TYPE, ScriptBoxStatePayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ScriptBoxActionPayload.TYPE, (payload, context) -> ScriptUploads.action(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ScriptUploadPayload.TYPE, (payload, context) -> ScriptUploads.receive(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ScriptUploads.forget(handler.getPlayer().getUUID()));

		ServerPlayNetworking.registerGlobalReceiver(EngineActionPayload.TYPE, (payload, context) -> {
			// Only act on the engine whose screen this player really has open.
			if (!(context.player().containerMenu instanceof BillboardEngineMenu menu)
					|| menu.engine() == null
					|| !menu.stillValid(context.player())) {
				return;
			}
			BillboardEngineBlockEntity engine = menu.engine();
			// While a program holds the engine, the player keeps only Stop and Restore, as an emergency brake.
			EngineController holder = engine.controller();
			if (holder != null) {
				if (payload.action() == EngineActionPayload.Action.GO) {
					BillboardEngineMenu.tell(context.player(), Component.translatable("message.billboard.claimed", holder.describe()));
					return;
				}
				if (payload.action() == EngineActionPayload.Action.SAVE) {
					return;
				}
			} else {
				engine.applySettings(payload.text(), payload.speed(), payload.size());
			}
			Component message = switch (payload.action()) {
				case SAVE -> null;
				case GO -> engine.go();
				case STOP -> engine.stop();
				case RESTORE -> engine.restore();
			};
			if (message != null) {
				BillboardEngineMenu.tell(context.player(), message);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(PixelSamplePayload.TYPE, (payload, context) -> {
			if (!(context.player().containerMenu instanceof BillboardEngineMenu menu)
					|| menu.engine() == null
					|| !menu.stillValid(context.player())) {
				return;
			}
			if (menu.engine().controller() != null) {
				return;
			}
			// Unknown or unsuitable items are ignored; the player never receives anything here.
			BuiltInRegistries.ITEM.getOptional(payload.item())
					.map(ItemStack::new)
					.filter(BillboardEngineBlockEntity::isValidPixel)
					.ifPresent(menu::setPixelSample);
		});
	}
}
