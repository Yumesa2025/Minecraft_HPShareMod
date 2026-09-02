package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.config.SharedFateConfig;

/**
 * 게임 전체에서 얻는 경험치에 배율을 먹이는 상시 규칙.
 *
 * <p>증강이 아니다. 팀이 무엇을 골랐든, 팀에 속해 있지 않든 똑같이 걸린다. 배율은
 * {@link SharedFateConfig#experienceMultiplier}(기본 1.2배)이고 설정 파일에서 바꿀 수 있다.
 *
 * <h2>왜 「줍는 순간」이 아니라 「떨어지는 순간」인가</h2>
 * <p>경험치 오브는 바닥에 있는 동안 서로 <b>합쳐진다</b>({@code ExperienceOrb.scanForMerges}).
 * 줍는 자리({@code playerTouch})에서 배율을 먹이면 이미 합쳐진 덩어리에 곱하게 되어, 같은 양을
 * 캐도 오브가 몇 개로 뭉쳤느냐에 따라 결과가 달라지고 반올림 오차도 덩어리 수만큼 달라진다.
 * 떨어지는 자리에서 한 번만 곱하면 그런 흔들림이 없다.
 *
 * <h2>어디를 잡는가</h2>
 * <p>26.2 에서 경험치 오브가 월드에 생기는 길은 {@code ExperienceOrb.awardWithDirection(
 * ServerLevel, Vec3, Vec3, int)} 하나로 모인다. {@code ExperienceOrb.award(ServerLevel, Vec3,
 * int)} 는 방향을 {@code Vec3.ZERO} 로 채워 그것을 그대로 부르는 한 줄짜리 메서드고, 몹 처치
 * ({@code LivingEntity}) · 광석({@code Block.popExperience}) · 화로 · 낚시 · 번식 · 주민 거래 ·
 * 경험치병 · 숫돌이 전부 그 둘 중 하나를 지난다. 그래서 {@code awardWithDirection} 의 인자
 * 하나만 고치면 모든 경로가 같은 배율을 받는다. 자세한 확인 근거는
 * {@code ExperienceOrbAwardMixin} 에 적어 뒀다.
 *
 * <h2>반올림</h2>
 * <p>{@link Math#round} 로 가장 가까운 정수에 맞춘다. 1점짜리 오브가 배율 1.2 에서 1점으로
 * 남는 것은 정수 경험치의 어쩔 수 없는 결과다. 대신 <b>원래 양이 1 이상이었으면 결과도 반드시
 * 1 이상</b>이 되게 막는다. 배율을 낮게 잡은 서버에서 작은 오브가 통째로 사라져 「경험치가 아예
 * 안 나온다」로 보이는 것을 막기 위해서다.
 */
public final class ExperienceBonus {
	private ExperienceBonus() {
	}

	/** 지금 걸려 있는 배율. 설정을 아직 읽지 않았으면(시험·초기화 전) 1.0 이라 아무 일도 없다. */
	public static double multiplier() {
		SharedFateConfig config = SharedFateMod.config;
		if (config == null) {
			return 1.0;
		}
		double multiplier = config.experienceMultiplier;
		return Double.isFinite(multiplier) && multiplier > 0.0 ? multiplier : 1.0;
	}

	/** 설정에 적힌 배율로 경험치 양을 조정한다. */
	public static int scale(int amount) {
		return scale(amount, multiplier());
	}

	/**
	 * 경험치 양에 배율을 먹인다. 설정을 읽지 않는 순수 계산이라 서버 없이 시험할 수 있다.
	 *
	 * @param amount     바닐라가 주려던 양
	 * @param multiplier 곱할 배율
	 * @return 조정된 양. 0 이하는 그대로 두고, 1 이상은 반올림하되 최소 1 을 남긴다
	 */
	public static int scale(int amount, double multiplier) {
		if (amount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0 || multiplier == 1.0) {
			return amount;
		}
		long scaled = Math.round(amount * multiplier);
		if (scaled < 1L) {
			return 1;
		}
		return (int) Math.min(scaled, Integer.MAX_VALUE);
	}
}
