package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 프리즘 「살기」. 팀원 주위의 적대적 몹이 가만히 있어도 초당 피해를 받는다.
 *
 * <pre>{@code
 * { "type": "aura_damage", "radius": 10, "damage_per_second": 2,
 *   "excluded_dimensions": ["minecraft:the_nether", "minecraft:the_end"] }
 * }</pre>
 *
 * <h2>필드</h2>
 * <ul>
 *   <li>{@code radius} — 살기가 닿는 반경(칸). 안 적으면 {@value #DEFAULT_RADIUS} 다.
 *       {@value #MIN_RADIUS}~{@value #MAX_RADIUS} 를 벗어나면 경고만 남기고 그 범위로 자른다.</li>
 *   <li>{@code damage_per_second} — 팀원 <b>한 명당</b> 1초에 넣는 피해. 안 적으면
 *       {@value #DEFAULT_DAMAGE_PER_SECOND} 다. {@value #MIN_DAMAGE_PER_SECOND}~
 *       {@value #MAX_DAMAGE_PER_SECOND} 를 벗어나면 역시 잘라 쓴다.</li>
 *   <li>{@code excluded_dimensions} — 이 차원에 서 있는 팀원은 살기를 뿜지 않는다.
 *       <b>생략할 수 있고, 생략하면 어디서나 작동한다.</b> 이름을 읽을 수 없는 항목은 경고만
 *       남기고 그 항목만 버린다.</li>
 * </ul>
 *
 * <p>{@code radius}·{@code damagePerSecond}·{@code excludedDimensions} 처럼 카멜케이스로 적어도
 * 같게 읽는다. 다른 효과들과 같은 규칙이다.
 *
 * <h2>겹친 인원만큼 곱해지되, 한 번에 들어간다</h2>
 * <p>바닐라는 피격 뒤 무적 시간(10틱)이 있어서 네 명이 각자 2씩 때리면 8이 아니라 <b>2</b>만
 * 들어간다. 그래서 {@link com.sharedfate.sync.AuraDamageManager} 는 몹 하나마다 반경 안에 있는
 * 팀원 수를 먼저 세고, {@link #stackedDamage(int)} 로 구한 <b>합계 한 방</b>을 넣는다. 「팀원이
 * 모이면 살기가 겹친다」는 설계가 성립하는 것은 이 셈 덕분이다.
 *
 * <h2>차원 제외는 몹이 아니라 팀원을 본다</h2>
 * <p>{@link #excludes(ResourceKey)} 에 넘기는 것은 <b>살기를 뿜는 팀원이 서 있는 차원</b>이다.
 * 살기는 팀원에게서 뻗어 나가는 것이므로 몹이 어디서 왔는지는 상관이 없다. 어차피 반경 안의
 * 몹은 그 팀원과 같은 차원에 있다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 「얼마나 넓게, 얼마나 세게, 어디서 빼는가」만 들고 있는 자료 그릇이다. 몹을 찾고
 * 인원을 세고 실제로 때리는 일은 전부 {@link com.sharedfate.sync.AuraDamageManager} 가 맡는다.
 */
public final class AuraDamageEffect implements PerkEffect {
	/** 살기가 닿는 기본 반경(칸). */
	public static final int DEFAULT_RADIUS = 10;
	/** 반경 하한. 이보다 좁으면 몸에 닿을 만큼 붙어야 해서 있으나 마나다. */
	public static final int MIN_RADIUS = 2;
	/**
	 * 반경 상한.
	 *
	 * <p>반경이 넓어지면 훑어야 하는 구역이 세제곱으로 늘어난다. 32칸이면 청크 두 개 남짓이라
	 * 1초에 한 번 도는 조회로 감당할 수 있는 마지막 선이다.
	 */
	public static final int MAX_RADIUS = 32;

	/** 팀원 한 명이 1초에 넣는 기본 피해. 하트 한 칸이다. */
	public static final int DEFAULT_DAMAGE_PER_SECOND = 2;
	public static final int MIN_DAMAGE_PER_SECOND = 1;
	/**
	 * 초당 피해 상한.
	 *
	 * <p>인원수만큼 곱해지므로 네 명이 모이면 이미 초당 40이다. 그 위는 서 있기만 해도 웬만한
	 * 몹이 즉사해 전투가 사라진다.
	 */
	public static final int MAX_DAMAGE_PER_SECOND = 10;

	private final int radius;
	private final int damagePerSecond;
	private final Set<ResourceKey<Level>> excludedDimensions;

	public AuraDamageEffect(int radius, int damagePerSecond,
			Set<ResourceKey<Level>> excludedDimensions) {
		this.radius = radius;
		this.damagePerSecond = damagePerSecond;
		this.excludedDimensions = Set.copyOf(excludedDimensions);
	}

	/**
	 * JSON에서 만든다.
	 *
	 * <p>값이 범위를 벗어나도 정의를 버리지 않는다. 반경 하나가 틀렸다고 프리즘 증강 전체가
	 * 사라지는 쪽이 손해가 크다. {@link RallyShardEffect#fromJson} 과 같은 정책이다.
	 */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int radius = clamp(perkId, "radius",
				PerkEffectType.readInt(json, "radius", DEFAULT_RADIUS),
				MIN_RADIUS, MAX_RADIUS);
		int damage = clamp(perkId, "damage_per_second",
				PerkEffectType.readInt(json, damageKey(json), DEFAULT_DAMAGE_PER_SECOND),
				MIN_DAMAGE_PER_SECOND, MAX_DAMAGE_PER_SECOND);
		return new AuraDamageEffect(radius, damage, readExcludedDimensions(perkId, json));
	}

	/** 카멜케이스로 적어도 읽어 준다. {@code radius} 는 한 낱말이라 갈릴 일이 없다. */
	private static String damageKey(JsonObject json) {
		return json != null && json.has("damagePerSecond") ? "damagePerSecond" : "damage_per_second";
	}

	private static String excludedKey(JsonObject json) {
		return json != null && json.has("excludedDimensions")
				? "excludedDimensions" : "excluded_dimensions";
	}

	/**
	 * {@code excluded_dimensions} 를 읽는다.
	 *
	 * <p>필드가 아예 없으면 빈 집합이다 — 어디서나 작동한다는 뜻이다. 이름을 읽을 수 없는
	 * 항목은 경고만 남기고 그 항목만 버린다. 오타 하나로 증강이 통째로 사라지지 않게 한다.
	 */
	private static Set<ResourceKey<Level>> readExcludedDimensions(String perkId, JsonObject json) {
		List<String> raw = PerkEffectType.readStringList(json, excludedKey(json));
		if (raw == null || raw.isEmpty()) {
			return Set.of();
		}
		Set<ResourceKey<Level>> found = new LinkedHashSet<>(raw.size());
		for (String entry : raw) {
			Identifier id = Identifier.tryParse(entry);
			if (id == null) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: aura_damage 의 excluded_dimensions 에 올바르지 않은 차원 이름이 있어 "
								+ "그 항목만 버립니다 ({})", perkId, entry);
				continue;
			}
			found.add(ResourceKey.create(Registries.DIMENSION, id));
		}
		return Set.copyOf(found);
	}

	private static int clamp(String perkId, String key, int value, int min, int max) {
		if (value >= min && value <= max) {
			return value;
		}
		int cut = Math.max(min, Math.min(max, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: aura_damage 의 {} 가 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, key, min, max, cut, value);
		return cut;
	}

	/** 살기가 닿는 반경(칸). */
	public int radius() {
		return radius;
	}

	/** 반경의 제곱. 거리 비교에 제곱근을 뽑지 않으려고 미리 내어 둔다. */
	public double radiusSquared() {
		return (double) radius * radius;
	}

	/** 팀원 한 명이 1초에 넣는 피해. */
	public int damagePerSecond() {
		return damagePerSecond;
	}

	/** 살기를 뿜지 않는 차원들. 비어 있으면 어디서나 작동한다. */
	public Set<ResourceKey<Level>> excludedDimensions() {
		return excludedDimensions;
	}

	/**
	 * 이 차원에 서 있는 팀원은 살기를 뿜지 않는가.
	 *
	 * <p>넘기는 것은 <b>몹이 아니라 팀원이 서 있는 차원</b>이다.
	 */
	public boolean excludes(@Nullable ResourceKey<Level> dimension) {
		return dimension != null && excludedDimensions.contains(dimension);
	}

	/**
	 * 몹 하나에게 한 번에 넣을 피해.
	 *
	 * <p>{@code 초당 피해 × 반경 안에 있는 팀원 수} 다. 나누어 넣으면 무적 시간에 먹혀 한 명분만
	 * 들어가므로, 부르는 쪽은 반드시 이 값 하나를 한 번에 넣어야 한다.
	 *
	 * @param memberCount 그 몹의 반경 안에 있는 팀원 수. 0 이하면 0
	 */
	public float stackedDamage(int memberCount) {
		return memberCount <= 0 ? 0.0F : (float) damagePerSecond * memberCount;
	}
}
