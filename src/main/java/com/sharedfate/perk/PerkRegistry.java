package com.sharedfate.perk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.SharedFateMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 증강 정의 보관소.
 *
 * <p>{@code config/sharedfate-perks.json} 에서 읽은 목록과 Java로 등록한 custom 핸들러를
 * 합쳐서 id로 찾을 수 있게 해 준다.
 *
 * <p>이 클래스는 어떤 경우에도 예외를 위로 던지지 않는다. 파일이 없으면 빈 풀로,
 * 파일이 깨졌으면 읽을 수 있는 것만으로 시작한다. 잘못된 증강 하나가 서버를 멈추면 안 된다.
 */
public final class PerkRegistry {
	/** 설정 폴더 안의 증강 정의 파일 이름. */
	public static final String FILE_NAME = "sharedfate-perks.json";
	/** 모드 안에 들어 있는 기본 증강 풀. 설정 파일이 없으면 이걸 꺼내 놓는다. */
	private static final String DEFAULT_RESOURCE = "sharedfate-perks-default.json";

	private static final Map<String, Perk> PERKS = new LinkedHashMap<>();
	private static final Map<String, PerkEffect> CUSTOM_HANDLERS = new HashMap<>();
	private static boolean loaded;

	private PerkRegistry() {
	}

	/**
	 * {@code configDir/sharedfate-perks.json} 을 읽어 증강 풀을 다시 만든다.
	 * 파일이 없으면 빈 풀로 시작한다. custom 핸들러 등록은 그대로 유지된다.
	 */
	public static synchronized void load(Path configDir) {
		PERKS.clear();
		// 대가 표시는 효과 객체의 신원으로 걸려 있다. 정의를 다시 읽으면 객체가 통째로 새로
		// 만들어지므로 옛 표시는 아무도 가리키지 않는 쓰레기가 된다.
		PerkDrawbacks.clear();
		loaded = true;

		if (configDir == null) {
			SharedFateMod.LOGGER.warn("설정 폴더가 없어 증강을 빈 풀로 시작합니다");
			return;
		}

		Path file = configDir.resolve(FILE_NAME);
		if (!Files.exists(file) && !writeBundledDefault(file)) {
			SharedFateMod.LOGGER.info("증강 정의 파일이 없어 빈 풀로 시작합니다: {}", file);
			return;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(reader);
			readInto(root, file);
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("증강 정의 파일을 읽지 못해 빈 풀로 시작합니다: {}", file, error);
			PERKS.clear();
		}
	}

	/**
	 * 모드에 들어 있는 기본 증강 풀을 설정 폴더로 꺼내 놓는다.
	 *
	 * @return 꺼내 놓기에 성공했으면 true
	 */
	private static boolean writeBundledDefault(Path file) {
		try (InputStream bundled = PerkRegistry.class.getResourceAsStream("/" + DEFAULT_RESOURCE)) {
			if (bundled == null) {
				return false;
			}
			Path parent = file.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.copy(bundled, file);
			SharedFateMod.LOGGER.info("기본 증강 풀을 만들었습니다: {}", file);
			return true;
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("기본 증강 풀을 만들지 못했습니다: {}", file, error);
			return false;
		}
	}

	/** id로 증강 하나를 찾는다. */
	public static synchronized Optional<Perk> byId(String id) {
		return id == null ? Optional.empty() : Optional.ofNullable(PERKS.get(id));
	}

	/** 읽어 둔 증강 전체. 파일에 적힌 순서를 지킨다. */
	public static synchronized List<Perk> all() {
		return List.copyOf(PERKS.values());
	}

	/** custom 효과가 위임할 Java 핸들러를 등록한다. */
	public static synchronized void registerCustom(String handlerId, PerkEffect effect) {
		if (handlerId == null || handlerId.isBlank() || effect == null) {
			SharedFateMod.LOGGER.warn("custom 핸들러 등록에 빈 id나 null 효과가 들어왔습니다: {}", handlerId);
			return;
		}
		String key = handlerId.trim();
		if (CUSTOM_HANDLERS.put(key, effect) != null) {
			SharedFateMod.LOGGER.warn("custom 핸들러 {} 를 덮어썼습니다", key);
		}
	}

	/**
	 * 등록된 custom 핸들러를 찾는다.
	 * {@link com.sharedfate.perk.effect.CustomEffect} 가 쓰기 위한 통로다.
	 */
	public static synchronized Optional<PerkEffect> customHandler(String handlerId) {
		return handlerId == null ? Optional.empty()
				: Optional.ofNullable(CUSTOM_HANDLERS.get(handlerId.trim()));
	}

