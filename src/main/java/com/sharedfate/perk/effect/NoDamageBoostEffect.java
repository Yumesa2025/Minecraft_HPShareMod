package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;

/**
 * 이 사람의 근접 공격력({@code minecraft:attack_damage})이 다른 곳에서 오는 증가 효과를
 * 전혀 받지 못하게 한다. 원래 공격력(맨손·무기 자체가 얹는 값)과 공격력을 <b>줄이는</b> 효과는
 * 그대로 적용된다.
 *
 * <p>정의는 {@code { "type": "no_damage_boost" }} 하나뿐이고 필드가 없다. 프리즘 「삽질의 대가」가
 * 쓴다 — 삽은 이 증강 자체가 주는 배율(×3)이라 "정상"으로 치고, 그 밖의 무기·상태이상·다른
 * 증강이 얹으려는 공격력 증가분만 막는다.
 *
 * <h2>무엇을 "증가"로 보는가</h2>
 * <p>{@code minecraft:attack_damage} 속성에 붙은 수정자 중, 다음 셋을 뺀 나머지에서
 * <b>양수인 것만</b> 막는다.
 * <ul>
 *   <li>지금 손에 든 무기 자체가 얹는 수정자({@code ItemStack.forEachModifier}) — 무기를 바꿔
 *       드는 것은 "원래 공격력"이다.</li>
 *   <li>이 증강 자신이 붙이는 {@link com.sharedfate.perk.PerkWeaponDamage#MODIFIER_ID} —
 *       삽의 ×3 배율은 이 증강의 정체성이다.</li>
 *   <li>이 판정 자신이 붙이는 상쇄용 수정자 — 아니면 자기 자신을 다시 상쇄하는 무한 루프가 된다.</li>
 * </ul>
 * <p>음수(공격력을 줄이는 것, 예: 나약함)는 원천에 상관없이 항상 통과시킨다. "공격력 감소만
 * 적용된다"는 약속이 이것이다.
 *
 * <h2>실제 계산</h2>
 * <p>속성 연산 세 종류(더하기·기본값에 곱하기·전체에 곱하기)마다 상쇄용 수정자를 하나씩 붙여
 * 막힌 양수 수정자들의 합·곱을 정확히 되돌린다. {@link com.sharedfate.perk.PerkDamageBoostBan}
 * 이 계산과 부착을 맡는다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link com.sharedfate.perk.effect.OffhandLockEffect}와 같은 이유다. {@link PerkEffect#apply}로
 * 미리 붙여 둘 것이 없다 — 매 틱 손에 든 것과 걸린 효과가 달라지므로 그때그때 다시 계산해야
 * 한다.
 */
public final class NoDamageBoostEffect implements PerkEffect {
	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final NoDamageBoostEffect INSTANCE = new NoDamageBoostEffect();

	private NoDamageBoostEffect() {
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
