package de.knito.billboard.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import de.knito.billboard.Billboard;

/** A block dragged from a recipe viewer (JEI, REI) onto the pixel slot. Only the item id travels; the server checks it. */
public record PixelSamplePayload(Identifier item) implements CustomPacketPayload {
	public static final Type<PixelSamplePayload> TYPE = new Type<>(Billboard.id("pixel_sample"));

	public static final StreamCodec<ByteBuf, PixelSamplePayload> STREAM_CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, PixelSamplePayload::item,
			PixelSamplePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
