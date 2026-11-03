package de.knito.billboard.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import de.knito.billboard.wall.ProtectedAreas;

/**
 * Blocks of an active billboard are unbreakable like bedrock and drop nothing. The freeze in {@link LevelMixin}
 * alone is not enough: vein miners, drills and quarries often hand out the drops first and only then try to
 * remove the block. The removal fails, the block stays, and the drops could be collected again and again.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
	// Hardness -1 = unbreakable: players can't mine the block, and well-behaved tools, machines and movers skip it.
	@Inject(method = "getDestroySpeed", at = @At("HEAD"), cancellable = true)
	private void billboard$unbreakable(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (getter instanceof Level level && ProtectedAreas.isProtected(level, pos)) {
			cir.setReturnValue(-1.0F);
		}
	}

	// Loot of a block at a billboard position is empty, for every mod that rolls a block's loot table itself.
	@Inject(method = "getDrops", at = @At("HEAD"), cancellable = true)
	private void billboard$noDrops(LootParams.Builder params, CallbackInfoReturnable<List<ItemStack>> cir) {
		Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
		if (origin != null && ProtectedAreas.isProtected(params.getLevel(), BlockPos.containing(origin))) {
			cir.setReturnValue(List.of());
		}
	}

	// Experience from ores, silverfish from infested blocks.
	@Inject(method = "spawnAfterBreak", at = @At("HEAD"), cancellable = true)
	private void billboard$nothingAfterBreak(ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience, CallbackInfo ci) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}
}
