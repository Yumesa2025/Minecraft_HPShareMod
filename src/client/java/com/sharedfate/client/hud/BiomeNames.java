package com.sharedfate.client.hud;

import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 바이옴 id 를 <b>한글 이름</b>으로 바꾼다. {@link CoordinateHud} 가 쓰는 표다.
 *
 * <p>클라이언트의 언어 설정과 무관하게 늘 한글이 나와야 하므로 표를 코드 안에 박아 둔다.
 * {@code Component.translatable} 로 바닐라 번역 키를 쓰면 언어 설정을 따라간다.
 *
 * <p>표의 한글 이름은 <b>마인크래프트 26.2 공식 한국어 번역({@code ko_kr.json})에서 그대로
 * 옮겼다.</b> 그래서 한국어로 하는 사람이 게임 안 다른 곳에서 보던 이름과 어긋나지 않는다.
 *
 * <h2>모르는 id 에서 터지지 않는다</h2>
 * <p>다른 모드가 넣은 바이옴이나 다음 판올림에서 늘어난 바이옴은 표에 없다. 그때는 예외를
 * 던지지도, 빈칸을 남기지도 않고 <b>id 의 path 부분을 그대로</b> 보여 준다. HUD 는 매 프레임
 * 돌기 때문에 여기서 예외가 나면 화면 전체가 죽는다.
 */
public final class BiomeNames {
	/**
	 * 바닐라 바이옴 66종의 id → 한글 이름.
	 *
	 * <p>오버월드·네더·엔드에 더해 {@code the_void} 까지 {@code Biomes} 클래스에 등록된 것을
	 * 전부 담았다. 순서는 그 클래스에 적힌 차례와 같다.
	 */
	private static final Map<Identifier, String> KOREAN_NAMES = createTable();

	private BiomeNames() {
	}

	/**
	 * 바이옴 id 에 해당하는 한글 이름.
	 *
	 * <p>표에 없으면 id 의 path 를, 아직 바이옴을 모르는 상황({@code null})이면 빈 문자열을
	 * 돌려준다. <b>빈 문자열은 「그 줄을 그리지 말라」는 뜻이다.</b>
	 */
	public static String korean(Identifier biomeId) {
		if (biomeId == null) {
			return "";
		}
		String known = KOREAN_NAMES.get(biomeId);
		if (known != null) {
			return known;
		}
		// 표에 없는 id. namespace 까지 다 적으면 「terralith:alpine_grove」처럼 길어져 화면을
		// 잡아먹으므로 path 만 남긴다.
		return biomeId.getPath();
	}

	/** 표에 담긴 바이옴 수. 단위 시험이 표가 통째로 빠지지 않았는지 확인하는 데 쓴다. */
	public static int size() {
		return KOREAN_NAMES.size();
	}

	private static Map<Identifier, String> createTable() {
		Map<Identifier, String> names = new HashMap<>();
		put(names, "the_void", "공허");

		// 오버월드
		put(names, "plains", "평원");
		put(names, "sunflower_plains", "해바라기 평원");
		put(names, "snowy_plains", "눈 덮인 평원");
		put(names, "ice_spikes", "역고드름");
		put(names, "desert", "사막");
		put(names, "swamp", "늪");
		put(names, "mangrove_swamp", "맹그로브 늪");
		put(names, "forest", "숲");
		put(names, "flower_forest", "꽃 숲");
		put(names, "birch_forest", "자작나무 숲");
		put(names, "dark_forest", "어두운 숲");
		put(names, "pale_garden", "창백한 정원");
		put(names, "old_growth_birch_forest", "자작나무 원시림");
		put(names, "old_growth_pine_taiga", "소나무 원시 타이가");
		put(names, "old_growth_spruce_taiga", "가문비나무 원시 타이가");
		put(names, "taiga", "타이가");
		put(names, "snowy_taiga", "눈 덮인 타이가");
		put(names, "savanna", "사바나");
		put(names, "savanna_plateau", "사바나 고원");
		put(names, "windswept_hills", "바람이 세찬 언덕");
		put(names, "windswept_gravelly_hills", "바람이 세찬 자갈투성이 언덕");
		put(names, "windswept_forest", "바람이 세찬 숲");
		put(names, "windswept_savanna", "바람이 세찬 사바나");
		put(names, "jungle", "정글");
		put(names, "sparse_jungle", "듬성듬성한 정글");
		put(names, "bamboo_jungle", "대나무 정글");
		put(names, "badlands", "악지");
		put(names, "eroded_badlands", "침식된 악지");
		put(names, "wooded_badlands", "나무가 우거진 악지");
		put(names, "meadow", "목초지");
		put(names, "cherry_grove", "벚나무 숲");
		put(names, "grove", "산림");
		put(names, "snowy_slopes", "눈 덮인 비탈");
		put(names, "frozen_peaks", "얼어붙은 봉우리");
		put(names, "jagged_peaks", "뾰족한 봉우리");
		put(names, "stony_peaks", "돌 봉우리");
		put(names, "river", "강");
		put(names, "frozen_river", "얼어붙은 강");
		put(names, "beach", "해변");
		put(names, "snowy_beach", "눈 덮인 해변");
		put(names, "stony_shore", "돌 해안");
		put(names, "warm_ocean", "따뜻한 바다");
		put(names, "lukewarm_ocean", "미지근한 바다");
		put(names, "deep_lukewarm_ocean", "깊고 미지근한 바다");
		put(names, "ocean", "바다");
		put(names, "deep_ocean", "깊은 바다");
		put(names, "cold_ocean", "차가운 바다");
		put(names, "deep_cold_ocean", "깊고 차가운 바다");
		put(names, "frozen_ocean", "얼어붙은 바다");
		put(names, "deep_frozen_ocean", "깊고 얼어붙은 바다");
		put(names, "mushroom_fields", "버섯 들판");

		// 동굴
		put(names, "dripstone_caves", "점적석 동굴");
		put(names, "lush_caves", "무성한 동굴");
		put(names, "deep_dark", "깊은 어둠");
		put(names, "sulfur_caves", "유황 동굴");

		// 네더
		put(names, "nether_wastes", "네더 황무지");
		put(names, "warped_forest", "뒤틀린 숲");
		put(names, "crimson_forest", "진홍빛 숲");
		put(names, "soul_sand_valley", "영혼 모래 골짜기");
		put(names, "basalt_deltas", "현무암 삼각주");

		// 엔드
		put(names, "the_end", "엔드");
		put(names, "end_highlands", "엔드 고지");
		put(names, "end_midlands", "엔드 중지");
		put(names, "small_end_islands", "작은 엔드 섬");
		put(names, "end_barrens", "엔드 불모지");

		return Map.copyOf(names);
	}

	private static void put(Map<Identifier, String> names, String path, String korean) {
		names.put(Identifier.fromNamespaceAndPath(Identifier.DEFAULT_NAMESPACE, path), korean);
	}
}
