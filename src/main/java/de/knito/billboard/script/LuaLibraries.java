package de.knito.billboard.script;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.ServerLevel;

import de.knito.billboard.block.BillboardEngineBlockEntity;
import de.knito.billboard.block.EngineControl;
import de.knito.billboard.lua.cobalt.Constants;
import de.knito.billboard.lua.cobalt.LuaError;
import de.knito.billboard.lua.cobalt.LuaState;
import de.knito.billboard.lua.cobalt.LuaString;
import de.knito.billboard.lua.cobalt.LuaTable;
import de.knito.billboard.lua.cobalt.LuaThread;
import de.knito.billboard.lua.cobalt.LuaValue;
import de.knito.billboard.lua.cobalt.ValueFactory;
import de.knito.billboard.lua.cobalt.Varargs;
import de.knito.billboard.lua.cobalt.compiler.CompileException;
import de.knito.billboard.lua.cobalt.compiler.LoadState;
import de.knito.billboard.lua.cobalt.function.RegisteredFunction;
import de.knito.billboard.wall.Picture;

/**
 * What a Script Box program sees besides standard Lua: the {@code engine} table, sleep, print, and the parts of
 * CC: Tweaked's {@code os} and {@code textutils} that clock scripts need. No files, no network, no peripherals.
 */
final class LuaLibraries {
	/** Written in Lua, because sleep() and drawImage() must be able to pause the program. */
	private static final String PRELUDE = """
			local java, SLEEP = ...
			local yield, select, tostring, type, concat, error = coroutine.yield, select, tostring, type, table.concat, error

			function sleep(seconds)
				seconds = seconds or 0
				if type(seconds) ~= "number" then
					error("bad argument #1 to 'sleep' (number expected, got " .. type(seconds) .. ")", 2)
				end
				yield(SLEEP, seconds)
			end

			function print(...)
				local parts = {}
				for i = 1, select("#", ...) do parts[i] = tostring((select(i, ...))) end
				java.print(concat(parts, " "))
			end

			local startImage, pollImage, pcall, unpack = java.startImage, java.pollImage, pcall, table.unpack
			function engine.drawImage(name, options)
				local started, job = pcall(startImage, name, options)
				if not started then error(job, 2) end
				while true do
					local result = table.pack(pcall(pollImage, job))
					if not result[1] then error(result[2], 2) end
					if result[2] then return unpack(result, 3, result.n) end
					yield(SLEEP, 0)
				end
			end

			textutils = {}
			function textutils.formatTime(time, twentyFour)
				if type(time) ~= "number" then
					error("bad argument #1 to 'formatTime' (number expected, got " .. type(time) .. ")", 2)
				end
				local suffix
				if not twentyFour then
					suffix = time >= 12 and "PM" or "AM"
					if time >= 13 then time = time - 12 end
				end
				local hour = math.floor(time)
				local minute = math.floor((time - hour) * 60)
				if suffix then
					return string.format("%d:%02d %s", hour == 0 and 12 or hour, minute, suffix)
				end
				return string.format("%d:%02d", hour, minute)
			end
			""";

	private final ScriptBoxBlockEntity box;
	private final LuaState state;
	private final long startTick;
	private ScriptRunner runner;
	private final Map<Integer, ImageJob> imageJobs = new HashMap<>();
	private int nextJob = 1;
	/** Decoded images by file name, so a slide show does not decode the same file again and again. */
	private final Map<String, CachedImage> imageCache = new HashMap<>();

	private record ImageJob(String name, CompletableFuture<Picture.Source> source, Picture.Fit fit, boolean dither, boolean loop) {
	}

	private record CachedImage(long modified, long size, Picture.Source source) {
	}

	LuaLibraries(ScriptBoxBlockEntity box, LuaState state) {
		this.box = box;
		this.state = state;
		this.startTick = box.getLevel() == null ? 0 : box.getLevel().getGameTime();
	}

