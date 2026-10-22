package de.knito.billboard.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.Billboard;

/**
 * One piece of a file dropped onto the Script Box screen. A packet from client to server may hold only about
 * 32 KB, so a file comes in pieces of {@link #CHUNK} bytes, in order; offset says where this piece belongs.
 */
public record ScriptUploadPayload(String name, int total, int offset, byte[] data) implements CustomPacketPayload {
	public static final int CHUNK = 30000;

	public static final Type<ScriptUploadPayload> TYPE = new Type<>(Billboard.id("script_upload"));

	public static final StreamCodec<ByteBuf, ScriptUploadPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(256), ScriptUploadPayload::name,
			ByteBufCodecs.VAR_INT, ScriptUploadPayload::total,
			ByteBufCodecs.VAR_INT, ScriptUploadPayload::offset,
			ByteBufCodecs.byteArray(CHUNK), ScriptUploadPayload::data,
			ScriptUploadPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
