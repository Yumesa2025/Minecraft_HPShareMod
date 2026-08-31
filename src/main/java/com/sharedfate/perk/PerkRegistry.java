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
	 * <p>서버 운영자가 빈 파일부터 손으로 채우게 두면 서버마다 증강이 달라진다. 기본 풀을
	 * 함께 배포하고 첫 실행 때 꺼내 두면, 그대로 써도 되고 편집해도 된다.
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

			// stackable 과 maxStacks 는 더 이상 읽지 않는다. 중첩 개념이 사라졌기 때문이다.
			// 예전 형식으로 적어 둔 파일이 그대로 열려야 하므로 남아 있어도 그냥 지나친다.

			// 특정 구간부터만 후보로 나오게 하는 필드. 안 적으면 0(제한 없음).
			// 음수는 뜻이 없으므로 0으로 접어 둔다.
			int minLevel = Math.max(0, PerkEffectType.readInt(json, "min_level", 0));

			List<PerkEffect> effects = parseEffects(id, json);
			if (effects == null) {
				return null;
			}

			return new Perk(id, name, description, rarity, parseIcon(id, json), minLevel, effects);
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
			effects.add(effect);
		}
		return effects;
	}
}