	void install(ScriptRunner owner, LuaTable sleepToken) throws LuaError, CompileException {
		runner = owner;
		LuaTable globals = state.globals();
		globals.rawset("engine", engineTable());
		globals.rawset("os", osTable());

		LuaTable java = RegisteredFunction.bind(new RegisteredFunction[]{
				RegisteredFunction.of("print", (s, line) -> {
					box.print(line.checkString(), false);
					return Constants.NIL;
				}),
				RegisteredFunction.of("startImage", (s, name, options) -> startImage(name.checkString(), options.optTable(null))),
				RegisteredFunction.ofV("pollImage", (s, args) -> pollImage(args.arg(1).checkInteger())),
		});
		LuaValue prelude = LoadState.load(state, new ByteArrayInputStream(PRELUDE.getBytes(StandardCharsets.UTF_8)), "=prelude", globals);
		LuaThread.runMain(state, prelude.checkFunction(), ValueFactory.varargsOf(java, sleepToken));
	}

	/** Called when the program ends: frees the engine and drops waiting image jobs. */
	void close() {
		BillboardEngineBlockEntity engine = box.touchingEngine();
		if (engine != null) {
			engine.release(runner);
		}
		imageJobs.values().forEach(job -> job.source().cancel(false));
		imageJobs.clear();
		imageCache.clear();
	}

	// ---- engine ----

	private EngineControl control() throws LuaError {
		return control(1);
	}

	/** level as in Lua's error(): 1 names the calling line, 0 adds no position (the prelude adds its caller's). */
	private EngineControl control(int level) throws LuaError {
		BillboardEngineBlockEntity engine = box.touchingEngine();
		if (engine == null) {
			int count = box.countTouchingEngines();
			throw new LuaError(count == 0 ? "no engine touches this Script Box" : "touches " + count + " engines, needs exactly 1", level);
		}
		return new EngineControl(engine, runner);
	}

	@FunctionalInterface
	private interface EngineCall {
		Object call(EngineControl control, Varargs args) throws LuaError, EngineControl.Failure;
	}

	private RegisteredFunction engineFunction(String name, EngineCall call) {
		return RegisteredFunction.ofV(name, (s, args) -> {
			try {
				return toLua(call.call(control(), args));
			} catch (EngineControl.Failure e) {
				throw new LuaError(e.getMessage());
			}
		});
	}

	private LuaTable engineTable() {
		return RegisteredFunction.bind(new RegisteredFunction[]{
				engineFunction("getText", (c, a) -> c.getText()),
				engineFunction("setText", (c, a) -> {
					c.setText(a.arg(1).checkString());
					return null;
				}),
				engineFunction("getSpeed", (c, a) -> c.getSpeed()),
				engineFunction("setSpeed", (c, a) -> {
					c.setSpeed(a.arg(1).checkInteger());
					return null;
				}),
				engineFunction("getSize", (c, a) -> c.getSize()),
				engineFunction("setSize", (c, a) -> {
					c.setSize(a.arg(1).checkInteger());
					return null;
				}),
				engineFunction("getPixel", (c, a) -> c.getPixel()),
				engineFunction("setPixel", (c, a) -> {
					c.setPixel(a.arg(1).checkString());
					return null;
				}),
				engineFunction("isRunning", (c, a) -> c.isRunning()),
				engineFunction("isShowing", (c, a) -> c.isShowing()),
				engineFunction("go", (c, a) -> c.go(a.arg(1).isNil() ? null : a.arg(1).checkInteger())),
				engineFunction("show", (c, a) -> c.show(a.arg(1).checkString())),
				engineFunction("runOff", (c, a) -> c.runOff()),
				engineFunction("isRunningOff", (c, a) -> c.isRunningOff()),
				engineFunction("stop", (c, a) -> c.stop()),
				engineFunction("restore", (c, a) -> c.restore()),
				engineFunction("drawPixels", (c, a) -> {
					LuaString data = a.arg(5).checkLuaString();
					byte[] bytes = new byte[data.length()];
					data.copyTo(bytes, 0);
					return c.drawPixels(a.arg(1).checkInteger(), a.arg(2).checkInteger(), a.arg(3).checkInteger(), a.arg(4).checkInteger(), bytes);
				}),
				engineFunction("getWallSize", (c, a) -> c.getWallSize()),
				engineFunction("isPainting", (c, a) -> c.isPainting()),
				engineFunction("isShowingPicture", (c, a) -> c.isShowingPicture()),
				engineFunction("play", (c, a) -> c.play()),
				engineFunction("claim", (c, a) -> c.claim()),
				engineFunction("release", (c, a) -> c.release()),
				engineFunction("isClaimed", (c, a) -> c.isClaimed()),
		});
	}

