package com.sharedfate.team;

/**
 * 팀을 만들 때 한 번만 정하는 설정.
 *
 * <p>0.8.0-dev 의 피격·사망 알림이 그랬듯, <b>증강 사용 여부·공유 최대 체력·위치 교환
 * 주기·난이도 상승·증강 다시 뽑기 횟수</b>도 팀을 만드는 순간에만 정한다. 회차가 이미
 * 굴러가는 중에 이것들이 바뀌면 같은 회차의 앞뒤가 다른 규칙으로 흘러간다. 특히 증강은
 * 껐다 켜는 것이 이미 받은 효과를 잠깐 벗어 두는 길이 되어 회차 자체가 뜻을 잃는다.
 * 그래서 바꾸는 길을 아예 두지 않고, 바꾸려 하면 {@link Locked} 의 안내로 돌려보낸다.
 *
 * <p>여기서 정한 값이 <b>회차를 넘어 이어지는</b> 일은
 * {@link TeamManager#restoreFreshRoster} 가 맡는다. 전멸로 월드가 새로 만들어져도 팀이 한 번
 * 내린 결정은 그대로 따라간다.
 *
 * @param perksEnabled                  증강을 쓸 것인가
 * @param damageAlertEnabled            피격 알림을 띄울 것인가
 * @param deathAlertEnabled             사망 알림을 띄울 것인가
 * @param difficultyEscalationEnabled   시간이 흐를수록 적대적 몹이 강해질 것인가
 * @param maxHealth                     팀이 정한 공유 최대 체력. 증강 보너스가 붙기 전의 값이다
 * @param swapIntervalMinutes           위치 교환 주기(분). {@link #SWAP_DISABLED} 면 끔
 * @param rerollCount                   증강 선택창에서 후보를 다시 뽑을 수 있는 <b>회차당</b> 횟수
 */
