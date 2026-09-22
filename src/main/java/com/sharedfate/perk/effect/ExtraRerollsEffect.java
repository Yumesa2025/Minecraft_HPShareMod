package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.team.TeamCreationSettings;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

/**
 * <b>이번 회차에만</b> 다시 뽑기 횟수를 더 준다. 세트 「도박」의 <b>두 단계가 모두</b> 쓴다 —
 * 2단계 「한 판 더」가 5, 3단계 「어차피 프리즘」이 3 이다.
 *
 * <p><b>단계는 누적이라 둘이 함께 켜진다.</b> 3단계가 2단계를 대체하지 않으므로 도박 증강 셋을
 * 모은 팀의 몫은 <b>8</b> 이고, 회차당 기본 3 과 합쳐 11회가 된다. 3단계가 리롤을 주기 시작한
 * 것은 0.24.0-dev 부터다 — 그 전 문서를 보고 「3단계는 프리즘만 바꾼다」고 읽지 말 것.
 *
 * <pre>{@code
 * { "type": "extra_rerolls", "amount": 5 }
 * }</pre>
 *
 * <ul>
 *   <li>{@code amount} — 더 줄 횟수. 1 이상 {@link TeamCreationSettings#MAX_REROLL_COUNT} 이하가
 *       아니면 정의를 버린다.</li>
 * </ul>
 *
 * <h2>이 세트 보상만 「재계산형」이 아니다</h2>
 * <p>다른 세트 보상은 전부 보유 증강에서 <b>매번 다시 계산하는 파생 상태</b>다. 약탈 등급도
 * 피해 배율도 「지금 이 팀이 얼마여야 하는가」를 물을 때마다 새로 세면 그만이라, 몇 번을 물어도
 * 답이 같다.
 *
 * <p>그런데 다시 뽑기 횟수는 <b>소비되며 줄어드는 값</b>이다. 「지금 몇 번 남았는가」는 다시
 * 계산할 수 없다 — 팀이 몇 번을 이미 썼는지는 보유 증강 어디에도 적혀 있지 않기 때문이다.
 * 그래서 이 효과만은 {@code TeamState.rerollsRemaining} 이라는 <b>저장되는 값</b>을 실제로
 * 늘려야 하고, 그 순간 「두 번 늘리면 두 배가 된다」는 문제가 따라온다.
 *
 * <h2>그래서 지급은 딱 한 곳에서만 한다</h2>
 * <p>{@code PerkGrantChain.run} 의 끝이다. 「직접 고름」과 「시간초과 자동선택」이 합류하는
 * {@code PerkManager.commit} 아래에 있는 유일한 지점이라, 증강 하나를 얻는 사건마다 정확히 한
 * 번만 지나간다. {@code PerkManager.refreshPlayer} 에 두면 접속·부활 때마다 다시 도는 길이라
 * 접속할 때마다 몫이 통째로 불어난다({@code item_grant} 를 그 자리에 두면 안 되는 것과 같은
 * 이유다).
 *
 * <p>거기서도 「몫을 더한다」가 아니라 {@link TeamState#syncRerollSetBonus} 로 <b>「세트로 얻은
 * 몫을 지금 값에 맞춘다」</b> 를 시킨다. 이미 받아 둔 팀은 다시 불러도 아무 일이 없고, 세트가
 * 풀린 팀은 그 자리에서 몫이 0 으로 내려간다. 몇 번을 불러도 결과가 같다.
 *
 * <h2>저장 왕복에서 살아남는 법</h2>
 * <p>{@code TeamState} 의 저장 클램프 두 곳이 {@code rerollsRemaining} 을 <b>회차당 허용치</b>로
 * 자른다({@code sanitize}·{@code applyRerollSection}). 남은 횟수만 올려 두면 서버를 껐다
 * 켜는 순간 허용치로 되돌아간다. 그래서 「세트로 얻은 몫」이 {@code TeamState.rerollSetBonus} 로
 * 함께 저장되고, 두 클램프의 상한은 {@code 허용치 + 세트 몫} 이다. 자세한 것은 그 필드에 있다.
 *
 * <h2>붙였다 떼는 효과가 아니다</h2>
 * <p>{@link PerkEffect#apply} 로 플레이어에게 붙일 것이 없다. 「이 팀이 세트로 몇 회를 받아야
 * 하는가」라는 물음에 답하기 위한 자료 그릇일 뿐이고, 그 답이 {@link #bonusOf(TeamState)} 다.
 */
public final class ExtraRerollsEffect implements PerkEffect {
	private final int amount;

	public ExtraRerollsEffect(int amount) {
		this.amount = amount;
	}

	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int amount = PerkEffectType.readInt(json, "amount", 0);
		if (amount <= 0 || amount > TeamCreationSettings.MAX_REROLL_COUNT) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: extra_rerolls 의 amount 가 없거나 범위를 벗어났습니다 ({})", perkId, amount);
			return null;
		}
		return new ExtraRerollsEffect(amount);
	}

	/** 이번 회차에 더 줄 다시 뽑기 횟수. */
	public int amount() {
		return amount;
	}

	/**
	 * 이 팀이 <b>세트와 보유 증강을 합쳐</b> 이번 회차에 더 받아야 할 횟수. 없으면 0.
	 *
	 * <p>같은 형이 여럿이면 <b>더한다.</b> {@link AlwaysLootingEffect} 가 「가장 높은 하나만」인
	 * 것과 반대다.
	 *
	 * <p>돌려주는 값에는 상한을 걸지 않는다. 회차당 허용치와 합쳐
	 * {@link TeamCreationSettings#MAX_REROLL_COUNT} 를 넘지 않도록 접는 일은
	 * {@link TeamState#syncRerollSetBonus} 한 곳에서만 한다 — 접는 규칙이 두 군데 있으면 반드시
	 * 어긋난다.
	 *
	 * <p>팀이 없거나 증강을 껐으면 곧바로 0 이다. 풀에서 사라진 id 는 건너뛴다.
	 */
	public static int bonusOf(@Nullable TeamState state) {
		if (state == null || !state.perksEnabled) {
			return 0;
		}
		int total = 0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof ExtraRerollsEffect extra) {
					total += extra.amount();
				}
			}
		}
		// 켜진 세트의 효과도 같은 규칙으로 센다. 실제로 이 형을 태우는 것은 지금 이 길뿐이다 —
		// 이 한 줄이 없으면 빌드도 통과하고 로그도 없는데 도박 2·3단계가 완전 무동작이 된다.
		for (PerkEffect effect : PerkSetEffects.activeEffectsOf(state)) {
			if (effect instanceof ExtraRerollsEffect extra) {
				total += extra.amount();
			}
		}
		return total;
	}
}
