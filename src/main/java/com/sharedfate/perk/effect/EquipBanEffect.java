package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 정해진 장비 칸을 통째로 못 쓰게 만드는 효과.
 *
 * <p>JSON 형식:
 * <pre>
 * { "type": "equip_ban", "slots": ["head"] }
 * { "type": "equip_ban", "slots": ["armor"] }
 * </pre>
 *
 * <p>{@code slots} 에 적을 수 있는 값은 {@code head}, {@code chest}, {@code legs},
 * {@code feet} 와, 그 넷을 한꺼번에 가리키는 {@code armor} 다. 손 칸({@code mainhand},
 * {@code offhand})은 여기서 막지 않는다. 주 손을 막으면 게임이 성립하지 않고, 왼손은
 * {@link OffhandLockEffect} 가 따로 다룬다.
 *
 * <h2>어디서 걸리는가</h2>
 * <p>이 클래스는 "무엇을 막는가"만 들고 있고 실제 차단은 두 곳에서 일어난다.
 * <ul>
 *   <li>{@code LivingEntityEquipBanMixin} 이 {@code LivingEntity.canUseSlot} 을 거짓으로
 *       만든다. 바닐라가 그 한 메서드를 인벤토리 칸의 활성 여부·배치 가능 여부, 우클릭 착용,
 *       디스펜서 착용에 모두 쓰기 때문에 이 한 곳이면 착용 경로가 전부 막힌다.</li>
 *   <li>{@link com.sharedfate.perk.PerkGearManager} 가 주기적으로 훑어, 증강을 얻기 전에
 *       이미 입고 있던 장비를 벗겨 공유 인벤토리로 보낸다.</li>
 * </ul>
 *
 * <p>{@link #apply}/{@link #remove} 는 아무 일도 하지 않는다. 이 효과는 플레이어에게
 * 붙였다 떼는 것이 아니라 "지금 이 팀이 그 증강을 갖고 있는가"를 그때그때 물어보는 규칙이다.
 * 그래서 증강을 잃으면 물어볼 대상이 사라져 제한이 저절로 풀린다.
 */
public final class EquipBanEffect implements PerkEffect {
	/** {@code armor} 축약이 가리키는 칸들. */
	public static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private final Set<EquipmentSlot> slots;

	public EquipBanEffect(Set<EquipmentSlot> slots) {
		this.slots = slots.isEmpty()
				? EnumSet.noneOf(EquipmentSlot.class) : EnumSet.copyOf(slots);
	}

	/** JSON에서 만든다. 막을 칸이 하나도 없으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		List<String> raw = PerkEffectType.readStringList(json, "slots");
		if (raw == null || raw.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: equip_ban 효과에 slots 배열이 없습니다", perkId);
			return null;
		}

		Set<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
		for (String name : raw) {
			List<EquipmentSlot> parsed = parseSlot(name);
			if (parsed.isEmpty()) {
				SharedFateMod.LOGGER.warn(
						"증강 {}: equip_ban 이 모르는 칸 이름 {} 을 건너뜁니다", perkId, name);
				continue;
			}
			slots.addAll(parsed);
		}

		if (slots.isEmpty()) {
			SharedFateMod.LOGGER.warn("증강 {}: equip_ban 에 쓸 수 있는 칸이 하나도 없습니다", perkId);
			return null;
		}
		return new EquipBanEffect(slots);
	}

	/** 칸 이름 하나를 실제 칸들로 바꾼다. 모르는 이름이면 빈 목록. */
	private static List<EquipmentSlot> parseSlot(String name) {
		return switch (name.trim().toLowerCase(Locale.ROOT)) {
			case "head", "helmet" -> List.of(EquipmentSlot.HEAD);
			case "chest", "chestplate" -> List.of(EquipmentSlot.CHEST);
			case "legs", "leggings" -> List.of(EquipmentSlot.LEGS);
			case "feet", "boots" -> List.of(EquipmentSlot.FEET);
			case "armor", "all" -> ARMOR_SLOTS;
			default -> List.of();
		};
	}

	/** 못 쓰게 된 칸들. */
	public Set<EquipmentSlot> slots() {
		return slots;
	}

	/** 이 칸이 막혔는가. */
	public boolean bans(@Nullable EquipmentSlot slot) {
		return slot != null && slots.contains(slot);
	}
}
