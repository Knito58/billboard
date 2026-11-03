package de.knito.billboard.mixin;

import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import de.knito.billboard.wall.ProtectedAreas;

/** An enderman would carry a copy of a billboard block away (and drop it when killed), while the block stays. */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public abstract class EndermanTakeBlockGoalMixin {
	@Inject(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"), cancellable = true)
	private void billboard$leaveWall(CallbackInfo ci, @Local Level level, @Local BlockPos pos) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}
}
