package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.BlockSelector;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 정해진 블록의 채굴 속도에 배율을 건다.
 *
 * <p>예: {@code { "type": "mining_speed", "multiplier": 0.7,
 * "blocks": ["#minecraft:base_stone_overworld", "#minecraft:dirt"] }} 는 돌과 흙만 30% 느리게
 * 만든다. 실버 7 광맥 감각의 대가 쪽이 이 타입을 쓴다.
 *
 * <p>이 효과는 팀원에게 붙였다 떼는 것이 아니므로 {@link #apply}/{@link #remove} 는 아무 일도
 * 하지 않는다. 실제로 속도를 깎는 자리는
 * {@link com.sharedfate.perk.PerkBlockBreaks#scaleDestroySpeed} 이고, 거기까지 이어 주는 것은
 * {@code PlayerMiningSpeedMixin} 이다. {@code mob_health} 와
 * {@link com.sharedfate.perk.MobPerkModifiers} 의 관계와 같은 구도다.
 */
public final class MiningSpeedEffect implements PerkEffect {
	/**
	 * 배율 하한. 0 을 허용하면 그 블록을 영영 캘 수 없게 되므로 막는다. 0.01 이면 100배 느린
	 * 것이라 사실상 가장 가혹한 값이다.
	 */
	static final double MIN_MULTIPLIER = 0.01;
	/** 배율 상한. 빨라지는 쪽으로도 쓸 수 있게 열어 두되 터무니없는 값은 막는다. */
	static final double MAX_MULTIPLIER = 64.0;

	private final double multiplier;
	private final BlockSelector blocks;

	public MiningSpeedEffect(double multiplier, BlockSelector blocks) {
		this.multiplier = multiplier;
		this.blocks = blocks == null ? BlockSelector.ALL : blocks;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: mining_speed 의 multiplier 값이 없거나 범위를 벗어났습니다 ({})",
					perkId, multiplier);
			return null;
		}

		BlockSelector blocks = BlockSelector.fromJson(perkId, "mining_speed", json);
		if (blocks == null) {
			return null;
		}
		return new MiningSpeedEffect(multiplier, blocks);
	}

	/** 이 블록에 걸리는 효과인가. */
	public boolean appliesTo(BlockState state) {
		return blocks.matches(state);
	}

	/** 안전한 범위로 자른 배율. */
	public double multiplierFor() {
		if (!Double.isFinite(multiplier)) {
			return 1.0;
		}
		return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, multiplier));
	}

	public double multiplier() {
		return multiplier;
	}

	public BlockSelector blocks() {
		return blocks;
	}
}
