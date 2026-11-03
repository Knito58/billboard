package de.knito.billboard.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import de.knito.billboard.wall.ProtectedAreas;

/** No item and no experience pops out of a billboard block, whoever asks for it. See {@link BlockStateBaseMixin}. */
@Mixin(Block.class)
public abstract class BlockMixin {
	@Inject(method = "popResource(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private static void billboard$noPop(Level level, BlockPos pos, ItemStack stack, CallbackInfo ci) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "popResourceFromFace", at = @At("HEAD"), cancellable = true)
	private static void billboard$noPopFromFace(Level level, BlockPos pos, Direction face, ItemStack stack, CallbackInfo ci) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
	private void billboard$noExperience(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
		if (ProtectedAreas.isProtected(level, pos)) {
			ci.cancel();
		}
	}

	// Interaction loot, such as the seeds from carving a pumpkin: the block can't change, so it gives nothing either.
	@Inject(method = "dropFromBlockInteractLootTable", at = @At("HEAD"), cancellable = true)
	private static void billboard$noInteractLoot(ServerLevel level, ResourceKey<LootTable> table, BlockPos pos, BlockState state,
			BlockEntity blockEntity, ItemInstance tool, Entity entity, BiConsumer<ServerLevel, ItemStack> consumer,
			CallbackInfoReturnable<Boolean> cir) {
		if (ProtectedAreas.isProtected(level, pos)) {
			cir.setReturnValue(false);
		}
	}
}
