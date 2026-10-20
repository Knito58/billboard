package de.knito.billboard.net;

import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.menu.BillboardEngineMenu;

public class ModNetworking {
	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(EngineActionPayload.TYPE, EngineActionPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EngineStatusPayload.TYPE, EngineStatusPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(EngineActionPayload.TYPE, (payload, context) -> {
			// Only act on the engine whose screen this player really has open.
			if (!(context.player().containerMenu instanceof BillboardEngineMenu menu)
					|| menu.engine() == null
					|| !menu.stillValid(context.player())) {
				return;
			}
			BillboardEngineBlockEntity engine = menu.engine();
			engine.applySettings(payload.text(), payload.speed());
			Component message = switch (payload.action()) {
				case SAVE -> null;
				case GO -> engine.go();
				case STOP -> engine.stop();
				case RESTORE -> engine.restore();
			};
			if (message != null) {
				context.responseSender().sendPacket(new EngineStatusPayload(message));
			}
		});
	}
}
