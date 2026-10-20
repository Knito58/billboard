package de.knito.billboard.net;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import de.knito.billboard.Billboard;
import de.knito.billboard.block.BillboardEngineBlockEntity;

/** A button press in the engine screen. Always carries the current text and speed. */
public record EngineActionPayload(Action action, String text, int speed) implements CustomPacketPayload {
	public enum Action {
		SAVE, GO, STOP, RESTORE
	}

	public static final Type<EngineActionPayload> TYPE = new Type<>(Billboard.id("engine_action"));

	public static final StreamCodec<ByteBuf, EngineActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), EngineActionPayload::action,
			ByteBufCodecs.stringUtf8(BillboardEngineBlockEntity.MAX_TEXT_LENGTH * 4), EngineActionPayload::text,
			ByteBufCodecs.VAR_INT, EngineActionPayload::speed,
			EngineActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
