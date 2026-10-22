package de.knito.billboard.script;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import de.knito.billboard.ModBlockEntities;

/** Runs one Lua program that drives the Billboard engine it touches. */
public class ScriptBoxBlock extends BaseEntityBlock {
	public static final EnumProperty<ScriptState> STATE = EnumProperty.create("state", ScriptState.class);
	/** Output lines shown in chat on right-click. */
	private static final int CHAT_LINES = 6;

	public ScriptBoxBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(STATE, ScriptState.STOPPED));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(STATE);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		super.setPlacedBy(level, pos, state, by, itemStack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScriptBoxBlockEntity box) {
			box.placedBy(by instanceof Player player ? player.getUUID() : null);
		}
	}

	/** For now the box reports its state and last output lines in chat. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScriptBoxBlockEntity box) {
			player.sendSystemMessage(Component.translatable("message.billboard.script_status", box.statusText()).withStyle(ChatFormatting.GOLD));
			List<ScriptBoxBlockEntity.Line> lines = box.lines();
			for (ScriptBoxBlockEntity.Line line : lines.subList(Math.max(0, lines.size() - CHAT_LINES), lines.size())) {
				player.sendSystemMessage(Component.literal(line.text()).withStyle(line.error() ? ChatFormatting.RED : ChatFormatting.GRAY));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SCRIPT_BOX, ScriptBoxBlockEntity::serverTick);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ScriptBoxBlockEntity(pos, state);
	}
}
