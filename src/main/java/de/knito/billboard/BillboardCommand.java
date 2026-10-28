package de.knito.billboard;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.knito.billboard.block.BillboardEngineBlockEntity;

/**
 * {@code /billboard runoff <engine pos>}: lets the text run off the wall once. Needs op level 2, so command blocks
 * can use it. Lua Boxes have their own command in Billboard API: {@code /luabox <pos> restart|stop|status}.
 */
public final class BillboardCommand {
	private BillboardCommand() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("billboard")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("runoff")
								.then(Commands.argument("engine", BlockPosArgument.blockPos())
										.executes(BillboardCommand::runOff)))));
	}

	private static int runOff(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "engine");
		if (!(source.getLevel().getBlockEntity(pos) instanceof BillboardEngineBlockEntity engine)) {
			source.sendFailure(Component.translatable("message.billboard.no_engine", pos.getX(), pos.getY(), pos.getZ()));
			return 0;
		}
		if (engine.controller() != null) {
			source.sendFailure(Component.translatable("message.billboard.claimed", engine.controller().describe()));
			return 0;
		}
		Component message = engine.runOff();
		if (!engine.isRunningOff()) {
			source.sendFailure(message);
			return 0;
		}
		source.sendSuccess(() -> message, false);
		return 1;
	}
}
