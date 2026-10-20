package de.knito.billboard.item;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import de.knito.billboard.ModComponents;

/**
 * Right-click marks corner 1, the next right-click corner 2, the one after starts over.
 * Sneak + right-click clears both. While held, the action bar shows the size.
 */
public class SelectorWandItem extends Item {
	private static final double PREVIEW_RANGE = 64.0;

	public SelectorWandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			return clear(context.getLevel(), player, context.getItemInHand());
		}
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = context.getItemInHand();
		WandCorners corners = stack.getOrDefault(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
		GlobalPos clicked = GlobalPos.of(context.getLevel().dimension(), context.getClickedPos().immutable());
		BlockPos pos = clicked.pos();

		if (corners.first().isEmpty() || corners.isComplete()) {
			stack.set(ModComponents.WAND_CORNERS, new WandCorners(Optional.of(clicked), Optional.empty()));
			player.sendOverlayMessage(Component.translatable("message.billboard.corner1", pos.getX(), pos.getY(), pos.getZ()));
		} else {
			stack.set(ModComponents.WAND_CORNERS, new WandCorners(corners.first(), Optional.of(clicked)));
			player.sendOverlayMessage(Component.translatable("message.billboard.corner2", pos.getX(), pos.getY(), pos.getZ()));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown()) {
			return clear(level, player, player.getItemInHand(hand));
		}
		return InteractionResult.PASS;
	}

	private InteractionResult clear(Level level, Player player, ItemStack stack) {
		if (!level.isClientSide()) {
			stack.set(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
			player.sendOverlayMessage(Component.translatable("message.billboard.cleared"));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		if (slot != EquipmentSlot.MAINHAND || !(owner instanceof ServerPlayer player) || level.getGameTime() % 5 != 0) {
			return;
		}
		WandCorners corners = stack.getOrDefault(ModComponents.WAND_CORNERS, WandCorners.EMPTY);
		if (corners.first().isEmpty() || !corners.first().get().dimension().equals(level.dimension())) {
			return;
		}

		BlockPos first = corners.first().get().pos();
		BlockPos second;
		if (corners.second().isPresent()) {
			second = corners.second().get().pos();
		} else {
			HitResult hit = player.pick(PREVIEW_RANGE, 1.0f, false);
			if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
				player.sendOverlayMessage(Component.translatable("message.billboard.aim_corner2"));
				return;
			}
			second = blockHit.getBlockPos();
		}
		player.sendOverlayMessage(sizeMessage(first, second, corners.isComplete()));
	}

	private static Component sizeMessage(BlockPos a, BlockPos b, boolean complete) {
		int sizeX = Math.abs(a.getX() - b.getX()) + 1;
		int sizeY = Math.abs(a.getY() - b.getY()) + 1;
		int sizeZ = Math.abs(a.getZ() - b.getZ()) + 1;
		if (sizeX > 1 && sizeZ > 1) {
			return Component.translatable("message.billboard.size_not_flat", sizeX, sizeY, sizeZ);
		}
		int width = Math.max(sizeX, sizeZ);
		return Component.translatable(complete ? "message.billboard.size_done" : "message.billboard.size", width, sizeY);
	}
}
