package com.sharedfate.perk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.SharedFateMod;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 세트 보상 정의 보관소.
 *
 * <p>{@code config/sharedfate-sets.json} 을 읽어 유형별 단계 목록으로 들고 있는다. 판정과 적용은
 * 하지 않는다 — 판정은 {@link PerkSets}(순수 계산), 적용은 {@link PerkSetEffects} 가 맡는다.
 *
 * <p>{@link PerkRegistry} 와 완전히 같은 규칙으로 만들었다. 설정 폴더에 파일이 없으면 모드에
 * 들어 있는 기본 정의를 꺼내 놓고, 읽을 수 없는 항목은 그것만 건너뛰며 경고를 남기고, 어떤
 * 경우에도 예외를 위로 던지지 않는다. 세트 정의 하나가 잘못돼 서버가 멈추면 안 된다.
 *
 * <h2>보상 값을 코드에 박지 않는 이유</h2>
 * <p>세트 보상은 밸런스를 잡으며 자주 바뀐다. 파일 한 곳에 모아 두면 서버 운영자가 코드를 다시
 * 빌드하지 않고 고칠 수 있고, 새 보상을 얹는 사람도 Java 를 건드릴 필요가 없다.
 *
 * <h2>새 보상을 얹는 법</h2>
 * <ol>
 *   <li>필요한 효과 타입이 이미 있으면 {@code sharedfate-sets-default.json} 의 해당 단계에
 *       {@code effects} 만 채운다. <b>그것으로 끝이다.</b></li>
 *   <li>없으면 {@code perk/effect/} 에 효과 클래스를 만들고 {@code PerkEffectType} 에 한 줄
 *       등록한 뒤 같은 방식으로 적는다. 등록을 빠뜨리면 <b>빌드는 통과하는데 그 단계만 조용히
 *       사라진다</b> — {@code DefaultPerkSetValuesTest} 가 그것을 잡는다.</li>
 * </ol>
 */
public final class PerkSetRegistry {
	/** 설정 폴더 안의 세트 정의 파일 이름. */
	public static final String FILE_NAME = "sharedfate-sets.json";
	/** 모드 안에 들어 있는 기본 세트 정의. 설정 파일이 없으면 이걸 꺼내 놓는다. */
	private static final String DEFAULT_RESOURCE = "sharedfate-sets-default.json";
	/** 한 유형에 적을 수 있는 단계 수 상한. 정의 실수로 수백 개가 들어오는 것을 막는다. */
	private static final int MAX_TIERS_PER_TYPE = 16;

	private static final Map<PerkSetType, List<PerkSets.Tier>> TIERS =
			new EnumMap<>(PerkSetType.class);
	private static boolean loaded;

	private PerkSetRegistry() {
	}

	/**
	 * 세트 효과가 {@link com.sharedfate.perk.effect.AttributeEffect} 수정자 이름을 만들 때 쓸
	 * 「증강 id」 자리의 값.
	 *
	 * <p><b>증강과 반드시 갈라져 있어야 한다.</b> {@code AttributeEffect.modifierId} 는
	 * {@code perk/<증강 id>/<순번>} 으로 수정자를 만드는데, 세트가 증강과 같은 이름을 쓰면 세트가
	 * 켜졌다 꺼질 때 증강이 걸어 둔 수정자를 지운다. 그래서 {@code set/<유형>/<단계>} 로 적는다 —
	 * 증강 id 는 언제나 {@code 네임스페이스:경로} 형태라 이 모양과 겹칠 수 없다.
	 */
	public static String effectOwnerId(PerkSetType type, int count) {
		return "set/" + (type == null ? "unknown" : type.id()) + "/" + count;
	}

	/**
	 * {@code configDir/sharedfate-sets.json} 을 읽어 세트 정의를 다시 만든다.
	 * 파일이 없으면 기본 정의를 꺼내 놓고, 그것도 못 하면 빈 채로 시작한다.
	 */
	public static synchronized void load(Path configDir) {
		TIERS.clear();
		loaded = true;

		if (configDir == null) {
			SharedFateMod.LOGGER.warn("설정 폴더가 없어 세트 정의를 비운 채로 시작합니다");
			return;
		}

		Path file = configDir.resolve(FILE_NAME);
		if (!Files.exists(file) && !writeBundledDefault(file)) {
			SharedFateMod.LOGGER.info("세트 정의 파일이 없어 세트 없이 시작합니다: {}", file);
			return;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			readInto(root, file);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("세트 정의 파일을 읽지 못해 세트 없이 시작합니다: {}", file, error);
			TIERS.clear();
		}
	}

	/**
	 * 모드에 들어 있는 기본 세트 정의를 설정 폴더로 꺼내 놓는다.
	 *
	 * @return 꺼내 놓기에 성공했으면 true
	 */
	private static boolean writeBundledDefault(Path file) {
		try (InputStream bundled = PerkSetRegistry.class.getResourceAsStream("/" + DEFAULT_RESOURCE)) {
			if (bundled == null) {
				return false;
			}
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.copy(bundled, file);
			SharedFateMod.LOGGER.info("기본 세트 정의를 만들었습니다: {}", file);
			return true;
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("기본 세트 정의를 만들지 못했습니다: {}", file, error);
			return false;
		}
	}