	/** 한 번이라도 {@link #load(Path)} 를 거쳤는지. */
	public static synchronized boolean isLoaded() {
		return loaded;
	}

	/** 증강과 핸들러를 모두 비운다. 테스트에서 상태를 격리하는 용도다. */
	public static synchronized void clear() {
		PERKS.clear();
		CUSTOM_HANDLERS.clear();
		PerkDrawbacks.clear();
		loaded = false;
	}

	private static void readInto(JsonElement root, Path file) {
		if (root == null || !root.isJsonObject()) {
			SharedFateMod.LOGGER.warn("증강 정의 파일의 최상위가 객체가 아닙니다: {}", file);
			return;
		}
		JsonElement perksElement = root.getAsJsonObject().get("perks");
		if (perksElement == null || !perksElement.isJsonArray()) {
			SharedFateMod.LOGGER.warn("증강 정의 파일에 perks 배열이 없습니다: {}", file);
			return;
		}

		JsonArray array = perksElement.getAsJsonArray();
		int skipped = 0;
		for (JsonElement element : array) {
			if (element == null || !element.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 항목이 객체가 아니라 건너뜁니다: {}", element);
				skipped++;
				continue;
			}
			Perk perk = parsePerk(element.getAsJsonObject());
			if (perk == null) {
				skipped++;
				continue;
			}
			if (PERKS.putIfAbsent(perk.id(), perk) != null) {
				SharedFateMod.LOGGER.warn("증강 id 가 중복돼 나중 것을 버립니다: {}", perk.id());
				skipped++;
			}
		}
		SharedFateMod.LOGGER.info("증강 {}개를 읽었습니다 (건너뜀 {}개)", PERKS.size(), skipped);
		warnIfCountDiffersFromBundle(file);
	}

	/**
	 * 번들 기본 증강 풀의 개수와 실제로 읽은 개수를 견줘, 다르면 경고를 남긴다.
	 *
	 * <p>{@code config/sharedfate-perks.json} 은 한 번 만들어지면 판이 올라가도 절대 다시
	 * 덮어써지지 않는다 — {@link #load} 의 {@code Files.exists} 검사가 그렇게 짜여 있고, 그건
	 * 운영자가 값을 고쳐 쓸 수 있게 하려는 의도라 옳다. 문제는 그 대가로, 판을 올려 기본 증강이
	 * 늘었는데 옛 파일을 그대로 들고 있어도 "건너뜀 0개" 로 정상처럼 보이는 로그만 남는다는
	 * 것이다. 여기서 개수만 견줘 다르면 경고한다 — <b>막지는 않는다.</b> 운영자가 일부러
	 * 증강을 줄여 쓰는 것일 수도 있어서다. 개수가 같으면 아무 말도 하지 않는다.
	 */
	private static void warnIfCountDiffersFromBundle(Path file) {
		OptionalInt bundled = bundledPerkCount();
		if (!DefaultDefinitionCount.isStale(bundled, PERKS.size())) {
			return;
		}
		SharedFateMod.LOGGER.warn(
				"증강 {}개를 읽었습니다. 이 판의 기본 정의는 {}개입니다 — {} 이 낡았을 수"
						+ " 있습니다. 이 파일은 JAR 안의 기본 정의보다 우선합니다. 값을 직접 고쳐"
						+ " 쓰는 중이 아니라면 {} 와 {} 를 둘 다 지우고 다시 켜십시오 — 하나만"
						+ " 지우면 반쪽이 옛 정의로 읽히는데도 오류가 나지 않습니다.",
				PERKS.size(), bundled.getAsInt(), file, FILE_NAME, PerkSetRegistry.FILE_NAME);
	}

	/**
	 * JAR 안에 번들된 기본 증강 개수. {@code writeBundledDefault} 와 같은 리소스를 한 번 더
	 * 읽어 개수만 센다 — 서버가 뜰 때 한 번뿐이라 비용은 무시해도 된다.
	 *
	 * <p>못 읽거나 파싱에 실패하면 경고를 내리지 않도록 빈 값을 돌려준다. 경고를 내려다 여기서
	 * 새 오류를 만들면 안 된다.
	 */
	private static OptionalInt bundledPerkCount() {
		try (InputStream bundled = PerkRegistry.class.getResourceAsStream("/" + DEFAULT_RESOURCE)) {
			if (bundled == null) {
				return OptionalInt.empty();
			}
			JsonElement root = JsonParser.parseReader(new InputStreamReader(bundled, StandardCharsets.UTF_8));
			if (root == null || !root.isJsonObject()) {
				return OptionalInt.empty();
			}
			JsonElement perksElement = root.getAsJsonObject().get("perks");
			if (perksElement == null || !perksElement.isJsonArray()) {
				return OptionalInt.empty();
			}
			return OptionalInt.of(perksElement.getAsJsonArray().size());
		} catch (Exception error) {
			return OptionalInt.empty();
		}
	}

