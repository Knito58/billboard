package de.knito.billboard.net;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.Billboard;

/**
 * What the Script Box screen shows: state, files, output lines, whether this player may change the box,
 * and the server's upload limits. Sent when the screen opens and whenever something changes. folder is the box's
 * folder on disk, only in single player (empty otherwise), so the screen can open it and its files.
 */
public record ScriptBoxStatePayload(String status, boolean canEdit, List<FileEntry> files, List<Line> lines,
		int maxLuaKb, int maxImages, int maxImageKb, String folder) implements CustomPacketPayload {
	public record FileEntry(String name, int size) {
		static final StreamCodec<ByteBuf, FileEntry> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(256), FileEntry::name,
				ByteBufCodecs.VAR_INT, FileEntry::size,
				FileEntry::new
		);
	}

	public record Line(String text, boolean error) {
		static final StreamCodec<ByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.stringUtf8(1024), Line::text,
				ByteBufCodecs.BOOL, Line::error,
				Line::new
		);
	}

	public static final Type<ScriptBoxStatePayload> TYPE = new Type<>(Billboard.id("script_box_state"));

	public static final StreamCodec<ByteBuf, ScriptBoxStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), ScriptBoxStatePayload::status,
			ByteBufCodecs.BOOL, ScriptBoxStatePayload::canEdit,
			FileEntry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ScriptBoxStatePayload::files,
			Line.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ScriptBoxStatePayload::lines,
			ByteBufCodecs.VAR_INT, ScriptBoxStatePayload::maxLuaKb,
			ByteBufCodecs.VAR_INT, ScriptBoxStatePayload::maxImages,
			ByteBufCodecs.VAR_INT, ScriptBoxStatePayload::maxImageKb,
			ByteBufCodecs.stringUtf8(4096), ScriptBoxStatePayload::folder,
			ScriptBoxStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
