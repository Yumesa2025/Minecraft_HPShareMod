package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 골드 「방패벽」. 팀이 한곳에 뭉쳐 있는 동안 몹이 쏜 투사체가 팀 근처에서 지워진다.
 *
 * <pre>{@code
 * { "type": "projectile_ward", "distance": 10, "radius": 8 }
 * }</pre>
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code distance} — 팀 전원이 이 거리 안에 뭉쳐 있어야 벽이 선다. 안 적으면
 *       {@value #DEFAULT_DISTANCE} 다. {@value #MIN_DISTANCE}~{@value #MAX_DISTANCE} 를 벗어나면
 *       경고만 남기고 그 범위로 자른다.</li>
 *   <li>{@code radius} — 팀원에게서 이만큼 안으로 들어온 투사체를 지운다. 안 적으면
 *       {@value #DEFAULT_RADIUS} 다. {@value #MIN_RADIUS}~{@value #MAX_RADIUS} 를 벗어나면 역시
 *       잘라 쓴다.</li>
 * </ul>
 *
 * <p>두 값 모두 한 낱말이라 카멜케이스로 갈릴 일이 없다. 다중 단어 칸이 생기면 다른 효과들처럼
 * 카멜케이스도 함께 읽어 주어야 한다.
 *
 * <h2>거리 둘이 서로 다른 일을 한다</h2>
 * <p>{@code distance} 는 <b>켜지는 조건</b>이고 {@code radius} 는 <b>지우는 범위</b>다. 헷갈리기
 * 쉬워 이름을 갈라 두었다. 조건이 넓고 범위가 좁은 것이 이 증강의 모양이다 — 10칸 안에 모여
 * 있으면 각자 몸 주위 8칸이 지워진다. 반대로 두면 뭉치지 않아도 켜지거나, 뭉쳐도 아무것도
 * 못 막는다.
 *
 * <p>두 값 모두 <b>정의에 적힌 그대로</b> 넘겨야 한다. 세트 「결속 3」의 배율(×1.5)은
 * {@link com.sharedfate.sync.TeamProximity#together} 와
 * {@link com.sharedfate.sync.TeamProximity#scaled} 가 먹이므로 여기서 미리 곱하면 두 번 곱해진다.
 *
 * <h2>범위를 벗어나도 정의를 버리지 않는다</h2>
 * <p>{@link AuraDamageEffect#fromJson}·{@link RallyShardEffect#fromJson} 과 같은 정책이다. 숫자
 * 하나가 틀렸다고 골드 증강이 통째로 사라지는 쪽이 손해가 크다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 「얼마나 뭉쳐야 하고 얼마나 넓게 지우는가」만 들고 있는 자료 그릇이다. 팀이 지금
 * 뭉쳐 있는지 묻고, 투사체를 찾고, 쏜 주체가 몹인지 가리고, 실제로 지우는 일은 전부
 * {@link com.sharedfate.sync.ProjectileWardManager} 가 맡는다.
 */
public final class ProjectileWardEffect implements PerkEffect {
	/** 벽이 서기 위해 팀이 뭉쳐 있어야 하는 기본 거리(칸). */
	public static final int DEFAULT_DISTANCE = 10;
	/** 뭉침 거리 하한. {@link ProximityEffect#MIN_DISTANCE} 와 같은 선이다. */
	public static final int MIN_DISTANCE = 4;
	/**
	 * 뭉침 거리 상한.
	 *
	 * <p>이보다 넓으면 서로 보이지도 않는 거리에서 「뭉쳐 있다」가 되어 조건이 뜻을 잃는다.
	 * 조건 없이 늘 켜지는 벽을 원한다면 그것은 다른 증강이지 방패벽이 아니다.
	 */
	public static final int MAX_DISTANCE = 64;

	/** 투사체를 지우는 기본 반경(칸). */
	public static final int DEFAULT_RADIUS = 8;
	/** 반경 하한. 이보다 좁으면 이미 맞은 뒤에나 닿아서 있으나 마나다. */
	public static final int MIN_RADIUS = 2;
	/**
	 * 반경 상한.
	 *
	 * <p>{@link AuraDamageEffect#MAX_RADIUS} 와 같은 값이고 같은 이유다. 반경이 넓어지면 훑어야
	 * 하는 구역이 세제곱으로 늘어난다. 이쪽은 살기보다 자주 도는 조회라 더 넘길 수 없다.
	 */
	public static final int MAX_RADIUS = 32;

	private final int distance;
	private final int radius;

	public ProjectileWardEffect(int distance, int radius) {
		this.distance = distance;
		this.radius = radius;
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>읽을 값이 둘뿐이고 둘 다 잘라 쓰므로 실패로 끝나는 길이 없다. 언제나 쓸 수 있는 효과가
	 * 나온다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int distance = clamp(perkId, "distance",
				PerkEffectType.readInt(json, "distance", DEFAULT_DISTANCE),
				MIN_DISTANCE, MAX_DISTANCE);
		int radius = clamp(perkId, "radius",
				PerkEffectType.readInt(json, "radius", DEFAULT_RADIUS),
				MIN_RADIUS, MAX_RADIUS);
		return new ProjectileWardEffect(distance, radius);
	}

	private static int clamp(String perkId, String key, int value, int min, int max) {
		if (value >= min && value <= max) {
			return value;
		}
		int cut = Math.max(min, Math.min(max, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: projectile_ward 의 {} 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, key, min, max, cut, value);
		return cut;
	}

	/**
	 * 벽이 서기 위해 팀 전원이 들어와 있어야 하는 거리(칸).
	 *
	 * <p>{@link com.sharedfate.sync.TeamProximity#together} 에 <b>이 값을 그대로</b> 넘긴다.
	 * 배율은 그쪽이 먹인다.
	 */
	public int distance() {
		return distance;
	}

	/** 팀원에게서 이만큼 안으로 들어온 투사체를 지운다(칸). */
	public int radius() {
		return radius;
	}

	/** 반경의 제곱. 거리 비교에 제곱근을 뽑지 않으려고 미리 내어 둔다. */
	public double radiusSquared() {
		return (double) radius * radius;
	}

	/**
	 * 효과 목록에서 「방패벽」만 골라낸다. 없으면 빈 목록.
	 *
	 * <p>정의마다 뭉침 거리가 다를 수 있어 <b>하나로 합치지 않고 그대로 돌려준다.</b> 어느 정의는
	 * 켜지고 어느 정의는 안 켜지는 상황이 실제로 생기므로, 켜짐 판정은 정의마다 따로 해야 한다.
	 *
	 * <p>레지스트리도 팀도 보지 않는 순수 계산이라 살아 있는 서버 없이 시험할 수 있다.
	 */
	public static List<ProjectileWardEffect> wardsOf(@Nullable Iterable<PerkEffect> effects) {
		if (effects == null) {
			return List.of();
		}
		List<ProjectileWardEffect> found = new ArrayList<>();
		for (PerkEffect effect : effects) {
			if (effect instanceof ProjectileWardEffect ward) {
				found.add(ward);
			}
		}
		return found;
	}

	/**
	 * 효과 목록에서 반경이 가장 넓은 「방패벽」을 고른다. 없으면 {@code null}.
	 *
	 * <p>여럿을 가졌으면 <b>넓은 쪽이 이긴다.</b> 좁은 쪽이 이기면 증강을 하나 더 얻은 것이 앞의
	 * 것을 깎아 먹는 꼴이 된다 — {@link DamageWardEffect#shortestOf} 가 짧은 쪽을 고르는 것과
	 * 같은 판단이고, 방향만 반대다.
	 *
	 * <p>뭉침 조건을 이미 만족한 정의들 가운데서 고르는 것이 뜻이 있으므로, 부르는 쪽이
	 * 걸러 낸 목록을 넘긴다.
	 */
	public static @Nullable ProjectileWardEffect widestOf(@Nullable Iterable<PerkEffect> effects) {
		ProjectileWardEffect widest = null;
		for (ProjectileWardEffect ward : wardsOf(effects)) {
			if (widest == null || ward.radius > widest.radius) {
				widest = ward;
			}
		}
		return widest;
	}
}