	/** 증강 하나를 읽는다. 어디 한 군데라도 잘못됐으면 경고를 남기고 null. */
	private static Perk parsePerk(JsonObject json) {
		try {
			String id = PerkEffectType.readString(json, "id");
			if (id == null || id.isBlank()) {
				SharedFateMod.LOGGER.warn("id 가 없는 증강을 건너뜁니다");
				return null;
			}
			id = id.trim();

			PerkRarity rarity = PerkRarity.fromId(PerkEffectType.readString(json, "rarity"));
			if (rarity == null) {
				SharedFateMod.LOGGER.warn("증강 {}: rarity 가 없거나 알 수 없는 값입니다", id);
				return null;
			}

			String name = PerkEffectType.readString(json, "name");
			if (name == null || name.isBlank()) {
				name = id;
			}
			String description = PerkEffectType.readString(json, "description");
			if (description == null) {
				description = "";
			}

			// 특정 구간부터만 후보로 나오게 하는 필드. 안 적으면 0(제한 없음).
			// 음수는 뜻이 없으므로 0으로 접어 둔다.
			int minLevel = Math.max(0, PerkEffectType.readInt(json, "min_level", 0));

			// 세트 유형. 안 적으면 빈 목록(무유형)이고, 그것이 정상이라 경고하지 않는다.
			List<PerkSetType> setTypes = parseSetTypes(id, json);

			// 전제조건. 안 적으면 null(언제나 후보), 적혀 있는데 모르는 값이면 증강을 버린다.
			// "안 적었다"와 "잘못 적었다"를 구분해야 하므로 결과를 두 값으로 받는다.
			Requirement requires = parseRequirement(id, json);
			if (requires.rejected()) {
				return null;
			}

			List<PerkEffect> effects = parseEffects(id, json);
			if (effects == null) {
				return null;
			}

			return new Perk(id, name, description, rarity, parseIcon(id, json), minLevel,
					setTypes, effects, requires.value());
		} catch (Exception error) {
			SharedFateMod.LOGGER.warn("증강 항목을 읽다가 실패해 건너뜁니다", error);
			return null;
		}
	}