	/**
	 * 유형별 단계 표 전체. {@link PerkSets} 에 그대로 넘기는 형태다.
	 *
	 * <p>단계가 하나도 없는 유형은 표에 들어 있지 않다. {@link PerkSets} 는 없는 유형을 빈
	 * 목록으로 다룬다.
	 */
	public static synchronized Map<PerkSetType, List<PerkSets.Tier>> all() {
		return Map.copyOf(TIERS);
	}

	/** 한 유형의 단계들. 없으면 빈 목록. 파일에 적힌 순서를 지킨다. */
	public static synchronized List<PerkSets.Tier> tiersOf(PerkSetType type) {
		if (type == null) {
			return List.of();
		}
		return TIERS.getOrDefault(type, List.of());
	}

	/** 읽어 둔 단계가 하나도 없는가. 이때 세트 시스템은 통째로 잠잠하다. */
	public static synchronized boolean isEmpty() {
		return TIERS.isEmpty();
	}

	/** 한 번이라도 {@link #load(Path)} 를 거쳤는지. */
	public static synchronized boolean isLoaded() {
		return loaded;
	}

	/** 세트 정의를 모두 비운다. 테스트에서 상태를 격리하는 용도다. */
	public static synchronized void clear() {
		TIERS.clear();
		loaded = false;
	}

	// ------------------------------------------------------------------ 읽기

