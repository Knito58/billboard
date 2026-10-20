package de.knito.billboard.wall;

import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

/** Wires the protection index to the server's lifecycle and tells players why a block won't break. */
public final class WallProtection {
	private WallProtection() {
	}

	public static void initialize() {
		// Load each dimension's backups right away, so walls are protected before any engine ticks.
		ServerLevelEvents.LOAD.register((server, level) -> BackupData.get(level));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ProtectedAreas.clear());

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (ProtectedAreas.isProtected(level, pos)) {
				player.sendOverlayMessage(Component.translatable("message.billboard.protected"));
				return false;
			}
			return true;
		});
	}
}
