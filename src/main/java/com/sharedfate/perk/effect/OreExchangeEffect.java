package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * 나무 도끼를 들고 우클릭하면, 인벤토리에서 종류를 가리지 않고 나무를 {@value #WOOD_COST}개
 * 소모하고 무작위 광물 하나를 준다. 부작용은 없다.
 *
 * <p>정의는 {@code { "type": "ore_exchange" }} 하나뿐이고 필드가 없다. 실버 「나무꾼의 욕심」이
 * 쓴다. {@link com.sharedfate.perk.effect.PairedMiningEffect}·{@link EchoMiningEffect}와 같은
 * 이유로 값을 전부 코드 상수로 고정했다 — 굴려 보고 조정하는 단계라 아직은 JSON으로 뺄 필요가
 * 없다. 다만 소모량만은 앞으로도 손댈 값이라 {@link #WOOD_COST} 상수 하나만 고치면 되도록
 * 실행부({@link com.sharedfate.perk.PerkOreExchange})가 이 값을 직접 읽어 쓴다.
 *
 * <h2>대가를 걸지 않는 이유</h2>
 * <p>예전에는 쓸 때마다 허기 V·독 I 이 10초간 걸렸다. 소모량이 60개이던 시절의 균형이었는데,
 * 나무 60개 자체가 이미 충분히 무거운 대가인 데다 독이 겹치면 <b>공유 체력</b>이 깎여 팀 전체가
 * 위험해진다. 즉 "쓸 만할 때 쓰는 선택지"가 아니라 "쓰면 팀이 죽을 수도 있는 버튼"이 되어
 * 아무도 쓰지 않았다. 소모량을 30개로 낮추면서 상태이상은 통째로 뺐다 — 대가는 나무 개수
 * 하나로 충분하다.
 *
 * <h2>"나무"의 범위</h2>
 * <p>{@code #minecraft:logs} 태그를 그대로 쓴다 — 원목 · 나무(6면 나무껍질 블록) · 벗긴 원목 ·
 * 벗긴 나무 · 네더 줄기/균사까지 전부 포함하고, 판자는 빠진다. 이 증강이 예전에 쓰던
 * {@code bonus_drop}(원목 +1)도 같은 태그를 썼으므로 "나무"의 뜻을 새로 정의하지 않고 그대로
 * 이어받았다. 판자를 넣지 않은 이유는 원목 1개가 판자 4개가 되므로, 판자까지 받아 주면 사실상
 * 나무 8개 남짓으로 광물을 뽑는 셈이 되어 30개라는 대가가 무의미해지기 때문이다.
 *
 * <h2>무엇을 주는가</h2>
 * <p>실제 채광 결과와 맞춘다 — 철·금·구리는 제련 전 원석({@code raw_iron} 등, 채굴하면 그대로
 * 나오는 형태), 다이아몬드는 원석이 없으므로 {@code diamond}, 석탄은 {@code coal} 그대로다.
 *
 * <h2>왜 표시 클래스인가</h2>
 * <p>{@link EchoMiningEffect}와 같은 이유다. {@link PerkEffect#apply}로 팀원에게 붙일 것이
 * 없다. 실제로 우클릭을 감지하고 나무를 세고 광물을 주는 일은
 * {@link com.sharedfate.perk.PerkOreExchange}가 맡는다.
 */
public final class OreExchangeEffect implements PerkEffect {
	/**
	 * 한 번 교환할 때 소모하는 나무 개수.
	 *
	 * <p>이 증강에서 앞으로도 조정될 값은 사실상 이것 하나뿐이라 상수로 따로 뺐다. 실행부와
	 * 안내 문구가 모두 이 값을 읽으므로 여기만 고치면 된다.
	 */
	public static final int WOOD_COST = 30;

	/** 이 도구를 주 손에 들고 우클릭해야 한다. */
	public static final Identifier TOOL = Identifier.withDefaultNamespace("wooden_axe");

	/** 결과 하나. 개수는 언제나 1이다. */
	public record Result(Identifier itemId, int weight) {
	}

	/** 다이아몬드 10% · 금 15% · 철 15% · 구리 30% · 석탄 30%. 순서대로 가중치 누적선을 이룬다. */
	public static final List<Result> RESULTS = List.of(
			new Result(Identifier.withDefaultNamespace("diamond"), 10),
			new Result(Identifier.withDefaultNamespace("raw_gold"), 15),
			new Result(Identifier.withDefaultNamespace("raw_iron"), 15),
			new Result(Identifier.withDefaultNamespace("raw_copper"), 30),
			new Result(Identifier.withDefaultNamespace("coal"), 30));

	public static final int TOTAL_WEIGHT =
			RESULTS.stream().mapToInt(Result::weight).sum();

	/** 상태가 없으므로 하나만 만들어 돌려쓴다. */
	public static final OreExchangeEffect INSTANCE = new OreExchangeEffect();

	private OreExchangeEffect() {
	}

	/**
	 * {@link #RESULTS}의 가중치대로 결과 하나를 뽑는다.
	 *
	 * <p>플레이어를 읽지 않는 순수 계산이라 살아 있는 서버 없이 시험할 수 있다.
	 */
	public static Identifier rollResult(RandomSource random) {
		int roll = random.nextInt(TOTAL_WEIGHT);
		int cumulative = 0;
		for (Result result : RESULTS) {
			cumulative += result.weight();
			if (roll < cumulative) {
				return result.itemId();
			}
		}
		// 부동소수 오차로도 여기 닿을 일이 없지만, 혹시 몰라 마지막 것으로 막는다.
		return RESULTS.get(RESULTS.size() - 1).itemId();
	}

	/** JSON에서 만든다. 읽을 필드가 없어 언제나 성공한다. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		return INSTANCE;
	}
}
