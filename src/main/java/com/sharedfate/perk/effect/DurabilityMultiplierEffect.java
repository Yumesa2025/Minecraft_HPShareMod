package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import com.sharedfate.perk.PerkItemMatcher;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 정해진 아이템 무리가 <b>천천히 닳게</b> 만드는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * {
 *   "type": "durability_multiplier",
 *   "tags": ["sharedfate:netherite_gear"],
 *   "multiplier": 3.0
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code tags}/{@code items} — 오래 쓰게 해 줄 아이템 무리.</li>
 *   <li>{@code multiplier} — 내구도가 몇 배로 늘어난 것처럼 굴 것인가. 3.0 이면 세 번 닳을 것을
 *       한 번만 닳는다.</li>
 * </ul>
 *
 * <h2>내구도를 늘리는 게 아니라 닳는 쪽을 줄인다</h2>
 * <p>아이템의 {@code minecraft:max_damage} 컴포넌트는 건드리지 않고, 닳는 지점에서 소모량만
 * 줄인다. 아이템에는 아무 흔적도 남지 않는다.
 *
 * <h2>증강을 잃으면 그 즉시 원래대로 돌아간다</h2>
 * <p>이 효과는 상태를 남기지 않는다. 내구도가 닳을 때마다
 * {@link com.sharedfate.perk.PerkGearRules#durabilityMultiplier} 에게 "지금 이 팀이 이 배수를
 * 갖고 있는가"를 새로 물어볼 뿐이다. 회차 리셋·팀 해체·증강 상실 어느 쪽이든 다음 한 번부터
 * 바닐라와 똑같이 닳는다. <b>이미 아껴 둔 내구도는 그대로 남는다</b> — 증강을 갖고 있던 동안
 * 덜 닳은 것이지 없던 내구도를 빌려 온 것이 아니기 때문이다.
 *
 * <p>대신 도구 설명에 적히는 최대 내구도는 바닐라 그대로다. 막대가 천천히 줄어드는 것으로만
 * 보인다.
 *
 * <p>실제로 소모량을 깎는 자리는 {@code ItemStackDurabilityMixin} 이고, 계산은
 * {@link com.sharedfate.perk.PerkGearRules#reduceDurabilityLoss} 한 곳에만 있다.
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다.
 */
public final class DurabilityMultiplierEffect implements PerkEffect {
	/** 배수 상한. 사실상 안 닳는 장비를 실수로 만들지 않게 둔다. */
	public static final double MAX_MULTIPLIER = 64.0;

	private final PerkItemMatcher matcher;
	private final double multiplier;

	public DurabilityMultiplierEffect(PerkItemMatcher matcher, double multiplier) {
		this.matcher = matcher;
		this.multiplier = multiplier;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		PerkItemMatcher matcher = PerkItemMatcher.fromJson(perkId, "durability_multiplier", json);
		if (matcher == null) {
			return null;
		}

		Double raw = PerkEffectType.readDouble(json, "multiplier");
		if (raw == null || raw <= 1.0 || raw > MAX_MULTIPLIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: durability_multiplier 의 multiplier 가 1 초과 {} 이하가 아닙니다 ({})",
					perkId, MAX_MULTIPLIER, raw);
			return null;
		}
		return new DurabilityMultiplierEffect(matcher, raw);
	}

	/** 이 아이템이 오래 쓰는 무리에 들어가는가. */
	public boolean matches(@Nullable ItemStack stack) {
		return matcher.matches(stack);
	}

	public PerkItemMatcher matcher() {
		return matcher;
	}

	/** 내구도가 몇 배가 된 것처럼 구는가. */
	public double multiplier() {
		return multiplier;
	}
}
