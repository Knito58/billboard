package de.knito.billboard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.billboard.wall.ProtectedAreas;

/**
 * Sand or gravel in a wall would spawn a falling block while the frozen block stays, again every time
 * the text puts it back. While the billboard is shown it simply doesn't fall.
 */
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void billboard$stayInWall(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}
}
