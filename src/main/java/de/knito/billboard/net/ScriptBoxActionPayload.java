package de.knito.billboard.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.Billboard;

/** A button in the Script Box screen: Restart, Stop, or remove the file with the given name. */
public record ScriptBoxActionPayload(Action action, String name) implements CustomPacketPayload {
	public enum Action {
		RESTART, STOP, DELETE
	}

	public static final Type<ScriptBoxActionPayload> TYPE = new Type<>(Billboard.id("script_box_action"));

	public static final StreamCodec<ByteBuf, ScriptBoxActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), ScriptBoxActionPayload::action,
			ByteBufCodecs.stringUtf8(256), ScriptBoxActionPayload::name,
			ScriptBoxActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
