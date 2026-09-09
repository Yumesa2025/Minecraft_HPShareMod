package com.sharedfate.perk;

import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 「가호」 세트가 {@code holder} 증강들의 모드를 정하는 규칙.
 *
 * <p>가호는 <b>이 증강을 고른 사람 하나만</b> 강해지고 나머지 팀원이 손해를 보는 증강들의
 * 묶음이고, 단계가 셋과 넷 둘뿐이다. 둘은 서로 맞바꾸는 관계다.
 *
 * <table border="1">
 *   <caption>가호 단계</caption>
 *   <tr><th>모은 개수</th><th>모드</th><th>무엇이 달라지나</th></tr>
 *   <tr><td>0~2</td><td>{@code NORMAL}</td><td>평소대로. 고른 사람만 {@code on_holder}</td></tr>
 *   <tr><td>3</td><td>{@code AMPLIFIED}</td><td>고른 사람이 {@code on_holder_amplified} 를 받는다</td></tr>
 *   <tr><td>4 이상</td><td>{@code EVERYONE}</td><td>강화가 사라지고 <b>전원이</b> {@code on_holder} 를 받는다</td></tr>
 * </table>
 *
 * <p>2단계를 두지 않은 것은 의도다. 둘만 모은 사람이 아무것도 못 받는 모양은 「기동」과 같다 —
 * 끝까지 모아야 값이 나오는 자리로 남긴다.
 *
 * <h2>가호에 속한 증강만 바뀐다</h2>
 * <p>판정에 {@code perkId} 를 함께 받는 이유다. 팀 상태만 보고 정하면, 세트 밖의 {@code holder}
 * 증강(무유형 「버프 돌리기」)까지 덩달아 강화되거나 전원에게 퍼진다. 그 증강의 재미는
 * 「누가 받을지 모르고 맞으면 뺏긴다」인데 그것이 통째로 사라진다.
 *
 * <h2>단계는 세트 정의가 정한다</h2>
 * <p>여기서 3·4라는 숫자를 직접 쓰지 않고 {@code sharedfate-sets-default.json} 에 적힌 단계를
 * 읽는다. 그 파일의 단계 수를 고치면 이 규칙도 함께 따라온다. 가호 단계의 {@code effects} 가
 * 비어 있는 것도 그래서다 — 붙일 효과가 있는 것이 아니라 <b>모드를 바꾸는 표식</b>이다.
 */
public final class PerkBlessingSet {

	private PerkBlessingSet() {
	}

	/**
	 * {@code PerkHolderManager} 에 꽂을 판정기. {@code SharedFateMod} 가 시작할 때 한 번 부른다.
	 */
	public static void install() {
		PerkHolderManager.setModeResolver(PerkBlessingSet::modeFor);
	}

	/**
	 * 이 팀에서 이 증강이 지금 어느 모드로 돌아야 하는가.
	 *
	 * <p>가호 유형이 아닌 증강은 언제나 {@code NORMAL} 이다.
	 */
	public static HolderEffect.HolderMode modeFor(@Nullable TeamState state, @Nullable String perkId) {
		if (!isBlessingPerk(perkId)) {
			return HolderEffect.HolderMode.NORMAL;
		}
		int highest = highestActiveTier(state);
		return HolderEffect.HolderMode.resolve(highest >= 3, highest >= 4);
	}

	/**
	 * 이 증강의 효과가 이 사람에게 걸리는가.
	 *
	 * <p>{@code holder} 로 감쌀 수 없는 가호 증강들이 「누구에게 걸리나」를 물어보는 자리다.
	 * 넉백 교체·피해 차단·교환 제외처럼 상태이상도 속성도 아닌 효과는 {@link HolderEffect} 에
	 * 담을 수 없어 저마다 독립 효과 타입이 되었고, 그것들은 {@code TeamState.perkOwners} 에서
	 * 주인을 직접 찾는다. 그 판정을 여기로 모아 <b>가호 4가 켜지면 팀 전원이 참</b>이 되게 한다.
	 *
	 * <p>가호가 아닌 증강, 그리고 가호가 셋 이하인 팀에서는 예전과 똑같이 <b>고른 사람만</b>
	 * 참이다. 주인이 없는 증강(「숨은 재능」처럼 덤으로 받은 것)은 {@code perkOwners} 에 적히지
	 * 않아 아무와도 같지 않다.
	 *
	 * <p>⚠ {@code state} 는 <b>물어본 사람의 팀 상태</b>여야 한다. 「전원」은 그 팀의 전원이라는
	 * 뜻이라, 남의 팀 상태를 넘기면 그 팀 것이 이 사람에게 걸린다.
	 */
	public static boolean appliesTo(@Nullable TeamState state, @Nullable String perkId,
			@Nullable UUID player) {
		if (state == null || perkId == null || player == null) {
			return false;
		}
		if (modeFor(state, perkId) == HolderEffect.HolderMode.EVERYONE) {
			return true;
		}
		return player.equals(state.perkOwners.get(perkId));
	}

	/**
	 * 지금 이 증강이 「가호 3」의 강화 모드인가.
	 *
	 * <p>넷을 모아 {@code EVERYONE} 으로 넘어가면 <b>거짓</b>이다. 3과 4는 맞바꾸는 관계라
	 * 강화와 「전원에게」가 함께 켜지지 않는다.
	 */
	public static boolean isAmplified(@Nullable TeamState state, @Nullable String perkId) {
		return modeFor(state, perkId) == HolderEffect.HolderMode.AMPLIFIED;
	}

	/**
	 * 이 팀에서 「가호 3」의 강화가 켜져 있는가. 증강 하나를 특정하지 않고 팀만 본다.
	 *
	 * <p>가호 증강인 것이 이미 분명한 자리에서 쓴다 — 어느 증강인지 되짚을 수 없는 계산
	 * ({@code PerkSwapRules} 의 교환 보너스)이 이 형태를 쓴다. 넷을 모아 「전원에게」로
	 * 넘어가면 {@link #isAmplified} 와 마찬가지로 거짓이다.
	 */
	public static boolean teamAmplified(@Nullable TeamState state) {
		int highest = highestActiveTier(state);
		return highest >= 3 && highest < 4;
	}

	/** 이 증강이 가호 유형인가. 풀에서 사라진 id 는 아니다. */
	static boolean isBlessingPerk(@Nullable String perkId) {
		if (perkId == null) {
			return false;
		}
		Perk perk = PerkRegistry.byId(perkId).orElse(null);
		return perk != null && perk.hasSetType(PerkSetType.BLESSING);
	}

	/**
	 * 지금 켜져 있는 가호 단계 중 가장 높은 것. 하나도 없으면 0.
	 *
	 * <p>단계는 누적이라 넷을 모으면 3단계와 4단계가 함께 켜져 있다. 그중 <b>가장 높은
	 * 것</b>이 모드를 정한다 — 그래야 4단계가 3단계의 강화를 덮어쓴다.
	 */
	static int highestActiveTier(@Nullable TeamState state) {
		List<PerkSets.Tier> tiers = PerkSetEffects.activeTiersOf(state);
		int highest = 0;
		for (PerkSets.Tier tier : tiers) {
			if (tier != null && tier.type() == PerkSetType.BLESSING && tier.count() > highest) {
				highest = tier.count();
			}
		}
		return highest;
	}
}
