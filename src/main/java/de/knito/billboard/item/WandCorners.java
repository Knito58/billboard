package de.knito.billboard.item;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The two corners a selector wand has marked. Either may be missing. */
public record WandCorners(Optional<GlobalPos> first, Optional<GlobalPos> second) {
	public static final WandCorners EMPTY = new WandCorners(Optional.empty(), Optional.empty());

	public static final Codec<WandCorners> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.optionalFieldOf("first").forGetter(WandCorners::first),
			GlobalPos.CODEC.optionalFieldOf("second").forGetter(WandCorners::second)
	).apply(i, WandCorners::new));

	public static final StreamCodec<ByteBuf, WandCorners> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), WandCorners::first,
			ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), WandCorners::second,
			WandCorners::new
	);

	public boolean isComplete() {
		return first.isPresent() && second.isPresent();
	}
}
