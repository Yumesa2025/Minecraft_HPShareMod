package com.sharedfate.perk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 팀이 보유한 증강 하나와 그 중첩 수.
 *
 * @param perkId 증강 식별자
 * @param count  중첩 수. 최소 1
 */
public record PerkStack(String perkId, int count) {
	public static final Codec<PerkStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("perkId").forGetter(PerkStack::perkId),
			Codec.INT.optionalFieldOf("count", 1).forGetter(PerkStack::count)
	).apply(instance, PerkStack::new));

	public PerkStack {
		count = Math.max(1, count);
	}

	public PerkStack plusOne() {
		return new PerkStack(perkId, count + 1);
	}
}
