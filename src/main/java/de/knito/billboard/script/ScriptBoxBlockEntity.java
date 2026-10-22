package de.knito.billboard.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import de.knito.billboard.BillboardConfig;
import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.menu.ScriptBoxMenu;
import de.knito.billboard.net.ScriptBoxStatePayload;

/**
 * One Script Box: the id of its file folder, its owner, and the program running now. The program starts when
 * the box is loaded and stops when it is unloaded; it keeps nothing between runs.
 */
public class ScriptBoxBlockEntity extends BlockEntity implements ExtendedMenuProvider<ScriptBoxMenu.OpenData> {
	/** Output lines kept for the screen and the chat summary. */
	public static final int MAX_LINES = 50;
	private static final int MAX_LINE_LENGTH = 200;

	public record Line(String text, boolean error) {
	}

	private String id;
	private UUID owner;
	/** Set by Stop; the program stays off, also after a restart of the world, until Restart. */
	private boolean halted;
	private ScriptRunner runner;
	/** True once this load tried to start the program, so a broken program is not compiled every tick. */
	private boolean started;
	private final Deque<Line> lines = new ArrayDeque<>();
	/** Counts every change an open screen should see: output, state, files. */
	private int version;

	public ScriptBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.SCRIPT_BOX, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ScriptBoxBlockEntity box) {
		if (!box.started) {
			box.started = true;
			if (!box.halted) {
				box.start();
			}
		}
		if (box.runner != null) {
			box.runner.tick(level.getGameTime());
		}
	}

	/** Time of day for os.time: overworld clock ticks, 0 = 6:00 in the morning. */
	static long dayTime(ServerLevel level) {
		return level.getOverworldClockTime();
	}

	// ---- program control ----

	/** Starts the program from the top, stopping a running one first. */
	public void restart() {
		halted = false;
		setChanged();
		stopRunner();
		start();
	}

	/** The hand brake. The program stays off until Restart. */
	public void stop() {
		halted = true;
		setChanged();
		if (runner != null && runner.isAlive()) {
			runner.stop();
		} else {
			print("Stopped.", false);
		}
		runner = null;
		runnerChanged();
	}

	private void stopRunner() {
		if (runner != null && runner.isAlive()) {
			runner.stop();
		}
		runner = null;
	}

	private void start() {
		started = true;
		runner = null;
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!BillboardConfig.scriptsEnabled()) {
			print("Script Boxes are switched off on this server (scripts_enabled in billboard.properties).", true);
			runnerChanged();
			return;
		}
		Path folder = folder(serverLevel.getServer());
		List<Path> programs = ScriptStorage.programs(folder);
		if (programs.isEmpty()) {
			print("No program yet. Drop one .lua file onto this screen (or put it into " + folder + ").", true);
			runnerChanged();
			return;
		}
		if (programs.size() > 1) {
			print("More than one .lua file; keep only one.", true);
			runnerChanged();
			return;
		}
		int engines = countTouchingEngines();
		if (engines > 1) {
			print("Touches " + engines + " engines, needs exactly 1.", true);
			runnerChanged();
			return;
		}
		Path program = programs.getFirst();
		try {
			if (Files.size(program) > BillboardConfig.scriptMaxLuaKb() * 1024L) {
				print(program.getFileName() + " is larger than " + BillboardConfig.scriptMaxLuaKb() + " KB.", true);
				runnerChanged();
				return;
			}
			runner = ScriptRunner.start(this, program.getFileName().toString(), Files.readAllBytes(program));
			print("Started " + program.getFileName() + ".", false);
		} catch (IOException e) {
			print("Could not read " + program.getFileName() + ".", true);
		} catch (Exception e) {
			// Compile errors: the message already names the file and line.
			print(e.getMessage(), true);
		}
		runnerChanged();
	}

	public ScriptState scriptState() {
		if (runner != null && runner.isAlive()) {
			return ScriptState.RUNNING;
		}
		Line last = lines.peekLast();
		return last != null && last.error() ? ScriptState.ERROR : ScriptState.STOPPED;
	}

	/** Shows the program's state on the block, for the light on its label. */
	void runnerChanged() {
		version++;
		if (level == null || isRemoved()) {
			return;
		}
		BlockState state = getBlockState();
		ScriptState now = scriptState();
		if (state.hasProperty(ScriptBoxBlock.STATE) && state.getValue(ScriptBoxBlock.STATE) != now) {
			level.setBlock(worldPosition, state.setValue(ScriptBoxBlock.STATE, now), Block.UPDATE_CLIENTS);
		}
	}

	void print(String text, boolean error) {
		version++;
		for (String line : text.split("\n", -1)) {
			lines.addLast(new Line(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) + "..." : line, error));
		}
		while (lines.size() > MAX_LINES) {
			lines.removeFirst();
		}
	}

	public List<Line> lines() {
		return new ArrayList<>(lines);
	}

	public String statusText() {
		if (runner != null) {
			return switch (runner.status()) {
				case RUNNING, SLEEPING -> "Running";
				case ENDED -> "Ended";
				case FAILED -> "Error";
				case STOPPED -> "Stopped";
			};
		}
		return scriptState() == ScriptState.ERROR ? "Error" : "Stopped";
	}

	// ---- engine and files ----

	/** The one engine touching this box, or null if none or more than one touch it. */
	public BillboardEngineBlockEntity touchingEngine() {
		if (level == null) {
			return null;
		}
		BillboardEngineBlockEntity found = null;
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction)) instanceof BillboardEngineBlockEntity engine) {
				if (found != null) {
					return null;
				}
				found = engine;
			}
		}
		return found;
	}

	public int countTouchingEngines() {
		if (level == null) {
			return 0;
		}
		int count = 0;
		for (Direction direction : Direction.values()) {
			if (level.getBlockEntity(worldPosition.relative(direction)) instanceof BillboardEngineBlockEntity) {
				count++;
			}
		}
		return count;
	}

	/** This box's folder; gives the box an id first if it has none (for instance after /setblock). */
	public Path folder(MinecraftServer server) {
		if (id == null || ScriptStorage.folder(server, id) == null) {
			id = ScriptStorage.newId(server);
			setChanged();
		}
		return ScriptStorage.folder(server, id);
	}

	Path imageFile(String name) {
		return level instanceof ServerLevel serverLevel ? ScriptStorage.image(folder(serverLevel.getServer()), name) : null;
	}

	void onServerThread(Runnable task) {
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	/** Called when a player places the box: a fresh id, so a copied box never shares files with the original. */
	void placedBy(UUID player) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Path old = id == null ? null : ScriptStorage.folder(serverLevel.getServer(), id);
		id = ScriptStorage.newId(serverLevel.getServer());
		owner = player;
		ScriptStorage.copy(old, ScriptStorage.folder(serverLevel.getServer(), id));
		setChanged();
	}

	public UUID owner() {
		return owner;
	}

	public int version() {
		return version;
	}

	/** Whether this player may give the box files, remove them, restart or stop it: its owner, or an operator. */
	public boolean canEdit(ServerPlayer player) {
		boolean op = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
		if (BillboardConfig.scriptsOpsOnly()) {
			return op;
		}
		return op || player.getUUID().equals(owner);
	}

	/** Everything the screen shows, for this player. */
	public ScriptBoxStatePayload statePayload(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Path folder = folder(server);
		List<ScriptBoxStatePayload.FileEntry> files = new ArrayList<>();
		for (Path file : ScriptStorage.files(folder)) {
			long size;
			try {
				size = Files.size(file);
			} catch (IOException e) {
				size = 0;
			}
			files.add(new ScriptBoxStatePayload.FileEntry(file.getFileName().toString(), (int) Math.min(Integer.MAX_VALUE, size)));
		}
		List<ScriptBoxStatePayload.Line> shown = lines.stream().map(line -> new ScriptBoxStatePayload.Line(line.text(), line.error())).toList();
		boolean canEdit = canEdit(player);
		// A server's paths mean nothing on the player's computer and are none of their business.
		String localFolder = "";
		if (canEdit && !server.isDedicatedServer()) {
			try {
				Files.createDirectories(folder); // so the Folder button has something to open
				localFolder = folder.toString();
			} catch (IOException e) {
				// no button then
			}
		}
		return new ScriptBoxStatePayload(statusText(), canEdit, files, shown,
				BillboardConfig.scriptMaxLuaKb(), BillboardConfig.scriptMaxImages(), BillboardConfig.scriptMaxImageKb(), localFolder);
	}

	/**
	 * Stores a file a player dropped onto the screen, already checked by {@link ScriptUploads}. A program replaces the old
	 * program, whatever its name, and starts it. An image replaces the image of the same name; a running program
	 * sees it on its next drawImage, and a program that ended (perhaps missing this image) starts again.
	 */
	void storeFile(String name, byte[] data) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		Path folder = folder(serverLevel.getServer());
		try {
			Files.createDirectories(folder);
			if (ScriptStorage.isProgram(name)) {
				for (Path old : ScriptStorage.programs(folder)) {
					Files.deleteIfExists(old);
				}
			}
			Files.write(folder.resolve(name), data);
		} catch (IOException e) {
			print("Could not save " + name + ".", true);
			return;
		}
		print("Got " + name + " (" + Math.max(1, data.length / 1024) + " KB).", false);
		if (ScriptStorage.isProgram(name) || !halted && scriptState() != ScriptState.RUNNING) {
			restart();
		}
	}

	void deleteFile(String name) {
		if (!(level instanceof ServerLevel serverLevel) || !ScriptStorage.isValidName(name)) {
			return;
		}
		try {
			if (Files.deleteIfExists(folder(serverLevel.getServer()).resolve(name))) {
				print("Removed " + name + ".", false);
			}
		} catch (IOException e) {
			print("Could not remove " + name + ".", true);
		}
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.billboard.script_box");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new ScriptBoxMenu(containerId, inventory, this);
	}

	@Override
	public ScriptBoxMenu.OpenData getScreenOpeningData(ServerPlayer player) {
		return new ScriptBoxMenu.OpenData(worldPosition);
	}

	// ---- life cycle ----

	@Override
	public void setRemoved() {
		// Unloaded or broken: the program ends and frees the engine. Removed first, so nothing touches the block any more.
		super.setRemoved();
		if (runner != null && runner.isAlive()) {
			runner.stop();
		}
		runner = null;
		started = false;
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// The box is broken: it drops blank, and its files go with it. They never were items in the world.
		if (level instanceof ServerLevel serverLevel && id != null) {
			ScriptStorage.delete(ScriptStorage.folder(serverLevel.getServer(), id));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		id = input.getStringOr("script_id", null);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		halted = input.getBooleanOr("halted", false);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (id != null) {
			output.putString("script_id", id);
		}
		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
		output.putBoolean("halted", halted);
	}
}