	/**
	 * 선택 화면 카드에 그릴 아이템 아이콘을 읽는다.
	 *
	 * <p>{@code icon} 은 있으면 좋은 장식일 뿐이므로 잘못돼 있어도 증강을 버리지 않는다.
	 * 이름이 깨졌거나 존재하지 않는 아이템이면 경고만 남기고 {@code null} 을 돌려주며,
	 * 그때는 화면이 등급별 기본 아이콘을 대신 쓴다.
	 */
	private static @Nullable Identifier parseIcon(String perkId, JsonObject json) {
		String raw = PerkEffectType.readString(json, "icon");
		if (raw == null || raw.isBlank()) {
			return null;
		}
		Identifier id = Identifier.tryParse(raw.trim());
		if (id == null) {
			SharedFateMod.LOGGER.warn("증강 {}: icon 이 아이템 이름 형식이 아닙니다 ({})", perkId, raw);
			return null;
		}
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(id);
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			if (found.isEmpty() || found.get().value() == Items.AIR) {
				SharedFateMod.LOGGER.warn("증강 {}: icon 아이템을 찾을 수 없어 기본 아이콘을 씁니다 ({})",
						perkId, id);
				return null;
			}
		} catch (Exception error) {
			// 레지스트리가 아직 준비되지 않은 상황이라면 굳이 아이콘을 버리지 않는다.
			SharedFateMod.LOGGER.warn("증강 {}: icon {} 을 확인하지 못했습니다", perkId, id, error);
			return id;
		}
		return id;
	}

	/**
	 * {@code requires} 를 읽은 결과.
	 *
	 * <p>{@code null} 하나로는 "안 적었다"와 "적었는데 모르는 값이다"를 구분할 수 없어 두 값으로
	 * 나눠 들고 다닌다.
	 *
	 * @param value    읽어 낸 전제조건. 안 적었으면 {@code null}
	 * @param rejected 적혀 있는데 읽을 수 없어 이 증강을 버려야 하는가
	 */
	private record Requirement(@Nullable Perk.Requirement value, boolean rejected) {
		static final Requirement ABSENT = new Requirement(null, false);
		static final Requirement REJECTED = new Requirement(null, true);
	}

	/**
	 * 증강의 최상위 {@code requires} 필드를 읽는다. {@code min_level} 과 같은 자리다.
	 *
	 * <p><b>모르는 값이면 증강을 통째로 버린다.</b> 세트 유형처럼 조용히 건너뛰면 오타 하나가
	 * 「왜 이 증강이 안 나오지」로 남아 아무도 못 찾는다. 이유는 {@link Perk.Requirement} 문서에
	 * 적어 뒀다.
	 *
	 * <p>필드를 아예 안 적었거나 {@code null} 이면 전제조건이 없는 것이고, 그것이 대부분이라
	 * 아무 말도 하지 않는다.
	 */
	private static Requirement parseRequirement(String perkId, JsonObject json) {
		JsonElement raw = json.get("requires");
		if (raw == null || raw.isJsonNull()) {
			return Requirement.ABSENT;
		}
		String text = PerkEffectType.readString(json, "requires");
		Perk.Requirement requirement = Perk.Requirement.fromId(text);
		if (requirement == null) {
			SharedFateMod.LOGGER.warn("증강 {}: 알 수 없는 requires 라 증강을 버립니다 ({})", perkId, raw);
			return Requirement.REJECTED;
		}
		return new Requirement(requirement, false);
	}

	/**
	 * 세트 유형 목록을 읽는다.
	 *
	 * <p>효과와 달리 <b>잘못돼 있어도 증강을 버리지 않는다.</b> 유형은 세트 판정에만 쓰는
	 * 덧붙임이라, 오타 하나 때문에 증강이 통째로 사라지면 잃는 쪽이 훨씬 크다. 모르는
	 * 문자열은 그 항목만 건너뛰고 경고를 남긴다.
	 *
	 * <p>{@code set_types} 를 아예 안 적은 증강은 무유형이며 이는 정상이다. 무유형이 열여섯
	 * 개나 되므로 그때는 아무 말도 하지 않는다. 같은 유형을 두 번 적으면 한 번만 센다.
	 */
	private static List<PerkSetType> parseSetTypes(String perkId, JsonObject json) {
		List<String> raw = PerkEffectType.readStringList(json, "set_types");
		if (raw == null || raw.isEmpty()) {
			return List.of();
		}
		List<PerkSetType> types = new ArrayList<>(raw.size());
		for (String entry : raw) {
			PerkSetType type = PerkSetType.fromId(entry);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: 알 수 없는 세트 유형이라 그것만 건너뜁니다 ({})",
						perkId, entry);
				continue;
			}
			if (!types.contains(type)) {
				types.add(type);
			}
		}
		return List.copyOf(types);
	}

	/**
	 * 효과 목록을 읽는다. 하나라도 잘못됐으면 증강 전체를 버린다.
	 * 설명은 그대로인데 효과 일부만 빠진 증강은 플레이어를 속이는 셈이기 때문이다.
	 */
	private static List<PerkEffect> parseEffects(String perkId, JsonObject json) {
		JsonElement element = json.get("effects");
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: effects 가 비어 있어 건너뜁니다", perkId);
			return null;
		}

		JsonArray array = element.getAsJsonArray();
		List<PerkEffect> effects = new ArrayList<>(array.size());
		for (int index = 0; index < array.size(); index++) {
			JsonElement raw = array.get(index);
			if (raw == null || !raw.isJsonObject()) {
				SharedFateMod.LOGGER.warn("증강 {}: {}번째 효과가 객체가 아닙니다", perkId, index);
				return null;
			}
			JsonObject effectJson = raw.getAsJsonObject();
			String typeId = PerkEffectType.readString(effectJson, "type");
			PerkEffectType type = PerkEffectType.fromId(typeId);
			if (type == null) {
				SharedFateMod.LOGGER.warn("증강 {}: 알 수 없는 효과 type 입니다 ({})", perkId, typeId);
				return null;
			}
			PerkEffect effect = type.create(perkId, index, effectJson);
			if (effect == null) {
				return null;
			}
			// {@code "drawback": true} 가 적혀 있으면 대가로 등록한다. 안 적혀 있으면 아무 일도
			// 하지 않는다.
			PerkDrawbacks.mark(perkId, effectJson, effect);
			effects.add(effect);
		}
		return effects;
	}
}