	/** Starts reading and decoding an image file next to the program; the result is fetched with pollImage. */
	private LuaValue startImage(String name, LuaTable options) throws LuaError {
		Picture.Fit fit;
		try {
			fit = EngineControl.fit(options == null || options.rawget("fit").isNil() ? null : options.rawget("fit").checkString());
		} catch (EngineControl.Failure e) {
			throw new LuaError(e.getMessage(), 0);
		}
		boolean dither = options != null && options.rawget("dither").toBoolean();
		boolean loop = options == null || options.rawget("loop").isNil() || options.rawget("loop").toBoolean();
		Path file = box.imageFile(name);
		if (file == null) {
			throw new LuaError("no image called " + name + " in this Script Box", 0);
		}
		CompletableFuture<Picture.Source> source;
		try {
			long modified = Files.getLastModifiedTime(file).toMillis();
			long size = Files.size(file);
			CachedImage cached = imageCache.get(name);
			if (cached != null && cached.modified() == modified && cached.size() == size) {
				source = CompletableFuture.completedFuture(cached.source());
			} else {
				source = CompletableFuture.supplyAsync(() -> {
					try {
						return Picture.decode(Files.readAllBytes(file));
					} catch (IOException e) {
						throw new IllegalStateException("could not read " + name);
					} catch (Picture.PictureException e) {
						throw new IllegalStateException(name + ": " + e.getMessage());
					}
				}, ScriptThreads.IMAGES);
				source.thenAccept(decoded -> box.onServerThread(() -> imageCache.put(name, new CachedImage(modified, size, decoded))));
			}
		} catch (IOException e) {
			throw new LuaError("could not read " + name, 0);
		}
		int id = nextJob++;
		imageJobs.put(id, new ImageJob(name, source, fit, dither, loop));
		return ValueFactory.valueOf(id);
	}

	/** false while the image is still being decoded; then true plus drawImage's results. */
	private Varargs pollImage(int id) throws LuaError {
		ImageJob job = imageJobs.get(id);
		if (job == null) {
			throw new LuaError("unknown image job");
		}
		if (!job.source().isDone()) {
			return Constants.FALSE;
		}
		imageJobs.remove(id);
		Picture.Source source;
		try {
			source = job.source().join();
		} catch (RuntimeException e) {
			Throwable cause = e.getCause() != null ? e.getCause() : e;
			throw new LuaError(cause.getMessage(), 0);
		}
		try {
			Varargs result = toLua(control(0).showPicture(source, job.fit(), job.dither(), job.loop()));
			return ValueFactory.varargsOf(Constants.TRUE, result);
		} catch (EngineControl.Failure e) {
			throw new LuaError(e.getMessage(), 0);
		}
	}

	// ---- os, as in CC: Tweaked ----

	private LuaTable osTable() {
		return RegisteredFunction.bind(new RegisteredFunction[]{
				RegisteredFunction.ofV("time", (s, args) -> time(args.arg(1))),
				RegisteredFunction.ofV("date", (s, args) -> date(args.arg(1).optString("%c"), args.arg(2))),
				RegisteredFunction.of("clock", s -> ValueFactory.valueOf((gameTime() - startTick) / 20.0)),
				RegisteredFunction.of("day", (s, kind) -> ValueFactory.valueOf(day(kind.optString("ingame")))),
				RegisteredFunction.of("epoch", (s, kind) -> ValueFactory.valueOf((double) epoch(kind.optString("ingame")))),
		});
	}

