package de.knito.billboard.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

import de.knito.billboard.wall.ProtectedAreas;

/** A piston that would push or pop a billboard block simply doesn't move. */
@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	@Final
	private List<BlockPos> toPush;

	@Shadow
	@Final
	private List<BlockPos> toDestroy;

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void billboard$blockProtected(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		for (BlockPos pos : toPush) {
			if (ProtectedAreas.isProtected(level, pos)) {
				cir.setReturnValue(false);
				return;
			}
		}
		for (BlockPos pos : toDestroy) {
			if (ProtectedAreas.isProtected(level, pos)) {
				cir.setReturnValue(false);
				return;
			}
		}
	}
}
