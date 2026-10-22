package de.knito.billboard;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.script.ScriptBoxBlockEntity;

/**
 * {@code /billboard runoff <engine pos>}: lets the text run off the wall once; {@code /billboard script <box pos> ...}
 * restarts, stops or shows a Script Box. Needs op level 2, so command blocks can use it.
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
										.executes(BillboardCommand::runOff)))
						.then(Commands.literal("script")
								.then(Commands.argument("box", BlockPosArgument.blockPos())
										.then(Commands.literal("restart").executes(context -> script(context, "restart")))
										.then(Commands.literal("stop").executes(context -> script(context, "stop")))
										.then(Commands.literal("status").executes(context -> script(context, "status")))))));
	}

	/** {@code /billboard script <box pos> restart|stop|status}: controls a Script Box; status also shows its folder. */
	private static int script(CommandContext<CommandSourceStack> context, String action) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "box");
		if (!(source.getLevel().getBlockEntity(pos) instanceof ScriptBoxBlockEntity box)) {
			source.sendFailure(Component.translatable("message.billboard.no_script_box", pos.getX(), pos.getY(), pos.getZ()));
			return 0;
		}
		switch (action) {
			case "restart" -> box.restart();
			case "stop" -> box.stop();
			default -> {
			}
		}
		source.sendSuccess(() -> Component.translatable("message.billboard.script_status", box.statusText()), false);
		if (action.equals("status")) {
			source.sendSuccess(() -> Component.literal(box.folder(source.getServer()).toString()), false);
			for (ScriptBoxBlockEntity.Line line : box.lines()) {
				source.sendSuccess(() -> Component.literal(line.text()).withStyle(line.error() ? ChatFormatting.RED : ChatFormatting.GRAY), false);
			}
		}
		return 1;
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
