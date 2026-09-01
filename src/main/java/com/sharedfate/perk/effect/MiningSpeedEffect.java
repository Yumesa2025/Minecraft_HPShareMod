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
 * <h2>느리게 하는 데만 쓸 수 있다</h2>
 * <p>이 타입은 <b>서버에서만</b> 계산된다. 그런데 26.2 에서 블록이 부서지는 시점을 정하는 것은
 * <b>클라이언트가 보내는 {@code STOP_DESTROY_BLOCK}</b> 이다 — {@code ServerPlayerGameMode.tick}
 * 은 진행도를 세어 금 가는 모습만 보내고 그 결과를 버린다. 그래서
 *
 * <ul>
 *   <li><b>1 미만(느려짐)은 작동한다.</b> 클라이언트가 먼저 끝냈다고 알려도 서버가
 *       {@code hasDelayedDestroy} 로 붙잡아 자기 진행도가 찰 때까지 미룬다</li>
 *   <li><b>1 초과(빨라짐)는 아무 일도 하지 않는다.</b> 서버가 아무리 빨라도 클라이언트가
 *       말해 주기 전에는 부수지 않는다. 유일한 예외는 한 틱 만에 끝나는 즉시 파괴다</li>
 * </ul>
 *
 * <p>빠르게 하려면 <b>{@code minecraft:block_break_speed} 속성</b>을 써야 한다. 그 속성은
 * {@code setSyncable(true)} 라 클라이언트까지 자동으로 내려가 양쪽이 같은 값을 본다. 실제로
 * 골드 「굴착기」가 여기에 ×3 을 적어 두고 <b>이득 없이 광석 페널티만</b> 물다가 2026-09-02 에
 * 속성 방식으로 옮겨졌다. 정의를 읽을 때 1보다 크면 경고를 남긴다.
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

		if (multiplier > 1.0) {
			// 정의는 살려 두되 반드시 알린다. 이 타입은 느리게 하는 데만 쓸 수 있다 —
			// 위 문단과 PlayerMiningSpeedMixin 을 보라. 실제로 「굴착기」가 ×3 을 적어 두고
			// 아무 일도 하지 않은 채 광석 페널티만 물었다.
			SharedFateMod.LOGGER.warn(
					"증강 {}: mining_speed 의 multiplier 가 1 보다 큽니다 ({}). 이 타입은 서버에서만 "
							+ "계산되고 채굴 완료는 클라이언트가 정하므로 빨라지는 쪽은 효과가 없습니다. "
							+ "minecraft:block_break_speed 속성으로 적으십시오.",
					perkId, multiplier);
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
