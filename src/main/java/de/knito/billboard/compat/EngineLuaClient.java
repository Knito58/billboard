package de.knito.billboard.compat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.server.MinecraftServer;

import de.knito.billboard.api.BillboardApi;
import de.knito.billboard.api.LuaArgs;
import de.knito.billboard.api.LuaCaller;
import de.knito.billboard.api.LuaClient;
import de.knito.billboard.api.LuaException;
import de.knito.billboard.api.LuaMethods;
import de.knito.billboard.ModBlockEntities;
import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.block.EngineControl;
import de.knito.billboard.block.EngineController;
import de.knito.billboard.wall.Picture;

/**
 * The engine as a Lua client of Billboard API, type {@code billboard:engine}: a Lua Box drives it through a pipe
 * or by touching it, and the old {@code engine} table of Script Box programs reaches it the same way. The functions
 * are those of the CC: Tweaked peripheral; both go through {@link EngineControl}.
 */
public final class EngineLuaClient implements LuaClient {
	public static final String TYPE = "billboard:engine";

	/** Reads and decodes image files for drawImage, so a large GIF does not hold up the tick. */
	private static final ExecutorService IMAGES = Executors.newFixedThreadPool(2,
			Thread.ofPlatform().daemon().name("Billboard Engine Images ", 1).factory());
	/** Decoded images by file, so a slide show does not decode the same file again and again. */
	private static final Map<Path, CachedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
	private static final int CACHE_SIZE = 32;

	private record CachedImage(long modified, long size, Picture.Source source) {
	}

	/** A Lua Box program as the engine's claim holder; the same program run is the same controller. */
	private record BoxController(LuaCaller caller) implements EngineController {
		@Override
		public String describe() {
			return caller.describe();
		}

		@Override
		public boolean isActive() {
			return caller.isActive();
		}
	}

	@FunctionalInterface
	private interface Call {
		Object call(EngineControl control, LuaArgs args) throws LuaException, EngineControl.Failure;
	}

	private final BillboardEngineBlockEntity engine;

	private EngineLuaClient(BillboardEngineBlockEntity engine) {
		this.engine = engine;
	}

	public static void initialize() {
		BillboardApi.CLIENTS.registerForBlockEntity((engine, side) -> new EngineLuaClient(engine), ModBlockEntities.BILLBOARD_ENGINE);
	}

	@Override
	public String type() {
		return TYPE;
	}

	@Override
	public void methods(LuaMethods m) {
		add(m, "getText", (c, a) -> c.getText());
		add(m, "setText", (c, a) -> {
			c.setText(text(a, 1));
			return null;
		});
		add(m, "getSpeed", (c, a) -> c.getSpeed());
		add(m, "setSpeed", (c, a) -> {
			c.setSpeed(a.integer(1));
			return null;
		});
		add(m, "getSize", (c, a) -> c.getSize());
		add(m, "setSize", (c, a) -> {
			c.setSize(a.integer(1));
			return null;
		});
		add(m, "getPixel", (c, a) -> c.getPixel());
		add(m, "setPixel", (c, a) -> {
			c.setPixel(a.string(1));
			return null;
		});
		add(m, "isRunning", (c, a) -> c.isRunning());
		add(m, "isShowing", (c, a) -> c.isShowing());
		add(m, "go", (c, a) -> c.go(a.isNil(1) ? null : a.integer(1)));
		add(m, "show", (c, a) -> c.show(text(a, 1)));
		add(m, "runOff", (c, a) -> c.runOff());
		add(m, "isRunningOff", (c, a) -> c.isRunningOff());
		add(m, "stop", (c, a) -> c.stop());
		add(m, "restore", (c, a) -> c.restore());
		add(m, "drawPixels", (c, a) -> c.drawPixels(a.integer(1), a.integer(2), a.integer(3), a.integer(4), a.bytes(5)));
		add(m, "getWallSize", (c, a) -> c.getWallSize());
		add(m, "isPainting", (c, a) -> c.isPainting());
		add(m, "isShowingPicture", (c, a) -> c.isShowingPicture());
		add(m, "play", (c, a) -> c.play());
		add(m, "claim", (c, a) -> c.claim());
		add(m, "release", (c, a) -> c.release());
		add(m, "isClaimed", (c, a) -> c.isClaimed());
		add(m, "drawImage", this::drawImage);
	}

	private void add(LuaMethods methods, String name, Call call) {
		methods.add(name, args -> {
			try {
				return call.call(control(args), args);
			} catch (EngineControl.Failure e) {
				throw new LuaException(e.getMessage());
			}
		});
	}

	private EngineControl control(LuaArgs args) {
		LuaCaller caller = args.caller();
		return new EngineControl(engine, caller == null ? null : new BoxController(caller));
	}

	/**
	 * Shows an image file of the calling box: decoded on a helper thread, then put on the wall on the server
	 * thread. The program waits for it, as it did in the old Script Box.
	 */
	private Object drawImage(EngineControl control, LuaArgs args) throws LuaException, EngineControl.Failure {
		String name = args.string(1);
		Map<String, ?> options = args.isNil(2) ? Map.of() : args.map(2);
		Picture.Fit fit = EngineControl.fit(options.get("fit"));
		boolean dither = truthy(options.get("dither"));
		boolean loop = options.get("loop") == null || truthy(options.get("loop"));
		LuaCaller caller = args.caller();
		Path file = caller == null ? null : caller.file(name);
		if (file == null) {
			throw new LuaException("no image called " + name + " in this Lua Box");
		}
		MinecraftServer server = engine.getLevel() == null ? null : engine.getLevel().getServer();
		if (server == null) {
			throw new LuaException("the engine is not loaded");
		}
		CompletableFuture<Picture.Source> source;
		try {
			long modified = Files.getLastModifiedTime(file).toMillis();
			long size = Files.size(file);
			CachedImage cached = IMAGE_CACHE.get(file);
			if (cached != null && cached.modified() == modified && cached.size() == size) {
				source = CompletableFuture.completedFuture(cached.source());
			} else {
				source = CompletableFuture.supplyAsync(() -> {
					try {
						Picture.Source decoded = Picture.decode(Files.readAllBytes(file));
						if (IMAGE_CACHE.size() >= CACHE_SIZE) {
							IMAGE_CACHE.clear();
						}
						IMAGE_CACHE.put(file, new CachedImage(modified, size, decoded));
						return decoded;
					} catch (IOException e) {
						throw new CompletionException(new LuaException("could not read " + name));
					} catch (Picture.PictureException e) {
						throw new CompletionException(new LuaException(name + ": " + e.getMessage()));
					}
				}, IMAGES);
			}
		} catch (IOException e) {
			throw new LuaException("could not read " + name);
		}
		return source.thenApplyAsync(decoded -> {
			try {
				return control.showPicture(decoded, fit, dither, loop);
			} catch (EngineControl.Failure e) {
				throw new CompletionException(new LuaException(e.getMessage()));
			}
		}, server);
	}

	/**
	 * Text for the wall. Billboard API reads Lua strings as UTF-8, but "\183" (the middle dot) is a single byte
	 * that is no valid UTF-8, as in CC: Tweaked and the old Script Box. So text that is not valid UTF-8 is read as
	 * Latin-1, like there; a "·" typed into a UTF-8 file still works too.
	 */
	private static String text(LuaArgs args, int index) throws LuaException {
		byte[] bytes = args.bytes(index);
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			return new String(bytes, StandardCharsets.ISO_8859_1);
		}
	}

	/** Lua's idea of true: anything but nil and false. */
	private static boolean truthy(Object value) {
		return value != null && !Boolean.FALSE.equals(value);
	}
}
