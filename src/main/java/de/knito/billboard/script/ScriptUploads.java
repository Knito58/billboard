package de.knito.billboard.script;

import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.server.level.ServerPlayer;

import de.knito.billboard.BillboardConfig;
import de.knito.billboard.menu.ScriptBoxMenu;
import de.knito.billboard.net.ScriptBoxActionPayload;
import de.knito.billboard.net.ScriptUploadPayload;
import de.knito.billboard.wall.Picture;

/**
 * Files dropped onto the Script Box screen arrive here in pieces. The server checks everything again, whatever
 * the client checked: who may upload, names, sizes, the number of images, and that an image really is one.
 */
public final class ScriptUploads {
	/** The file a player is sending right now, one per player. */
	private record Upload(ScriptBoxBlockEntity box, String name, int total, ByteArrayOutputStream data) {
	}

	private static final Map<UUID, Upload> UPLOADS = new HashMap<>();

	private ScriptUploads() {
	}

	/** The box whose screen this player has open, if they may change it; otherwise tells them why not. */
	private static ScriptBoxBlockEntity editableBox(ServerPlayer player) {
		if (!(player.containerMenu instanceof ScriptBoxMenu menu) || menu.box() == null || !menu.stillValid(player)) {
			return null;
		}
		ScriptBoxBlockEntity box = menu.box();
		if (!box.canEdit(player)) {
			box.print(player.getName().getString() + " may not change this Script Box; only its owner"
					+ (BillboardConfig.scriptsOpsOnly() ? " (server: operators only)" : " and operators") + " may.", true);
			return null;
		}
		return box;
	}

	public static void action(ServerPlayer player, ScriptBoxActionPayload payload) {
		ScriptBoxBlockEntity box = editableBox(player);
		if (box == null) {
			return;
		}
		switch (payload.action()) {
			case RESTART -> box.restart();
			case STOP -> box.stop();
			case DELETE -> box.deleteFile(payload.name());
		}
	}

	public static void receive(ServerPlayer player, ScriptUploadPayload piece) {
		Upload upload = UPLOADS.get(player.getUUID());
		if (piece.offset() == 0) {
			ScriptBoxBlockEntity box = editableBox(player);
			if (box == null) {
				UPLOADS.remove(player.getUUID());
				return;
			}
			String problem = check(box, piece.name(), piece.total());
			if (problem != null) {
				box.print(problem, true);
				UPLOADS.remove(player.getUUID());
				return;
			}
			upload = new Upload(box, piece.name(), piece.total(), new ByteArrayOutputStream(piece.total()));
			UPLOADS.put(player.getUUID(), upload);
		}
		// A piece out of order, for another file, or for a box that is gone ends the upload.
		if (upload == null || !upload.name().equals(piece.name()) || upload.data().size() != piece.offset()
				|| upload.box().isRemoved() || !(player.containerMenu instanceof ScriptBoxMenu menu) || menu.box() != upload.box()) {
			UPLOADS.remove(player.getUUID());
			return;
		}
		upload.data().write(piece.data(), 0, piece.data().length);
		if (upload.data().size() > upload.total()) {
			upload.box().print(upload.name() + ": more data than announced, dropped.", true);
			UPLOADS.remove(player.getUUID());
			return;
		}
		if (upload.data().size() == upload.total()) {
			UPLOADS.remove(player.getUUID());
			finish(upload);
		}
	}

	/** Forgets a half-sent file when the player leaves. */
	public static void forget(UUID player) {
		UPLOADS.remove(player);
	}

	/** Why this file may not go into the box, or null if it may. */
	static String check(ScriptBoxBlockEntity box, String name, int size) {
		if (!ScriptStorage.isValidName(name)) {
			return name + ": please use only letters, digits, _ - and . in file names.";
		}
		boolean program = ScriptStorage.isProgram(name);
		if (!program && !ScriptStorage.isImage(name)) {
			return name + ": only one .lua file and images (.png, .jpg, .gif, .bmp) go into a Script Box.";
		}
		if (size <= 0) {
			return name + " is empty.";
		}
		if (program && size > BillboardConfig.scriptMaxLuaKb() * 1024) {
			return name + " is larger than " + BillboardConfig.scriptMaxLuaKb() + " KB.";
		}
		if (!program && size > BillboardConfig.scriptMaxImageKb() * 1024) {
			return name + " is larger than " + BillboardConfig.scriptMaxImageKb() + " KB.";
		}
		if (!program && box.getLevel() != null && box.getLevel().getServer() != null) {
			Path folder = box.folder(box.getLevel().getServer());
			long images = ScriptStorage.files(folder).stream()
					.map(p -> p.getFileName().toString())
					.filter(n -> ScriptStorage.isImage(n) && !n.equals(name))
					.count();
			if (images >= BillboardConfig.scriptMaxImages()) {
				return "This box already holds " + images + " images, the most allowed. Remove one first.";
			}
		}
		return null;
	}

	private static void finish(Upload upload) {
		byte[] data = upload.data().toByteArray();
		ScriptBoxBlockEntity box = upload.box();
		if (ScriptStorage.isProgram(upload.name())) {
			try {
				StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data));
			} catch (CharacterCodingException e) {
				box.print(upload.name() + " is not a text file in UTF-8.", true);
				return;
			}
			box.storeFile(upload.name(), data);
			return;
		}
		// Decoding a big GIF takes a moment; do it beside the game, then store the file in the tick.
		box.print("Checking " + upload.name() + "...", false);
		CompletableFuture.runAsync(() -> {
			String problem = null;
			try {
				Picture.decode(data);
			} catch (Picture.PictureException e) {
				problem = upload.name() + ": " + e.getMessage();
			} catch (RuntimeException e) {
				problem = upload.name() + " is not an image the mod can read.";
			}
			String result = problem;
			box.onServerThread(() -> {
				if (box.isRemoved()) {
					return;
				}
				if (result != null) {
					box.print(result, true);
				} else if (check(box, upload.name(), data.length) == null) {
					box.storeFile(upload.name(), data);
				}
			});
		}, ScriptThreads.IMAGES);
	}
}
