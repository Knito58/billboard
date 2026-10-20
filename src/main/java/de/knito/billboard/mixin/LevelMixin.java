package de.knito.billboard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.wall.ProtectedAreas;

/** Catch-all: no block of an active billboard is replaced or destroyed unless the mod does it. */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void billboard$protectSetBlock(BlockPos pos, BlockState state, int flags, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		Level level = (Level) (Object) this;
		// A block may still change its own state (a lamp lighting up); it just can't become another block.
		if (ProtectedAreas.isProtected(level, pos) && !level.getBlockState(pos).is(state.getBlock())) {
			cir.setReturnValue(false);
		}
	}

	// destroyBlock drops the items before it calls setBlock, so it must be stopped up front.
	@Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
	private void billboard$protectDestroyBlock(BlockPos pos, boolean dropResources, Entity breaker, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		if (ProtectedAreas.isProtected((Level) (Object) this, pos)) {
			cir.setReturnValue(false);
		}
	}
}
