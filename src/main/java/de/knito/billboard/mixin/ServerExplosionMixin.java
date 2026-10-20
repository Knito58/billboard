package de.knito.billboard.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import de.knito.billboard.wall.ProtectedAreas;

/** Explosions drop loot before removing a block, so billboard blocks are taken off the list first. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void billboard$skipProtected(List<BlockPos> targetBlocks, CallbackInfo ci) {
		targetBlocks.removeIf(pos -> ProtectedAreas.isProtected(level, pos));
	}
}
