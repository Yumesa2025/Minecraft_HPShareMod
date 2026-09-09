package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkBlessingSet;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.TeamState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 정해진 아이템을 들고 때렸을 때 넉백을 통째로 갈아 끼우는 「몽둥이찜질」.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "weapon_knockback", "item": "minecraft:stick", "knockback": 10.0,
 *   "exclude_players": true }
 * </pre>
 *
 * <ul>
 *   <li>{@code item} — 이 넉백이 걸리는 아이템 하나. 반드시 적어야 하고, 이름 모양이 틀렸거나
 *       26.2 에 없는 이름이면 정의를 버린다({@code null} 반환).</li>
 *   <li>{@code knockback} — 세기. <b>넉백 인챈트의 레벨과 같은 눈금</b>이다(아래 참고).
 *       {@link #MIN_KNOCKBACK}~{@link #MAX_KNOCKBACK} 를 벗어나면 경고만 남기고 자른다.</li>
 *   <li>{@code exclude_players} — 참이면 플레이어를 때릴 때는 걸리지 않는다. 적지 않으면 참이다.</li>
 * </ul>
 *
 * <h2>{@code knockback} 의 눈금</h2>
 * <p>26.2 의 {@code LivingEntity.getKnockback(Entity, DamageSource)} 는 바이트코드상
 * {@code EnchantmentHelper.modifyKnockback(level, weapon, target, source, 속성값) / 2} 를
 * 돌려주고, {@code Player.attack} 은 그 값에 질주 보정(0.5)을 더해
 * {@code causeExtraKnockback} 에 넘긴다. 바닐라 넉백 인챈트는
 * {@code data/minecraft/enchantment/knockback.json} 에서 레벨당 1 을 <b>속성 뒤에</b> 더하므로,
 * 「넉백 N 짜리 막대기」의 최종값은 정확히 {@code N / 2} 다. 그래서 이 정의의
 * {@code knockback: 10} 은 넉백 10 인챈트와 같은 세기를 뜻하고, 실제로 돌려주는 값은
 * {@link #attackKnockback()} 인 5.0 이다.
 *
 * <h2>왜 {@code minecraft:attack_knockback} 속성을 쓰지 않는가</h2>
 * <p>두 가지 이유로 못 쓴다.
 *
 * <ol>
 *   <li><b>상한이 5 다.</b> 26.2 의 {@code Attributes} 는 {@code attack_knockback} 을
 *       {@code RangedAttribute(기본 0, 최소 0, 최대 5)} 로 등록한다. 속성에 10 을 넣어도 5 로
 *       잘려 최종 세기가 2.5 가 되므로 「넉백 10」이 되지 않는다.</li>
 *   <li><b>맞는 쪽을 모른다.</b> 속성은 때리는 사람에게 붙는 값이라 지금 때리는 대상이
 *       플레이어인지 몹인지 알 수 없다. {@code exclude_players} 를 지킬 방법이 없다.</li>
 * </ol>
 *
 * <p>그래서 {@code getKnockback} 이 돌려주는 값 자체를 갈아 끼운다. 그 자리는 이미 이 모드가
 * 잡고 있는 {@code LivingEntity} 진입점이라 새 mixin 이 필요 없다 —
 * {@code LivingEntityPerkDamageMixin} 참고. 원래 값보다 <b>큰 경우에만</b> 갈아 끼우므로,
 * 넉백 인챈트가 이미 더 세게 붙어 있으면 그쪽이 그대로 이긴다.
 *
 * <h2>고른 사람 한 명만</h2>
 * <p>{@link DamageWardEffect} 와 같다. {@code TeamState.perkOwners} 에 주인으로 적힌 사람이
 * 때릴 때만 걸리고, 고른 사람이라는 개념이 없는 세트 효과는 훑지 않는다.
 */
public final class WeaponKnockbackEffect implements PerkEffect {
	/** 세기 하한. 0 은 「이 무기로는 넉백이 없다」는 뜻이라 그대로 살려 둔다. */
	public static final double MIN_KNOCKBACK = 0.0;
	/**
	 * {@code knockback} 을 적지 않았을 때의 세기.
	 *
	 * <p>아이템만 맞으면 무엇을 하려는 정의인지는 이미 분명하므로, 숫자 하나가 빠졌다고 증강을
	 * 통째로 버리지 않는다. 값을 조절할 때는 <b>이 상수와 증강 정의 파일의 값이 어긋나지 않게</b>
	 * 같이 봐야 한다.
	 */
	public static final double DEFAULT_KNOCKBACK = 10.0;
	/**
	 * 세기 상한.
	 *
	 * <p>넉백 40 이면 맞은 쪽이 화면 밖으로 날아간다. 그 위는 「더 멀리」의 차이를 사람이 느낄
	 * 수 없고 청크 로딩만 괴롭히므로 여기서 자른다.
	 */
	public static final double MAX_KNOCKBACK = 40.0;

	/**
	 * 정의에 적힌 세기를 {@code getKnockback} 이 돌려줄 값으로 바꾸는 나눔수.
	 *
	 * <p>바닐라가 같은 자리에서 2 로 나누기 때문이다. 머리말의 눈금 설명 참고.
	 */
	private static final float VANILLA_HALVING = 2.0F;

	private final Identifier itemId;
	private final float knockback;
	private final float amplifiedKnockback;
	private final boolean excludePlayers;

	/** 레지스트리에서 찾아 둔 아이템. 처음 판정할 때 한 번 찾고 계속 쓴다. */
	private @Nullable Item resolved;
	/** 아이템을 찾아본 적이 있는가. 없는 이름이라도 매번 다시 찾지 않게 한다. */
	private boolean resolveTried;

	public WeaponKnockbackEffect(Identifier itemId, float knockback, boolean excludePlayers) {
		this(itemId, knockback, knockback, excludePlayers);
	}

	/**
	 * @param amplifiedKnockback 「가호 3」이 켜졌을 때의 세기. 안 적으면 평소 세기와 같다
	 */
	public WeaponKnockbackEffect(Identifier itemId, float knockback, float amplifiedKnockback,
			boolean excludePlayers) {
		this.itemId = itemId;
		this.knockback = knockback;
		this.amplifiedKnockback = amplifiedKnockback;
		this.excludePlayers = excludePlayers;
	}

	/**
	 * JSON에서 만든다. 아이템을 알아볼 수 없으면 경고를 남기고 {@code null}.
	 *
	 * <p>세기만 범위를 벗어난 경우에는 정의를 살리고 값만 자른다. 아이템과 달리 숫자 하나는
	 * 고쳐 쓸 수 있기 때문이다.
	 */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		String raw = PerkEffectType.readString(json, "item");
		if (raw == null || raw.isBlank()) {
			SharedFateMod.LOGGER.warn("증강 {}: weapon_knockback 에 item 이 없습니다", perkId);
			return null;
		}
		Identifier itemId = Identifier.tryParse(raw.trim());
		if (itemId == null) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: weapon_knockback 의 아이템 이름 {} 을 읽을 수 없습니다", perkId, raw);
			return null;
		}
		if (missingFromRegistry(itemId)) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: weapon_knockback 이 가리키는 아이템 {} 이 이 판에 없습니다", perkId, itemId);
			return null;
		}

		double knockback = clamp(perkId, readKnockback(perkId, json));
		double amplified = clamp(perkId, readAmplified(json, knockback));
		// 적지 않으면 플레이어를 뺀다. PvP 에서 사람이 날아가는 쪽이 훨씬 큰 사고라, 기본값은
		// 안전한 쪽이어야 한다.
		boolean excludePlayers = readExcludePlayers(json);
		return new WeaponKnockbackEffect(itemId, (float) knockback, (float) amplified,
				excludePlayers);
	}

	/** 세기를 읽는다. 적지 않았으면 기본값, 숫자가 아니면 경고를 남기고 기본값. */
	private static double readKnockback(String perkId, @Nullable JsonObject json) {
		Double raw = PerkEffectType.readDouble(json, "knockback");
		if (raw != null) {
			return raw;
		}
		if (json != null && json.has("knockback")) {
			// 숫자가 아닌 값을 적어 둔 경우다. 조용히 기본값으로 넘어가면 오타를 못 잡는다.
			SharedFateMod.LOGGER.warn(
					"증강 {}: weapon_knockback 의 knockback 이 숫자가 아니라 기본값 {} 을 씁니다",
					perkId, DEFAULT_KNOCKBACK);
		}
		return DEFAULT_KNOCKBACK;
	}

	/**
	 * 「가호 3」의 강화 세기를 읽는다. 안 적었으면 평소 세기와 같다.
	 *
	 * <p>카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다.
	 */
	private static double readAmplified(@Nullable JsonObject json, double fallback) {
		if (json == null) {
			return fallback;
		}
		String key = json.has("amplifiedKnockback") ? "amplifiedKnockback" : "amplified_knockback";
		Double raw = PerkEffectType.readDouble(json, key);
		return raw == null ? fallback : raw;
	}

	private static boolean readExcludePlayers(@Nullable JsonObject json) {
		if (json == null) {
			return true;
		}
		String key = json.has("excludePlayers") ? "excludePlayers" : "exclude_players";
		if (!json.has(key) || !json.get(key).isJsonPrimitive()
				|| !json.get(key).getAsJsonPrimitive().isBoolean()) {
			return true;
		}
		return json.get(key).getAsBoolean();
	}

	private static double clamp(String perkId, double value) {
		if (value >= MIN_KNOCKBACK && value <= MAX_KNOCKBACK) {
			return value;
		}
		// NaN 은 어느 비교도 참이 되지 않아 위 검사를 그대로 통과하지 못한다. 자를 수도 없으므로
		// 기본값으로 물러난다.
		double cut = Double.isNaN(value) ? DEFAULT_KNOCKBACK
				: Math.max(MIN_KNOCKBACK, Math.min(MAX_KNOCKBACK, value));
		SharedFateMod.LOGGER.warn(
				"증강 {}: weapon_knockback 의 knockback 이 {}~{} 범위를 벗어나 {} 로 자릅니다 ({})",
				perkId, MIN_KNOCKBACK, MAX_KNOCKBACK, cut, value);
		return cut;
	}

	/**
	 * 이 이름이 아이템 레지스트리에 없는가.
	 *
	 * <p>레지스트리가 <b>아직 채워지지 않았으면 없다고 보지 않는다.</b> 증강 정의는 모드가 뜰 때
	 * 읽는데 그 시점에는 바닐라 부트스트랩이 끝나기 전일 수 있고, 그때 「없다」로 판정하면 멀쩡한
	 * 정의가 통째로 사라진다. 그런 경우에는 판정을 처음 쓸 때({@link #matches})로 미룬다 —
	 * {@code PerkItemMatcher}·{@code MobPerkModifiers.Targets} 와 같은 대비다.
	 */
	private static boolean missingFromRegistry(Identifier itemId) {
		try {
			if (BuiltInRegistries.ITEM.keySet().isEmpty()) {
				return false;
			}
			return !BuiltInRegistries.ITEM.containsKey(itemId);
		} catch (RuntimeException error) {
			return false;
		}
	}

	/** 정의에 적힌 아이템 이름. */
	public Identifier itemId() {
		return itemId;
	}

	/** 정의에 적힌 세기. 넉백 인챈트의 레벨과 같은 눈금이다. */
	public float knockback() {
		return knockback;
	}

	/** 「가호 3」이 켜졌을 때의 세기. 안 적으면 {@link #knockback()} 과 같다. */
	public float amplifiedKnockback() {
		return amplifiedKnockback;
	}

	/**
	 * {@code LivingEntity.getKnockback} 이 돌려줄 값.
	 *
	 * <p>바닐라가 같은 자리에서 2 로 나누므로 정의에 적힌 세기의 절반이다.
	 */
	public float attackKnockback() {
		return attackKnockback(false);
	}

	/**
	 * 같은 값을 「가호 3」 여부와 함께 묻는다.
	 *
	 * @param amplified 참이면 강화 세기를 쓴다
	 */
	public float attackKnockback(boolean amplified) {
		return (amplified ? amplifiedKnockback : knockback) / VANILLA_HALVING;
	}

	/** 플레이어를 때릴 때는 걸리지 않는가. */
	public boolean excludePlayers() {
		return excludePlayers;
	}

	/** 지금 든 것이 이 정의가 가리키는 아이템인가. */
	public boolean matches(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = resolve();
		return item != null && stack.getItem() == item;
	}

	private @Nullable Item resolve() {
		if (resolveTried) {
			return resolved;
		}
		resolveTried = true;
		try {
			Optional<Holder.Reference<Item>> found = BuiltInRegistries.ITEM.get(itemId);
			// 아이템 레지스트리는 기본값이 공기라 없는 이름도 공기로 돌아올 수 있다.
			if (found.isEmpty() || found.get().value() == Items.AIR) {
				SharedFateMod.LOGGER.warn(
						"weapon_knockback 이 가리키는 아이템을 찾을 수 없어 넉백이 걸리지 않습니다: {}", itemId);
				return null;
			}
			resolved = found.get().value();
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("아이템 {} 을 찾다가 실패했습니다", itemId, error);
		}
		return resolved;
	}

	/**
	 * 이 사람이 지금 이것을 들고 저것을 때릴 때 걸릴 넉백. 걸릴 것이 없으면 음수.
	 *
	 * <p>{@link DamageWardEffect#wardFor} 와 같은 규칙으로 훑는다. 팀이 없거나, 증강을 껐거나,
	 * 아직 아무 증강도 없으면 곧바로 음수다. {@code perkOwners} 에 주인으로 적힌 사람의 증강만
	 * 세고, 고른 사람이라는 개념이 없는 세트 효과는 훑지 않는다.
	 *
	 * <p>살아 있는 월드를 읽지 않는 순수 판정이다. 실제로 무엇을 들었는지와 무엇을 때리는지를
	 * 읽는 일은 {@link com.sharedfate.perk.PerkDamage#weaponKnockback} 이 한다.
	 *
	 * @param state         때리는 사람의 팀 상태
	 * @param attacker      때리는 사람의 UUID
	 * @param held          때리는 사람이 주 손에 든 묶음
	 * @param targetIsPlayer 맞는 쪽이 플레이어인가
	 * @return {@code getKnockback} 이 돌려줄 값. 걸릴 것이 없으면 {@code -1}
	 */
	public static float strengthFor(@Nullable TeamState state, @Nullable UUID attacker,
			@Nullable ItemStack held, boolean targetIsPlayer) {
		if (state == null || attacker == null || !state.perksEnabled
				|| state.ownedPerks.isEmpty() || held == null || held.isEmpty()) {
			return -1.0F;
		}
		float best = -1.0F;
		for (String perkId : state.ownedPerks) {
			// 「가호 4」가 켜지면 고른 사람이 아니어도 걸린다. 판정은 PerkBlessingSet 한 곳이다.
			if (!PerkBlessingSet.appliesTo(state, perkId, attacker)) {
				continue;
			}
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			best = Math.max(best, strengthOf(perk.effects(), held, targetIsPlayer,
					PerkBlessingSet.isAmplified(state, perkId)));
		}
		return best;
	}

	/**
	 * 이 증강을 이 사람이 골랐는가.
	 *
	 * <p>{@code perkOwners} 는 증강 id → 고른 사람의 UUID 다. 적혀 있지 않은 증강(다른 증강이
	 * 덤으로 준 것)은 주인이 {@code null} 이라 누구와도 같지 않다. 팀 상태 하나만 보는 순수
	 * 판정이라 살아 있는 월드 없이 시험할 수 있다.
	 */
	public static boolean chosenBy(@Nullable TeamState state, @Nullable String perkId,
			@Nullable UUID player) {
		return state != null && player != null && player.equals(state.perkOwners.get(perkId));
	}

	/**
	 * 효과 목록에서 이 상황에 걸리는 가장 센 넉백. 걸릴 것이 없으면 음수.
	 *
	 * <p>레지스트리도 팀도 보지 않는 순수 계산이다. 다만 아이템 판정({@link #matches})은
	 * 아이템 레지스트리를 한 번 읽는다.
	 */
	public static float strengthOf(@Nullable Iterable<PerkEffect> effects, @Nullable ItemStack held,
			boolean targetIsPlayer) {
		return strengthOf(effects, held, targetIsPlayer, false);
	}

	/**
	 * 같은 계산을 「가호 3」 여부와 함께 한다.
	 *
	 * @param amplified 참이면 각 정의의 강화 세기를 쓴다
	 */
	public static float strengthOf(@Nullable Iterable<PerkEffect> effects, @Nullable ItemStack held,
			boolean targetIsPlayer, boolean amplified) {
		if (effects == null) {
			return -1.0F;
		}
		float best = -1.0F;
		for (PerkEffect effect : effects) {
			if (!(effect instanceof WeaponKnockbackEffect rule)) {
				continue;
			}
			if (rule.excludePlayers && targetIsPlayer) {
				continue;
			}
			if (!rule.matches(held)) {
				continue;
			}
			best = Math.max(best, rule.attackKnockback(amplified));
		}
		return best;
	}
}
