package com.sharedfate;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

public final class CodecRoundTrip {
	private CodecRoundTrip() {
	}

	public static <T> T through(Codec<T> codec, T value) {
		Tag encoded = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
		return codec.parse(NbtOps.INSTANCE, encoded).getOrThrow();
	}
}