	private static void readInto(JsonElement root, Path file) {
		if (root == null || !root.isJsonObject()) {
			SharedFateMod.LOGGER.warn("세트 정의 파일의 최상위가 객체가 아닙니다: {}", file);
			return;
		}
		JsonElement setsElement = root.getAsJsonObject().get("sets");
		if (setsElement == null || !setsElement.isJsonArray()) {
			SharedFateMod.LOGGER.warn("세트 정의 파일에 sets 배열이 없습니다: {}", file);
			return;
		}

		int tierCount = 0;
		int skipped = 0;
		for (JsonElement element : setsElement.getAsJsonArray()) {
			if (element == null || !element.isJsonObject()) {
				SharedFateMod.LOGGER.warn("세트 항목이 객체가 아니라 건너뜁니다: {}", element);
				skipped++;
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			String rawType = PerkEffectType.readString(json, "type");
			PerkSetType type = PerkSetType.fromId(rawType);
			if (type == null) {
				SharedFateMod.LOGGER.warn("알 수 없는 세트 유형이라 건너뜁니다: {}", rawType);
				skipped++;
				continue;
			}
			if (TIERS.containsKey(type)) {
				SharedFateMod.LOGGER.warn("세트 유형 {} 가 두 번 적혀 있어 나중 것을 버립니다", type.id());
				skipped++;
				continue;
			}

			List<PerkSets.Tier> tiers = parseTiers(type, json);
			// 단계가 하나도 없는 유형은 표에 넣지 않는다. 「무기」처럼 자리만 잡아 둔 항목이라
			// 값이 들어오기 전까지는 없는 것과 같다.
			if (!tiers.isEmpty()) {
				TIERS.put(type, tiers);
				tierCount += tiers.size();
			}
		}

		long placeholders = TIERS.values().stream()
				.flatMap(List::stream)
				.filter(PerkSets.Tier::isPlaceholder)
				.count();
		SharedFateMod.LOGGER.info(
				"세트 {}종 {}단계를 읽었습니다 (건너뜀 {}개, 아직 효과가 비어 있는 단계 {}개)",
				TIERS.size(), tierCount, skipped, placeholders);
	}

	/**
	 * 한 유형의 {@code tiers} 배열을 읽는다.
	 *
	 * <p>단계 하나가 잘못됐으면 <b>그 단계만</b> 버린다. 증강과 달리 세트는 여러 단계가 서로
	 * 독립이라, 4단계 하나가 깨졌다고 2·3단계까지 없앨 이유가 없다.
	 */
	private static List<PerkSets.Tier> parseTiers(PerkSetType type, JsonObject json) {
		JsonElement element = json.get("tiers");
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("세트 {}: tiers 가 배열이 아닙니다", type.id());
			return List.of();
		}
		JsonArray array = element.getAsJsonArray();
		if (array.size() > MAX_TIERS_PER_TYPE) {
			SharedFateMod.LOGGER.warn("세트 {}: 단계가 너무 많습니다 ({}개)", type.id(), array.size());
			return List.of();
		}

		List<PerkSets.Tier> tiers = new ArrayList<>(array.size());
		for (JsonElement raw : array) {
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("세트 {}: 단계 항목이 객체가 아니라 건너뜁니다", type.id());
				continue;
			}
			PerkSets.Tier tier = parseTier(type, raw.getAsJsonObject());
			if (tier == null) {
				continue;
			}
			if (tiers.stream().anyMatch(existing -> existing.count() == tier.count())) {
				SharedFateMod.LOGGER.warn("세트 {}: {}단계가 두 번 적혀 있어 나중 것을 버립니다",
						type.id(), tier.count());
				continue;
			}
			tiers.add(tier);
		}
		return List.copyOf(tiers);
	}

	/** 단계 하나를 읽는다. 어디 한 군데라도 잘못됐으면 경고를 남기고 null. */
	private static PerkSets.Tier parseTier(PerkSetType type, JsonObject json) {
		try {
			int count = PerkEffectType.readInt(json, "count", 0);
			if (count < 1) {
				SharedFateMod.LOGGER.warn("세트 {}: 단계의 count 가 없거나 1 보다 작습니다 ({})",
						type.id(), count);
				return null;
			}
			// count 를 PerkSetType.threshold() 와 비교해 거르지 않는다. 둘은 뜻이 다르다.
			// threshold() 는 「이 유형을 노렸을 때 몇 개면 세트라 부를 만한가」를 시뮬레이션으로
			// 정한 값이고, count 는 「이 보상이 열리는 개수」다. 실제로 어긋나는 자리가 있다 —
			// 채굴·화력은 임계값이 3인데 2단계 보상이 있고, 기동은 임계값이 2인데 3단계뿐이다.
			// 여기서 걸러 내면 설계대로 적은 단계가 조용히 사라진다.

			// 단계에는 따로 이름을 붙이지 않는다. 「보급 2」·「보급 3」처럼 유형 이름과 열리는
			// 개수만 보여 주는 것이 규칙이라, 기본 정의에는 name 이 아예 없다. 그래도 읽기는
			// 하는 이유는 설정 파일에서 이름을 붙이고 싶은 사람을 막을 이유가 없어서다.
			String name = PerkEffectType.readString(json, "name");
			if (name == null || name.isBlank()) {
				name = type.displayName() + " " + count;
			}
			String description = PerkEffectType.readString(json, "description");
			if (description == null) {
				description = "";
			}

			List<PerkEffect> effects = parseEffects(type, count, json);
			if (effects == null) {
				return null;
			}
			return new PerkSets.Tier(type, count, name, description, effects);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("세트 {} 의 단계를 읽다가 실패해 건너뜁니다", type.id(), error);
			return null;
		}
	}

	/**
	 * {@code effects} 배열을 읽는다. 형식은 증강의 것과 <b>완전히 같고</b>
	 * {@link PerkEffectType#create} 를 그대로 재사용한다.
	 *
	 * <p>증강과 다른 점이 둘 있다.
	 *
	 * <ul>
	 *   <li><b>비어 있어도 된다.</b> 이름과 설명만 정해 두고 값을 나중에 채우는 단계가 있다.
	 *       증강이라면 「설명만 있고 아무 일도 안 하는」 정의라 버려야 하지만, 세트는 보상 24개를
	 *       여럿이 나눠 붙이는 중이라 빈 자리가 먼저 생긴다.</li>
	 *   <li>효과 하나라도 잘못되면 <b>그 단계 전체</b>를 버린다(null). 설명은 그대로인데 효과
	 *       일부만 빠지면 플레이어를 속이는 셈이라는 이유는 증강과 같다.</li>
	 * </ul>
	 *
	 * @return 읽어 낸 효과들. 하나라도 잘못됐으면 null
	 */
	private static List<PerkEffect> parseEffects(PerkSetType type, int count, JsonObject json) {
		JsonElement element = json.get("effects");
		if (element == null || element.isJsonNull()) {
			return List.of();
		}
		if (!element.isJsonArray()) {
			SharedFateMod.LOGGER.warn("세트 {} {}단계: effects 가 배열이 아닙니다", type.id(), count);
			return null;
		}

		// 수정자 이름을 증강과 갈라 두는 자리. 까닭은 effectOwnerId 에 적어 뒀다.
		String owner = effectOwnerId(type, count);
		JsonArray array = element.getAsJsonArray();
		List<PerkEffect> effects = new ArrayList<>(array.size());
		for (int index = 0; index < array.size(); index++) {
			JsonElement raw = array.get(index);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("세트 {} {}단계: {}번째 효과가 객체가 아닙니다",
						type.id(), count, index);
				return null;
			}
			JsonObject effectJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(effectJson, "type");
			PerkEffectType effectType = PerkEffectType.fromId(typeId);
			if (effectType == null) {
				SharedFateMod.LOGGER.warn("세트 {} {}단계: 알 수 없는 효과 type 입니다 ({})",
						type.id(), count, typeId);
				return null;
			}
			PerkEffect effect = effectType.create(owner, index, effectJson);
			if (effect == null) {
				return null;
			}
			effects.add(effect);
		}
		return effects;
	}
}
