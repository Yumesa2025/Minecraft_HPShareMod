package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.effect.MobActionSpeedEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 「이 몹은 이번 틱을 건너뛰는가, 아니면 한 번 더 도는가」를 한 자리에서 답한다.
 *
 * <p>몹의 시간을 늦추거나 당기는 증강은 지금 둘이다.
 *
 * <ul>
 *   <li>프리즘 「성역」({@code sanctuary}) — 뭉쳐 있으면 반경 안의 몹을 늦추고, 흩어져 있으면
 *       전역으로 당긴다. 판정은 {@link SanctuaryManager} 가 들고 있다.</li>
 *   <li>{@code mob_action_speed} — 조건도 반경도 없는 상시 배율. 몹 종류별 값은
 *       {@link MobPerkModifiers#actionSpeedMultiplier} 가 미리 계산해 둔다.</li>
 * </ul>
 *
 * <p>둘을 <b>각자 mixin 으로 만들면 안 된다.</b> 가속은 같은 메서드를 다시 부르는 방식이라,
 * 주입이 둘이면 서로의 재진입 깃발을 보지 못해 한 틱이 셋·넷으로 불어난다. 그래서 주입은
 * {@code ServerLevelSanctuaryTickMixin} 하나로 두고, <b>누가 무엇을 요구하든 답은 여기서 하나로
 * 합쳐</b> 낸다. 한 틱에 더 도는 횟수는 언제나 많아야 한 번이다.
 *
 * <h2>왜 「틱을 더 주는」 방식인가</h2>
 * <p>「몹이 20% 빨라진다」는 이동 속도만이 아니다. 때리는 주기·크리퍼 부풀기·활 쏘는 간격이
 * 전부 함께 빨라져야 한다. 그 넷은 바닐라에서 서로 다른 곳에 흩어져 있고, 따로 건드리면 손댈
 * 자리가 넷이며 새 몹이 생길 때마다 늘어난다.
 *
 * <p>대신 <b>몹에게 흐르는 시간 자체를 당긴다.</b> 몹의 행동은 거의 전부 자기 틱 안에서 숫자를
 * 하나씩 세는 방식이라, 틱을 더 주면 넷이 한 자리에서 같은 비율로 빨라진다. 느리게 하는 쪽
 * ({@link SanctuaryManager})이 이미 같은 이유로 틱을 건너뛰고 있으니, 이것은 그 거울이다.
 *
 * <p>자리를 {@code ServerLevel.tickNonPassenger} 로 고른 근거는 {@code ServerLevelSanctuaryTickMixin}
 * 주석에 적혀 있다. 요약하면 <b>크리퍼가 부풀기를 {@code super.tick()} 보다 먼저 끝내기</b>
 * 때문에 {@code Mob.tick()} 위가 아니면 크리퍼만 조용히 빠진다.
 *
 * <h2>확률이 아니라 정해진 간격으로 준다</h2>
 * <p>성역은 매 틱 난수를 뽑아 「40% 확률로 건너뛴다」로 센다. 그쪽은 팀이 움직일 때마다 대상
 * 몹이 바뀌는 조건부 효과라 그것으로 충분하다.
 *
 * <p>{@code mob_action_speed} 는 다르다. 값이 늘 같은 상시 효과이고, 증강 카드에 「20%」라고
 * 적힌다. 그래서 <b>평균 20%</b> 가 아니라 <b>정확히 20%</b> 여야 한다. 난수로 세면 운 나쁜
 * 크리퍼가 연달아 다섯 번 틱을 더 받아 순간 두 배로 빨라지는 구간이 생기는데, 터지는 시각을
 * 눈으로 재는 몹에게 그 들쭉날쭉함은 그대로 억울함이 된다.
 *
 * <p>그래서 {@link #crossesThisTick} 로 <b>몫을 쌓아 넘칠 때만</b> 한 번 준다. 0.2 면 다섯 틱에
 * 한 번, 0.15 면 스무 틱에 세 번 — 어떤 값이든 오차 없이 그 비율이고, 같은 몹이 두 틱 연속
 * 더 도는 일이 없다. 몹마다 저장할 것이 하나도 없다는 점도 중요하다. 매 틱 모든 엔티티가
 * 지나는 자리에 맵을 하나 놓을 수는 없다.
 *
 * <h2>부작용 — 알고 그대로 두는 것들</h2>
 * <p>「틱을 더 준다」는 그 몹의 시간이 통째로 빨리 간다는 뜻이다. 아래는 그래서 함께 따라오는
 * 것들이고, 전부 「시간이 빨리 간다」로 읽으면 앞뒤가 맞는다.
 *
 * <ul>
 *   <li><b>중력은 두 배로 먹지 않는다.</b> 틱 하나에 더해지는 낙하 가속도는 그대로고 틱 수만
 *       는다. 몹이 그리는 포물선은 세계 좌표에서 <b>완전히 같고</b> 그 위를 20% 빨리 지날
 *       뿐이다. 낙하 피해는 틱 수가 아니라 떨어진 <b>거리</b>로 매기므로 역시 그대로다.</li>
 *   <li><b>이동 보간은 튀지 않는다.</b> {@code tickNonPassenger} 는 첫 줄에서
 *       {@code setOldPosAndRot()} 를 부르므로, 한 번 더 도는 틱도 「이전 위치」를 제대로 다시
 *       잡는다. 클라이언트에는 그 틱의 이동량이 조금 커진 것으로 전해질 뿐 순간이동이 아니다.
 *       다섯 틱에 한 번 걸음이 커지는데, 좀비 기준 한 걸음이 0.1칸 남짓이라 눈에 띄지 않는다.
 *       몹마다 {@code getId()} 만큼 위상을 어긋뜨려 두어 무리가 동시에 같은 틱에 큰 걸음을
 *       내딛지도 않는다 — 서버 부담이 5틱마다 한 번에 몰리지 않는 이유이기도 하다.</li>
 *   <li><b>{@code tickCount} 도 함께 는다.</b> 늘리는 줄이 {@code Entity.tick()} 을 부르기
 *       전에 있다. {@code tickCount % N} 으로 도는 주기 행동까지 같은 비율로 빨라진다.</li>
 *   <li><b>피격 무적 시간도 빨리 준다.</b> {@code LivingEntity.baseTick} 의
 *       {@code invulnerableTime} 감소가 함께 빨라지므로, 빨라진 몹은 다시 때릴 수 있게 되는
 *       시점이 조금 앞당겨진다. 플레이어에게 아주 작은 이득이다. <b>알면서 그대로 둔다</b> —
 *       여기만 되돌리면 「몹의 시간이 빨리 간다」는 한 줄짜리 규칙이 깨지고, 몹이 20% 더
 *       때리고 20% 빨리 다가오는 손해가 이 이득보다 훨씬 크다.</li>
 *   <li><b>불·독·질식·디스폰도 같은 비율로 빨라진다.</b> 햇빛에 타는 몹은 20% 빨리 재가 되고
 *       디스폰도 20% 빨리 온다. 전부 더 도는 틱 안에 있어 서로 어긋나지 않는다.</li>
 *   <li><b>서버 부담이 그만큼 는다.</b> 적대 몹의 틱 비용이 20% 늘어난다. 성역이 같은 자리에서
 *       이미 치르는 비용이고, 대상이 적대 몹으로 좁아 월드 전체가 아니다.</li>
 *   <li><b>탈것에 탄 것은 함께 돈다.</b> {@code tickNonPassenger} 가 태우고 있는 것도 돌리므로
 *       거미에 탄 스켈레톤은 거미와 같이 빨라진다. 반대로 보트·광산 수레에 탄 몹은 탈것이
 *       대상이 아니라 빨라지지 않는다. 드문 경우라 그대로 둔다.</li>
 * </ul>
 *
 * <h2>겹칠 때</h2>
 * <ul>
 *   <li><b>성역 안에서는 어떤 증강으로도 빨라지지 않는다.</b> 「지켜 주는 자리」가 조건부가
 *       되면 안 된다. {@link SanctuaryManager} 가 자기 가속에 이미 거는 규칙을 이쪽에도 그대로
 *       건다.</li>
 *   <li><b>팀이 여럿이어도 배가 되지 않는다.</b> {@link MobPerkModifiers} 가 팀 사이에서는
 *       1.0 에서 가장 먼 배율 <b>하나</b>만 고른다. 팀원이 넷이든 여덟이든 같은 값이다.</li>
 *   <li>한 팀이 「성역」과 {@code mob_action_speed} 를 <b>함께</b> 가지고 흩어져 있으면 둘 다
 *       걸린다. 한 팀 안에서 여러 증강의 효과가 겹치는 것은 이 모드가 원래 허용하는 쪽이고
 *       ({@code MobPerkModifiers.teamMultiplier} 도 한 팀 안에서는 곱한다), 그래도 한 틱에 더
 *       도는 횟수는 <b>많아야 한 번</b>이라 두 배를 넘지 않는다.</li>
 * </ul>
 */
public final class MobTickRate {

	private static boolean warned;

	private MobTickRate() {
	}

	/**
	 * 이 엔티티가 이번 틱을 통째로 건너뛰는가. <b>모든 엔티티가 매 틱</b> 지나는 물음이다.
	 *
	 * <p>두 물음 모두 첫 줄이 {@code volatile} 한 번 읽기로 끝난다. 해당 증강을 가진 팀이 없는
	 * 서버에서는 그 한 줄이 비용의 전부다.
	 */
	public static boolean shouldSkipTick(@Nullable ServerLevel level, @Nullable Entity entity) {
		if (SanctuaryManager.shouldSkipTick(level, entity)) {
			return true;
		}
		try {
			double skip = MobActionSpeedEffect.skipRate(
					MobPerkModifiers.actionSpeedMultiplier(entity));
			return skip > 0.0 && crossesThisTick(phaseOf(level, entity), skip);
		} catch (RuntimeException error) {
			warnOnce(error);
			return false;
		}
	}

	/**
	 * 이 엔티티가 이번 틱을 한 번 더 도는가.
	 *
	 * <p>{@code true} 를 돌려줘도 실제로 더 도는 것은 한 번뿐이다. 부르는 쪽
	 * ({@code ServerLevelSanctuaryTickMixin})이 깃발로 재진입을 막는다.
	 */
	public static boolean shouldRunExtraTick(@Nullable ServerLevel level, @Nullable Entity entity) {
		try {
			double extra = MobActionSpeedEffect.extraTickRate(
					MobPerkModifiers.actionSpeedMultiplier(entity));
			// 성역 안의 몹은 빨라지지 않는다. 느려지는 쪽이 언제나 이긴다.
			if (extra > 0.0 && SanctuaryManager.slowFor(level, entity) <= 0.0
					&& crossesThisTick(phaseOf(level, entity), extra)) {
				return true;
			}
		} catch (RuntimeException error) {
			warnOnce(error);
		}
		return SanctuaryManager.shouldRunExtraTick(level, entity);
	}

	// ------------------------------------------------------------------ 몫 쌓기

	/**
	 * 이번 틱에 몫이 넘쳤는가. <b>이 한 줄이 「20%」의 뜻이다.</b>
	 *
	 * <p>틱마다 {@code rate} 만큼을 쌓아 정수를 넘길 때 한 번 준다. 쌓아 둔 값을 어디에도
	 * 저장하지 않는 것이 요점이다 — 지금까지 쌓인 양은 언제나 {@code phase * rate} 이므로,
	 * <b>이번 틱과 지난 틱의 정수 부분이 달라졌는지</b>만 보면 된다.
	 *
	 * <p>그래서 몹마다 기억할 것이 없다. 매 틱 모든 엔티티가 지나는 자리라 맵 하나도 놓을 수
	 * 없는데, 그러면서도 비율은 난수와 달리 <b>정확히</b> 맞는다.
	 *
	 * <ul>
	 *   <li>{@code rate = 0.2} → 다섯 틱에 한 번. 연달아 두 번은 없다.</li>
	 *   <li>{@code rate = 0.4} → 다섯 틱에 두 번. 고르게 흩어진다.</li>
	 *   <li>{@code rate >= 1.0} → 매 틱. 가속이면 곧 ×2.0 이다.</li>
	 * </ul>
	 *
	 * @param phase 이 엔티티의 위상. 서버 틱 수에 엔티티 번호를 더한 값이다
	 * @param rate  0 이상 1 이하의 비율
	 */
	static boolean crossesThisTick(long phase, double rate) {
		if (!(rate > 0.0)) {
			return false;
		}
		if (rate >= 1.0) {
			return true;
		}
		return Math.floor(phase * rate) > Math.floor((phase - 1) * rate);
	}

	/**
	 * 이 엔티티의 위상.
	 *
	 * <p>서버 틱 수에 <b>엔티티 번호를 더한다.</b> 그래야 같은 배율을 받는 몹들이 서로 다른
	 * 틱에 한 번씩 더 돌아, 무리 전체가 5틱마다 한꺼번에 두 번 도는 일이 없다. 화면에서도
	 * 서버 부담에서도 고르게 퍼진다.
	 *
	 * <p>{@code getTickCount()} 는 {@code int} 라 약 3.4년 연속 가동 뒤 한 바퀴 돈다.
	 * 부호 없는 값으로 늘려 받으므로 그때도 위상이 음수로 튀지 않는다.
	 */
	private static long phaseOf(@Nullable ServerLevel level, @Nullable Entity entity) {
		if (level == null || entity == null) {
			return 0L;
		}
		MinecraftServer server = level.getServer();
		long tick = server == null ? 0L : Integer.toUnsignedLong(server.getTickCount());
		return tick + entity.getId();
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"몹 행동 속도 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. */
	static void resetForTesting() {
		warned = false;
	}
}