	private long gameTime() {
		return box.getLevel() == null ? startTick : box.getLevel().getGameTime();
	}

	/** Time of day in game ticks, 0 = 6:00 in the morning. */
	private long dayTime() {
		return box.getLevel() instanceof ServerLevel level ? ScriptBoxBlockEntity.dayTime(level) : 0;
	}

	private Varargs time(LuaValue arg) throws LuaError {
		if (arg instanceof LuaTable) {
			LuaTable table = arg.checkTable();
			LocalDateTime local = LocalDateTime.of(
					field(table, "year", null), field(table, "month", null), 1,
					field(table, "hour", 12), field(table, "min", 0), field(table, "sec", 0));
			local = local.plusDays(field(table, "day", null) - 1L);
			return ValueFactory.valueOf(local.atZone(ZoneId.systemDefault()).toEpochSecond());
		}
		String kind = arg.optString("ingame").toLowerCase(Locale.ROOT);
		return switch (kind) {
			case "ingame" -> ValueFactory.valueOf(((dayTime() + 6000) % 24000) / 1000.0);
			case "utc" -> ValueFactory.valueOf(Instant.now().getEpochSecond());
			case "local" -> ValueFactory.valueOf(Instant.now().getEpochSecond() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds());
			default -> throw new LuaError("Unsupported operation");
		};
	}

	private static int field(LuaTable table, String key, Integer fallback) throws LuaError {
		LuaValue value = table.rawget(key);
		if (value.isNil()) {
			if (fallback == null) {
				throw new LuaError("field '" + key + "' missing in date table");
			}
			return fallback;
		}
		return value.checkInteger();
	}

	private int day(String kind) throws LuaError {
		return switch (kind.toLowerCase(Locale.ROOT)) {
			case "ingame" -> (int) ((dayTime() + 6000) / 24000) + 1;
			case "utc" -> (int) (Instant.now().getEpochSecond() / 86400);
			case "local" -> (int) ((Instant.now().getEpochSecond() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds()) / 86400);
			default -> throw new LuaError("Unsupported operation");
		};
	}

	private long epoch(String kind) throws LuaError {
		return switch (kind.toLowerCase(Locale.ROOT)) {
			// Milliseconds of game time, where one in-game day lasts 86 400 000.
			case "ingame" -> (dayTime() + 6000) * 3600;
			case "utc" -> System.currentTimeMillis();
			case "local" -> System.currentTimeMillis() + ZoneId.systemDefault().getRules().getOffset(Instant.now()).getTotalSeconds() * 1000L;
			default -> throw new LuaError("Unsupported operation");
		};
	}

