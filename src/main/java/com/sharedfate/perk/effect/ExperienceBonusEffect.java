package com.sharedfate.perk.effect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.BlockSelector;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 정해진 <b>출처</b>에서 나오는 경험치에만 배율을 먹이는 효과.
 *
 * <pre>{@code
 * { "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }
 * }</pre>
 *
 * <ul>
 *   <li>{@code source} — {@code ore}(광물) · {@code mob}(몹) · {@code any}(둘 다). 모르는 값이면
 *       경고를 남기고 정의를 버린다. 조용히 {@code any} 로 넘기지 않는다 — 오타 하나가
 *       「광물만」이어야 할 보상을 모든 경험치에 거는 사고가 된다.</li>
 *   <li>{@code multiplier} — 곱할 값. {@code 1.5} 면 50% 증가다.
 *       {@link #MIN_MULTIPLIER}~{@link #MAX_MULTIPLIER} 범위를 벗어나면 정의를 버린다.</li>
 * </ul>
 *
 * <h2>{@code experienceMultiplier} 설정과 무엇이 다른가</h2>
 * <p>{@code SharedFateConfig.experienceMultiplier} 는 <b>모두에게 언제나</b> 걸리는 전역 배율이고
 * 출처를 가리지 않는다. 이 효과는 <b>그 팀에게만</b>, <b>정해진 출처에서만</b> 걸린다. 둘은
 * 서로를 대체하지 않고 <b>곱해진다</b>. 여러 개를 가지고 있어도 전부 곱해진다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "어느 출처에, 얼마를 곱하는가"만 들고 있는 자료 그릇이다. {@link #apply}/
 * {@link #remove} 는 아무 일도 하지 않는다 — 붙였다 뗄 수 있는 것이 아니라 경험치가 생기는
 * 순간에 조회하는 값이기 때문이다.
 *
 * <p>실제로 곱하는 자리는 {@link com.sharedfate.sync.ExperienceBonus} 와 그것을 부르는 두
 * mixin({@code BlockExperienceSourceMixin}·{@code MobExperienceSourceMixin})이다.
 *
 * <h2>「광물」의 정의는 새로 적지 않는다</h2>
 * <p>{@link #ORE_BLOCKS} 는 {@link LuckyOreEffect#DEFAULT_ORE_BLOCKS} 를 <b>그대로 가리킨다.</b>
 * 그 목록은 {@code sharedfate:greedy_pickaxe}(욕심 많은 곡괭이)의 {@code blocks} 배열과 같은
 * 것이고, 이 저장소에서 「광물」이라는 말이 뜻하는 바는 그 한 곳에만 적혀 있어야 한다. 여기에
 * 목록을 다시 적으면 어느 한쪽만 고쳤을 때 「욕심 많은 곡괭이는 터지는데 경험치는 안 붙는」
 * 상태가 조용히 생긴다.
 */
public final class ExperienceBonusEffect implements PerkEffect {
	/** 곱할 수 있는 값의 하한. 0 이나 음수는 경험치를 없애는 정의라 받지 않는다. */
	static final double MIN_MULTIPLIER = 0.01;

	/** 곱할 수 있는 값의 상한. 이보다 크면 한 회차의 성장 속도가 통째로 무너진다. */
	static final double MAX_MULTIPLIER = 10.0;

	/**
	 * 「광물」로 치는 블록 목록. 「욕심 많은 곡괭이」·「운수 좋은 날」과 같은 배열이다.
	 *
	 * <p><b>여기에 목록을 새로 적지 말 것.</b> 늘리거나 줄일 일이 생기면
	 * {@link LuckyOreEffect#DEFAULT_ORE_BLOCKS} 한 곳만 고친다.
	 */
	public static final List<String> ORE_BLOCKS = LuckyOreEffect.DEFAULT_ORE_BLOCKS;

	/**
	 * {@link #ORE_BLOCKS} 로 만든 판정기.
	 *
	 * <p>목록이 상수라 {@link BlockSelector#fromJson} 이 {@code null} 을 돌려줄 수 없다. 그래도
	 * 확인해서 터뜨린다 — 목록에 오타가 나면 조용히 「모든 블록」이 되기 때문이다.
	 */
	private static final BlockSelector ORE_SELECTOR = buildOreSelector();

	/**
	 * 경험치가 어디서 나왔는가.
	 *
	 * <p>{@link #ANY} 는 정의 파일에 적을 수 있는 값이지 「판정할 때 들어오는 값」이 아니다.
	 * 곱하는 자리는 언제나 {@link #ORE} 나 {@link #MOB} 중 하나로 물어본다.
	 */
	public enum Source {
		/** 광물 블록을 캐서 나온 경험치. */
		ORE("ore"),
		/** 몹을 잡아서 나온 경험치. */
		MOB("mob"),
		/** 위 둘 다. */
		ANY("any");

		private final String id;

		Source(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** JSON 의 {@code source} 문자열에 맞는 값. 알 수 없으면 {@code null}. */
		public static @Nullable Source fromId(@Nullable String id) {
			if (id == null) {
				return null;
			}
			String normalized = id.trim().toLowerCase(Locale.ROOT);
			for (Source source : values()) {
				if (source.id.equals(normalized)) {
					return source;
				}
			}
			return null;
		}
	}

	private final Source source;
	private final double multiplier;

	public ExperienceBonusEffect(Source source, double multiplier) {
		this.source = source == null ? Source.ANY : source;
		this.multiplier = multiplier;
	}

	/** JSON 에서 만든다. 정의가 잘못됐으면 경고를 남기고 {@code null}. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String rawSource = PerkEffectType.readString(json, "source");
		Source source = Source.fromId(rawSource);
		if (source == null) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: experience_bonus 의 source 가 없거나 모르는 값입니다 ({}). "
							+ "쓸 수 있는 값은 ore·mob·any 입니다",
					perkId, rawSource);
			return null;
		}

		Double multiplier = PerkEffectType.readDouble(json, "multiplier");
		if (multiplier == null || multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: experience_bonus 의 multiplier 가 없거나 {}~{} 범위를 벗어났습니다 ({})",
					perkId, MIN_MULTIPLIER, MAX_MULTIPLIER, multiplier);
			return null;
		}
		return new ExperienceBonusEffect(source, multiplier);
	}

	public Source source() {
		return source;
	}

	public double multiplier() {
		return multiplier;
	}

	/**
	 * 이 출처에서 나온 경험치에 걸리는가.
	 *
	 * <p>{@code any} 로 적힌 효과는 {@code ore} 에도 {@code mob} 에도 걸린다.
	 */
	public boolean appliesTo(@Nullable Source actual) {
		if (actual == null) {
			return false;
		}
		return source == Source.ANY || source == actual;
	}

	/** 이 출처에 곱할 값. 해당 없으면 1.0 이라 그냥 곱해도 된다. */
	public double multiplierFor(@Nullable Source actual) {
		if (!appliesTo(actual) || !Double.isFinite(multiplier) || multiplier <= 0.0) {
			return 1.0;
		}
		return multiplier;
	}

	// ------------------------------------------------------------------ 광물 판정

	/**
	 * 이 블록이 「광물」인가.
	 *
	 * <p>블록 태그는 데이터팩이 올라온 뒤에만 묶인다. 그 전에 물으면 {@link BlockSelector} 가
	 * 태그 판정만 건너뛰고 이름으로 적은 항목만 본다 — 예외가 나지 않는다.
	 */
	public static boolean isOre(@Nullable BlockState state) {
		return ORE_SELECTOR.matches(state);
	}

	/** 광물 판정기. 시험이 어떤 태그·블록이 들어 있는지 들여다볼 때 쓴다. */
	public static BlockSelector oreSelector() {
		return ORE_SELECTOR;
	}

	private static BlockSelector buildOreSelector() {
		JsonObject synthetic = new JsonObject();
		JsonArray array = new JsonArray();
		for (String entry : ORE_BLOCKS) {
			array.add(entry);
		}
		synthetic.add("blocks", array);
		BlockSelector selector =
				BlockSelector.fromJson("sharedfate:experience_bonus", "experience_bonus", synthetic);
		if (selector == null) {
			throw new IllegalStateException("experience_bonus 의 광물 목록이 올바르지 않습니다");
		}
		return selector;
	}
}
