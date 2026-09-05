package com.sharedfate.perk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.NoDefenseDrawbacksEffect;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 「이 효과는 대가다」라는 표시를 모아 두는 표.
 *
 * <p>증강 정의의 효과 하나에 {@code "drawback": true} 를 적으면 그 효과가 여기 등록된다.
 *
 * <pre>{@code
 * { "type": "attribute", "attribute": "minecraft:knockback_resistance",
 *   "operation": "add_value", "amount": -1.0, "drawback": true }
 * }</pre>
 *
 * <p>표시가 붙는다고 해서 <b>그 자체로는 아무 일도 일어나지 않는다.</b> 이 표를 보고 효과를
 * 건너뛰는 곳은 세트 「방어 3단계」가 켜진 팀뿐이고, 그 판정은
 * {@link NoDefenseDrawbacksEffect#heldBy} 가 한다. 표시를 안 적은 효과는 이 표에 없으므로
 * 조회가 곧바로 거짓이 되어 예전과 한 톨도 다르지 않게 동작한다.
 *
 * <h2>왜 효과 클래스에 필드를 더하지 않았는가</h2>
 * <p>대가는 {@code attribute}·{@code damage_taken_from}·{@code conditional} 처럼 <b>형이 서로 다른
 * 효과 어디에나</b> 붙을 수 있다. {@link PerkEffect} 는 인터페이스이고 구현이 일흔 가까이 되므로,
 * 「대가인가」를 필드로 표현하려면 그 전부를 고치거나 인터페이스에 기본 메서드를 더해야 한다.
 * 앞엣것은 손이 너무 많이 가고, 뒤엣것은 <b>구현마다 그 값을 실어 나르는 생성자·팩토리를 전부
 * 고쳐야</b> 하는 것은 똑같다.
 *
 * <h2>왜 감싸는 방식이 아니라 옆에 두는 표인가</h2>
 * <p>이 저장소에서 효과를 소비하는 자리는 예외 없이 {@code instanceof} 로 형을 가린다
 * ({@code effect instanceof DamageTakenFromEffect from}). 대가를 {@code DrawbackEffect} 로 감싸면
 * 그 {@code instanceof} 가 전부 빗나가므로 <b>세트가 꺼져 있을 때조차</b> 대가가 사라진다 —
 * 감싸는 방식을 고르는 순간 「표시가 없으면 지금과 똑같이」를 지키기 위해 효과를 훑는 자리를
 * 하나도 빠짐없이 고쳐야 하고, 한 곳이라도 빠뜨리면 빌드도 통과하고 로그도 없이 증강이 조용히
 * 반쪽이 된다.
 *
 * <p>옆에 두는 표는 그런 사고가 날 수 없다. 효과 객체는 그대로이므로 표를 모르는 자리는 예전과
 * 완전히 같은 길을 지나고, 표를 보는 자리만 골라서 건너뛴다. 덤으로 <b>효과 객체가 스스로는 알
 * 수 없는 「나를 가진 증강이 누구인가」를 함께 적어 둘 수 있다</b> — 「방어 유형 증강의 대가만
 * 사라진다」를 지키려면 그 정보가 반드시 필요하고, {@code on_team_hurt} 의 하위 효과처럼 소비하는
 * 자리에 부모 증강이 넘어오지 않는 경로도 있다.
 *
 * <h2>표시를 붙일 수 있는 자리</h2>
 * <p>아무 데나 적어도 되는 것은 아니다. 표시를 <b>읽어서 실제로 건너뛰는 자리가 있는 곳</b>에만
 * 붙일 수 있다. 지금은 두 곳이다.
 *
 * <ul>
 *   <li>증강의 <b>최상위</b> 효과 — {@link PerkRegistry} 가 읽는다</li>
 *   <li>{@code on_team_hurt}·{@code on_critical} 의 <b>하위</b> 효과 —
 *       {@link TemporaryPerkGrants} 가 읽고 {@code grant} 가 건너뛴다</li>
 * </ul>
 *
 * <p>{@code conditional} 의 {@code when_true}/{@code when_false} 안에는 붙일 수 없다. 조건부는
 * 붙였다 떼는 일을 스스로 하고 주기 재평가까지 받으므로, 하위 하나만 골라 빼려면 그 두 길을 모두
 * 갈라야 한다. 대신 <b>조건부 효과 전체</b>에 붙인다 — 대가로 쓰이는 조건부는 어차피 한쪽 묶음만
 * 가진 「대가 그 자체」라 통째로 빼는 것과 뜻이 같다. 실수로 안에 적으면
 * {@code ConditionalEffect} 가 경고를 남기고 무시하므로, 조용히 아무 일도 안 하는 사고는 없다.
 *
 * <h2>필드 없는 홑개체 효과에는 붙이지 말 것</h2>
 * <p>표의 열쇠는 <b>객체의 신원</b>이다. {@code no_sleep} 처럼 읽을 필드가 없어 하나를 만들어
 * 돌려쓰는 효과({@code INSTANCE})에 표시를 붙이면, 그 형을 쓰는 다른 증강까지 함께 대가로
 * 보이게 된다. 그런 일이 생기면 {@link #mark} 가 경고를 남긴다.
 */
public final class PerkDrawbacks {
	/** JSON 에 적는 표시 이름. */
	public static final String FIELD = "drawback";

	/**
	 * 대가로 표시된 효과 → 그 효과를 가진 증강의 id.
	 *
	 * <p>신원 비교라 {@code equals} 를 재정의한 효과(레코드 등)끼리도 섞이지 않는다.
	 * 정의를 다시 읽을 때 {@link PerkRegistry} 가 통째로 비운다.
	 */
	private static final Map<PerkEffect, String> OWNERS = new IdentityHashMap<>();

	private PerkDrawbacks() {
	}

	// ------------------------------------------------------------------ 등록

	/**
	 * 효과 정의에 {@code drawback} 이 적혀 있으면 대가로 등록한다.
	 *
	 * <p>효과를 만든 <b>직후</b>에 부른다. 받은 효과를 그대로 돌려주므로 만드는 줄을 감싸듯 쓸 수
	 * 있다. 표시가 없거나 효과가 {@code null} 이면 아무 일도 하지 않는다.
	 *
	 * <p>표시 값이 참·거짓이 아니면 경고를 남기고 <b>표시가 없는 것으로 본다.</b> 정의 실수
	 * 하나로 증강이 통째로 사라지는 것보다, 대가가 그대로 남아 예전처럼 동작하는 편이 안전하다.
	 *
	 * @param perkId 이 효과를 가진 증강의 id
	 * @param json   효과 정의 객체
	 * @param effect 방금 만들어진 효과. {@code null} 이면 그대로 돌려준다
	 * @return 받은 {@code effect} 그대로
	 */
	public static @Nullable PerkEffect mark(@Nullable String perkId, @Nullable JsonObject json,
			@Nullable PerkEffect effect) {
		if (effect == null || json == null || perkId == null) {
			return effect;
		}
		JsonElement raw = json.get(FIELD);
		if (raw == null || raw.isJsonNull()) {
			return effect;
		}
		if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) {
			SharedFateMod.LOGGER.warn("증강 {}: {} 가 참·거짓이 아니라 표시를 무시합니다 ({})",
					perkId, FIELD, raw);
			return effect;
		}
		if (!raw.getAsBoolean()) {
			return effect;
		}
		synchronized (OWNERS) {
			String previous = OWNERS.put(effect, perkId);
			if (previous != null && !previous.equals(perkId)) {
				SharedFateMod.LOGGER.warn(
						"증강 {} 와 {} 가 같은 효과 객체를 대가로 표시했습니다. "
								+ "필드가 없어 하나를 돌려쓰는 효과에는 {} 를 붙이면 안 됩니다.",
						previous, perkId, FIELD);
			}
		}
		return effect;
	}

	/** 표시를 모두 버린다. {@link PerkRegistry} 가 정의를 다시 읽을 때 부른다. */
	public static void clear() {
		synchronized (OWNERS) {
			OWNERS.clear();
		}
	}

	// ------------------------------------------------------------------ 조회

	/** 이 효과가 대가로 표시돼 있는가. */
	public static boolean isDrawback(@Nullable PerkEffect effect) {
		if (effect == null) {
			return false;
		}
		synchronized (OWNERS) {
			return OWNERS.containsKey(effect);
		}
	}

	/** 이 대가를 가진 증강의 id. 표시가 없으면 null. */
	public static @Nullable String ownerOf(@Nullable PerkEffect effect) {
		if (effect == null) {
			return null;
		}
		synchronized (OWNERS) {
			return OWNERS.get(effect);
		}
	}

	/** 목록에 대가가 하나라도 들어 있는가. 없으면 부르는 쪽이 팀 상태를 볼 필요조차 없다. */
	public static boolean anyDrawback(@Nullable Iterable<PerkEffect> effects) {
		if (effects == null) {
			return false;
		}
		for (PerkEffect effect : effects) {
			if (isDrawback(effect)) {
				return true;
			}
		}
		return false;
	}

	/** 표시가 하나도 없는가. 시험이 상태 격리를 확인할 때 쓴다. */
	static boolean isEmpty() {
		synchronized (OWNERS) {
			return OWNERS.isEmpty();
		}
	}

	// ------------------------------------------------------------------ 면제 판정

	/**
	 * 한 번의 조회 동안 「이 팀에서 대가가 면제되는가」를 <b>최대 한 번만</b> 계산해 들고 다니는
	 * 그릇.
	 *
	 * <p>효과를 훑는 자리는 피해 계산처럼 초당 여러 번 도는 곳도 있다. 효과마다 세트 판정을 다시
	 * 하면 그만큼 순회가 늘어나므로, 대가를 <b>실제로 만났을 때</b> 한 번만 판정하고 그 뒤로는
	 * 기억해 둔 값을 쓴다. 대가를 하나도 갖고 있지 않은 팀은 세트를 아예 보지 않는다.
	 *
	 * <p>한 번의 조회 안에서만 쓰고 버린다. 서버 스레드에서만 오간다.
	 */
	public static final class Waiver {
		private final @Nullable TeamState state;
		private @Nullable Boolean active;

		private Waiver(@Nullable TeamState state) {
			this.state = state;
		}

		/**
		 * 지금 이 효과를 건너뛰어야 하는가.
		 *
		 * <p>세 가지가 모두 참일 때만 참이다. (1) 효과가 대가로 표시돼 있고, (2) 그 효과를 가진
		 * 증강이 <b>방어 유형</b>이며, (3) 이 팀에 방어 3단계가 켜져 있다. 하나라도 어긋나면
		 * 거짓이고, 그때 부르는 쪽은 예전과 똑같이 동작해야 한다.
		 *
		 * @param perk   이 효과를 가진 증강. 손에 없으면 {@code null} — 표에서 찾는다
		 * @param effect 지금 보고 있는 효과
		 */
		public boolean waives(@Nullable Perk perk, @Nullable PerkEffect effect) {
			String owner = ownerOf(effect);
			if (owner == null) {
				return false;
			}
			Perk holder = perk != null ? perk : PerkRegistry.byId(owner).orElse(null);
			// 세트 단계가 가진 효과는 증강 id 로 찾을 수 없다. 그때는 면제 대상이 아니다.
			if (holder == null || !holder.hasSetType(PerkSetType.DEFENSE)) {
				return false;
			}
			if (active == null) {
				active = NoDefenseDrawbacksEffect.heldBy(state);
			}
			return active;
		}
	}

	/** 이 팀을 기준으로 한 면제 판정 그릇을 만든다. 판정 자체는 아직 하지 않는다. */
	public static Waiver waiverFor(@Nullable TeamState state) {
		return new Waiver(state);
	}
}