	/** os.date with the strftime codes of standard Lua; "!" in front means UTC, "*t" gives a table. */
	private static Varargs date(String format, LuaValue timeArg) throws LuaError {
		long seconds = timeArg.isNil() ? Instant.now().getEpochSecond() : timeArg.checkLong();
		ZoneId zone = ZoneId.systemDefault();
		if (format.startsWith("!")) {
			zone = ZoneOffset.UTC;
			format = format.substring(1);
		}
		ZonedDateTime t = Instant.ofEpochSecond(seconds).atZone(zone);
		if (format.startsWith("*t")) {
			LuaTable table = new LuaTable();
			table.rawset("year", ValueFactory.valueOf(t.getYear()));
			table.rawset("month", ValueFactory.valueOf(t.getMonthValue()));
			table.rawset("day", ValueFactory.valueOf(t.getDayOfMonth()));
			table.rawset("hour", ValueFactory.valueOf(t.getHour()));
			table.rawset("min", ValueFactory.valueOf(t.getMinute()));
			table.rawset("sec", ValueFactory.valueOf(t.getSecond()));
			table.rawset("wday", ValueFactory.valueOf(t.getDayOfWeek().getValue() % 7 + 1));
			table.rawset("yday", ValueFactory.valueOf(t.getDayOfYear()));
			table.rawset("isdst", ValueFactory.valueOf(zone.getRules().isDaylightSavings(t.toInstant())));
			return table;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < format.length(); i++) {
			char c = format.charAt(i);
			if (c != '%' || i + 1 >= format.length()) {
				out.append(c);
				continue;
			}
			char code = format.charAt(++i);
			switch (code) {
				case 'a' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case 'A' -> out.append(t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
				case 'b', 'h' -> out.append(t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case 'B' -> out.append(t.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
				case 'c' -> out.append(String.format(Locale.ROOT, "%s %s %2d %02d:%02d:%02d %d",
						t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH), t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
						t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getYear()));
				case 'd' -> out.append(String.format(Locale.ROOT, "%02d", t.getDayOfMonth()));
				case 'e' -> out.append(String.format(Locale.ROOT, "%2d", t.getDayOfMonth()));
				case 'D' -> out.append(String.format(Locale.ROOT, "%02d/%02d/%02d", t.getMonthValue(), t.getDayOfMonth(), t.getYear() % 100));
				case 'F' -> out.append(String.format(Locale.ROOT, "%d-%02d-%02d", t.getYear(), t.getMonthValue(), t.getDayOfMonth()));
				case 'H' -> out.append(String.format(Locale.ROOT, "%02d", t.getHour()));
				case 'I' -> out.append(String.format(Locale.ROOT, "%02d", (t.getHour() + 11) % 12 + 1));
				case 'j' -> out.append(String.format(Locale.ROOT, "%03d", t.getDayOfYear()));
				case 'm' -> out.append(String.format(Locale.ROOT, "%02d", t.getMonthValue()));
				case 'M' -> out.append(String.format(Locale.ROOT, "%02d", t.getMinute()));
				case 'n' -> out.append('\n');
				case 'p' -> out.append(t.getHour() < 12 ? "AM" : "PM");
				case 'R' -> out.append(String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()));
				case 'S' -> out.append(String.format(Locale.ROOT, "%02d", t.getSecond()));
				case 't' -> out.append('\t');
				case 'T', 'X' -> out.append(String.format(Locale.ROOT, "%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()));
				case 'u' -> out.append(t.getDayOfWeek().getValue());
				case 'w' -> out.append(t.getDayOfWeek().getValue() % 7);
				case 'x' -> out.append(String.format(Locale.ROOT, "%02d/%02d/%02d", t.getMonthValue(), t.getDayOfMonth(), t.getYear() % 100));
				case 'y' -> out.append(String.format(Locale.ROOT, "%02d", t.getYear() % 100));
				case 'Y' -> out.append(t.getYear());
				case 'z' -> {
					int offset = t.getOffset().getTotalSeconds() / 60;
					out.append(String.format(Locale.ROOT, "%c%02d%02d", offset < 0 ? '-' : '+', Math.abs(offset) / 60, Math.abs(offset) % 60));
				}
				case 'Z' -> out.append(zone.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
				case '%' -> out.append('%');
				default -> throw new LuaError("bad argument #1 to 'date' (invalid conversion specifier '%" + code + "')");
			}
		}
		return ValueFactory.valueOf(out.toString());
	}

	// ---- values ----

	/** Java results to Lua: null becomes nil, an Object[] becomes several return values. */
	private static Varargs toLua(Object value) {
		if (value instanceof Object[] values) {
			List<LuaValue> list = new ArrayList<>(values.length);
			for (Object v : values) {
				list.add(single(v));
			}
			return ValueFactory.varargsOf(list.toArray(LuaValue[]::new));
		}
		return value == null ? Constants.NONE : single(value);
	}

	private static LuaValue single(Object value) {
		return switch (value) {
			case null -> Constants.NIL;
			case Boolean b -> ValueFactory.valueOf(b);
			case Integer i -> ValueFactory.valueOf(i);
			case Number n -> ValueFactory.valueOf(n.doubleValue());
			default -> ValueFactory.valueOf(value.toString());
		};
	}
}
