package de.knito.billboard.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.Billboard;

/** Result of a button press, shown in the engine screen's status line. */
public record EngineStatusPayload(Component message) implements CustomPacketPayload {
	public static final Type<EngineStatusPayload> TYPE = new Type<>(Billboard.id("engine_status"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EngineStatusPayload> STREAM_CODEC = StreamCodec.composite(
			ComponentSerialization.TRUSTED_STREAM_CODEC, EngineStatusPayload::message,
			EngineStatusPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