public record TeamCreationSettings(boolean perksEnabled, boolean damageAlertEnabled,
		boolean deathAlertEnabled, boolean difficultyEscalationEnabled,
		float maxHealth, int swapIntervalMinutes, int rerollCount) {

	/**
	 * 증강은 <b>켠 채로</b> 시작한다.
	 *
	 * <p>이 모드에서 회차를 회차답게 만드는 것이 증강이라, 끄고 시작하는 쪽이 예외다.
	 * {@code /shareteam create} 에 {@code perks} 를 적지 않으면 이 값이 쓰인다.
	 */
	public static final boolean DEFAULT_PERKS_ENABLED = true;

	/**
	 * 난이도 상승은 <b>끈 채로</b> 시작한다.
	 *
	 * <p>회차를 통째로 어렵게 만드는 설정이라, {@code /shareteam create} 에 안 적었을 때
	 * 조용히 켜지는 쪽이 더 나쁘다. 손으로 켜게 둔다.
	 */
	public static final boolean DEFAULT_DIFFICULTY_ESCALATION = false;

	/** 위치 교환 「끔」. 주기 0분은 없으므로 0 을 끔으로 쓴다. */
	public static final int SWAP_DISABLED = 0;

	/** {@code /shareteam create ... health <값>} 이 받는 범위. */
	public static final int MIN_MAX_HEALTH = 20;
	public static final int MAX_MAX_HEALTH = 40;

	/**
	 * 다시 뽑기의 기본 횟수. <b>회차당</b> 세 번이다.
	 *
	 * <p>{@code /shareteam create} 에 {@code reroll} 을 적지 않으면 이 값이 쓰인다.
	 * 한 회차에 증강을 여덟 번 고르므로, 셋이면 「정말 마음에 안 드는 판」만 다시 굴리게 된다.
	 */
	public static final int DEFAULT_REROLL_COUNT = 3;

	/** {@code /shareteam create ... reroll <값>} 이 받는 범위. 0 이면 다시 뽑기를 안 쓰는 팀이다. */
	public static final int MIN_REROLL_COUNT = 0;
	public static final int MAX_REROLL_COUNT = 10;

	/** 손상된 저장값이나 조작된 값을 허용 범위 안으로 접는다. */
	public static int sanitizeRerollCount(int value) {
		return Math.max(MIN_REROLL_COUNT, Math.min(MAX_REROLL_COUNT, value));
	}

	private static final float ABSOLUTE_MIN_HEALTH = 1.0F;
	private static final float ABSOLUTE_MAX_HEALTH = 1024.0F;

	public TeamCreationSettings {
		// 최대 체력은 설정 파일에서도 흘러들어온다. 서버 설정이 이상하다고 팀 만들기 자체가
		// 죽으면 안 되므로 TeamState.sanitize 와 같은 결로 조용히 접는다.
		maxHealth = Float.isFinite(maxHealth)
				? Math.max(ABSOLUTE_MIN_HEALTH, Math.min(ABSOLUTE_MAX_HEALTH, maxHealth))
				: 20.0F;
		// 반면 주기는 명령이 이미 1~120 으로 걸러서 넘긴다. 벗어난 값이 오면 부르는 쪽의
		// 버그이므로 TeamState.enablePositionSwap 과 똑같이 예외로 알린다.
		if (swapIntervalMinutes != SWAP_DISABLED
				&& (swapIntervalMinutes < TeamState.PositionSwapLimits.MIN_MINUTES
						|| swapIntervalMinutes > TeamState.PositionSwapLimits.MAX_MINUTES)) {
			throw new IllegalArgumentException("위치 교환 주기는 1~120분이어야 합니다.");
		}
		// 다시 뽑기 횟수는 최대 체력과 같은 결로 조용히 접는다. 명령이 이미 0~10 으로 거르지만
		// 예전 형식의 팀 명단 파일에서도 흘러들어오는 값이라, 그것 때문에 팀 만들기가 죽으면 안 된다.
		rerollCount = sanitizeRerollCount(rerollCount);
	}

	/** 아무것도 적지 않고 만든 팀의 설정. 최대 체력만 서버 설정에서 온다. */
	public static TeamCreationSettings defaults(float maxHealth) {
		return new TeamCreationSettings(DEFAULT_PERKS_ENABLED, false, false,
				DEFAULT_DIFFICULTY_ESCALATION, maxHealth, SWAP_DISABLED, DEFAULT_REROLL_COUNT);
	}

	public TeamCreationSettings withPerks(boolean enabled) {
		return new TeamCreationSettings(enabled, damageAlertEnabled, deathAlertEnabled,
				difficultyEscalationEnabled, maxHealth, swapIntervalMinutes, rerollCount);
	}

	public TeamCreationSettings withDamageAlert(boolean enabled) {
		return new TeamCreationSettings(perksEnabled, enabled, deathAlertEnabled,
				difficultyEscalationEnabled, maxHealth, swapIntervalMinutes, rerollCount);
	}

	public TeamCreationSettings withDeathAlert(boolean enabled) {
		return new TeamCreationSettings(perksEnabled, damageAlertEnabled, enabled,
				difficultyEscalationEnabled, maxHealth, swapIntervalMinutes, rerollCount);
	}

	public TeamCreationSettings withDifficultyEscalation(boolean enabled) {
		return new TeamCreationSettings(perksEnabled, damageAlertEnabled, deathAlertEnabled,
				enabled, maxHealth, swapIntervalMinutes, rerollCount);
	}

	public TeamCreationSettings withMaxHealth(float value) {
		return new TeamCreationSettings(perksEnabled, damageAlertEnabled, deathAlertEnabled,
				difficultyEscalationEnabled, value, swapIntervalMinutes, rerollCount);
	}

	public TeamCreationSettings withSwapIntervalMinutes(int minutes) {
		return new TeamCreationSettings(perksEnabled, damageAlertEnabled, deathAlertEnabled,
				difficultyEscalationEnabled, maxHealth, minutes, rerollCount);
	}

	public TeamCreationSettings withRerollCount(int count) {
		return new TeamCreationSettings(perksEnabled, damageAlertEnabled, deathAlertEnabled,
				difficultyEscalationEnabled, maxHealth, swapIntervalMinutes, count);
	}

	public boolean swapEnabled() {
		return swapIntervalMinutes != SWAP_DISABLED;
	}

	/**
	 * 갓 만든 팀 상태에 이 설정을 새긴다.
	 *
	 * <p>증강을 아직 하나도 가지지 않은 상태에만 부른다. 그래서 {@code baseMaxHealth} 와
	 * {@code maxHealth} 를 같은 값으로 두어도 되고, 증강 보너스를 얹는
	 * {@code PerkHealthRules} 를 거칠 필요가 없다.
	 *
	 * <p>현재 체력은 <b>줄이기만</b> 한다. 팀을 만들 때 상한을 올렸다고 해서 만든 사람의
	 * 체력이 공짜로 차면 안 된다.
	 */
	public void applyTo(TeamState state) {
		state.perksEnabled = perksEnabled;
		state.damageAlertEnabled = damageAlertEnabled;
		state.deathAlertEnabled = deathAlertEnabled;
		state.difficultyEscalationEnabled = difficultyEscalationEnabled;
		// 난이도가 오른 시간은 「이 회차가 시작된 뒤」다. 갓 만든 팀은 언제나 0 에서 시작한다.
		state.difficultyElapsedTicks = 0;
		state.baseMaxHealth = maxHealth;
		state.maxHealth = maxHealth;
		state.health = Math.max(0.0F, Math.min(maxHealth, state.health));
		// 갓 만든 팀은 이번 회차의 다시 뽑기를 한 번도 쓰지 않았다. 회차가 넘어가 팀 상태를
		// 새로 만들 때도 restoreFreshRoster 가 같은 자리를 가득 채운 값으로 다시 세운다.
		state.rerollAllowance = rerollCount;
		state.rerollsRemaining = rerollCount;
		if (swapEnabled()) {
			state.enablePositionSwap(swapIntervalMinutes);
		} else {
			state.disablePositionSwap();
		}
	}

	/** 팀을 만든 직후 한 줄로 보여 줄 요약. */
	public String summary() {
		return "증강: " + onOff(perksEnabled)
				+ " · 피격 알림: " + onOff(damageAlertEnabled)
				+ " · 사망 알림: " + onOff(deathAlertEnabled)
				+ " · 최대 체력: " + trimZero(maxHealth)
				+ " · 위치 교환: " + (swapEnabled() ? swapIntervalMinutes + "분 주기" : "끔")
				+ " · 난이도 상승: " + onOff(difficultyEscalationEnabled)
				+ " · 다시 뽑기: 회차당 " + rerollCount + "회";
	}

	public static String onOff(boolean value) {
		return value ? "켬" : "끔";
	}

	/** 20.0 처럼 소수점이 의미 없는 값을 "20" 으로 보여 준다. */
	public static String trimZero(float value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/**
	 * 만든 뒤에 바꾸려 할 때 돌려주는 안내.
	 *
	 * <p>문구는 피격·사망 알림의 선례와 같은 결로 맞춘다 — 왜 막혔는지 한 줄, 그래도
	 * 바꾸고 싶으면 무엇을 해야 하는지 한 줄. 명령을 아예 없애 「알 수 없는 명령」이 뜨게
	 * 하지 않는 이유가 이것이다. 예전 판에서 쓰던 명령을 그대로 쳤을 때 왜 안 되는지가
	 * 보여야 한다.
	 */
	public enum Locked {
		PERKS("증강 사용 여부는"),
		MAX_HEALTH("팀 공유 최대 체력은"),
		POSITION_SWAP("위치 교환 설정은"),
		DIFFICULTY("난이도 상승 설정은");

		private final String subject;

		Locked(String subject) {
			this.subject = subject;
		}

		public String message() {
			return subject + " 팀을 만들 때 정한 값이라 바꿀 수 없습니다."
					+ "\n바꾸려면 리더가 /shareteam disband confirm 으로 팀을 해체하고 다시 만드세요."
					+ "\n지금 값은 /shareteam status 로 볼 수 있습니다.";
		}
	}
}
