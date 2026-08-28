package com.sharedfate.perk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 아직 고르지 않은 증강 선택권 하나.
 *
 * <p>후보 {@code optionIds}는 추첨한 그 순간에 확정해 저장한다. 창을 열 때마다 새로 뽑으면
 * 마음에 안 드는 후보가 나왔을 때 재접속으로 다시 굴리는 악용이 가능하기 때문이다.
 *
 * <p>{@code chooser}는 비어 있을 수 있다. 발동 시점에 팀원이 아무도 접속해 있지 않으면
 * 선정을 미루고, 누군가 들어올 때 그때 뽑는다.
 *
 * @param milestone 이 선택권을 만든 레벨 구간 (3, 6, …, 36)
 * @param chooser   고를 사람. 비어 있으면 아직 미정
 * @param optionIds 확정된 후보 증강 식별자들
 */
public record PendingOffer(int milestone, Optional<UUID> chooser, List<String> optionIds) {
	public static final Codec<PendingOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("milestone").forGetter(PendingOffer::milestone),
			UUIDUtil.STRING_CODEC.optionalFieldOf("chooser").forGetter(PendingOffer::chooser),
			Codec.STRING.listOf().fieldOf("optionIds").forGetter(PendingOffer::optionIds)
	).apply(instance, PendingOffer::new));

	public PendingOffer {
		optionIds = List.copyOf(optionIds);
	}

	/** 고를 사람을 바꾼 사본을 돌려준다. 후보는 그대로 유지된다. */
	public PendingOffer withChooser(UUID next) {
		return new PendingOffer(milestone, Optional.ofNullable(next), optionIds);
	}

	public boolean isChooser(UUID candidate) {
		return chooser.isPresent() && chooser.get().equals(candidate);
	}
}
