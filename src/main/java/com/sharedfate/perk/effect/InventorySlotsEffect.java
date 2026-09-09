package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 팀 공유 인벤토리의 추가 칸을 더 연다.
 *
 * <pre>{@code
 * { "type": "inventory_slots", "amount": 6, "amplified_amount": 9 }
 * }</pre>
 *
 * <p>{@code amount} 는 1 이상 {@link ExpandedInventoryManager#EXTRA_SIZE} 이하로 자른다.
 * 실버 「짐꾼」이 이 타입을 쓴다 — 여섯 칸을 받고, 「가호 3」이 켜지면 아홉 칸이 된다.
 *
 * <h2>칸은 지우는 것이 아니라 잠그는 것이다</h2>
 * <p>메뉴에는 언제나 {@link ExpandedInventoryManager#EXTRA_SIZE} 개의 칸이 만들어져 있고, 잠긴
 * 칸은 화면 밖으로 치워 두고 물건이 들어가지 못하게 막는다. 메뉴의 칸 수가 팀마다 다르면
 * 바닐라의 칸 동기화가 어긋나기 때문이다.
 *
 * <h2>⚠ 칸은 줄어들지 않는다</h2>
 * <p>「가호 4」로 넘어가 강화가 사라지거나 「환골탈태」로 이 증강을 잃으면 계산상 칸 수가 준다.
 * 그런데 <b>그 자리에 물건이 있으면 갈 곳을 잃는다.</b> 그래서 실제 해금 수를 정하는
 * {@code ExpandedInventoryManager} 가 <b>물건이 들어 있는 마지막 칸까지는 반드시 열어 둔다.</b>
 * 이 클래스는 「증강이 얼마를 주는가」만 답한다.
 *
 * <h2>팀에 하나뿐인 값이다</h2>
 * <p>인벤토리는 팀이 공유하므로 이 효과는 사람마다 세지 않는다. 여럿을 가지면 <b>가장 큰
 * 것 하나</b>가 이긴다 — 더하면 같은 증강을 두 번 받았을 때 칸이 두 배로 늘어난다.
 */
public final class InventorySlotsEffect implements PerkEffect {
	/** 값을 안 적었을 때. */
	public static final int DEFAULT_AMOUNT = 6;

	private final int amount;
	private final int amplifiedAmount;

	public InventorySlotsEffect(int amount, int amplifiedAmount) {
		this.amount = amount;
		this.amplifiedAmount = amplifiedAmount;
	}

	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int raw = PerkEffectType.readInt(json, "amount", DEFAULT_AMOUNT);
		int clamped = Math.max(1, Math.min(ExpandedInventoryManager.EXTRA_SIZE, raw));
		if (clamped != raw) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: inventory_slots 의 amount 가 1~{} 범위를 벗어나 {} 로 자릅니다 ({})",
					perkId, ExpandedInventoryManager.EXTRA_SIZE, clamped, raw);
		}
		int rawAmplified = PerkEffectType.readInt(json, amplifiedKey(json), clamped);
		int amplified = Math.max(clamped,
				Math.min(ExpandedInventoryManager.EXTRA_SIZE, rawAmplified));
		return new InventorySlotsEffect(clamped, amplified);
	}

	/** 카멜케이스로 적어도 읽어 준다. 다른 효과들과 같은 규칙이다. */
	private static String amplifiedKey(JsonObject json) {
		return json != null && json.has("amplifiedAmount") ? "amplifiedAmount" : "amplified_amount";
	}

	/** 이 효과가 여는 칸 수. */
	public int amount() {
		return amount;
	}

	/**
	 * 「가호 3」이 켜졌을 때 여는 칸 수. 안 적으면 {@link #amount()} 와 같다.
	 *
	 * <p>기본값보다 작게는 못 적는다 — 강화가 칸을 줄이면 물건이 갈 곳을 잃는다.
	 */
	public int amplifiedAmount() {
		return amplifiedAmount;
	}

	/**
	 * 효과 목록에서 가장 큰 해금 값. 없으면 0.
	 *
	 * <p>더하지 않고 <b>최댓값</b>을 쓰는 이유는 클래스 문서에 있다.
	 */
	public static int bonusOf(@Nullable List<PerkEffect> effects, boolean amplified) {
		if (effects == null || effects.isEmpty()) {
			return 0;
		}
		int best = 0;
		for (PerkEffect effect : effects) {
			if (!(effect instanceof InventorySlotsEffect slots)) {
				continue;
			}
			int value = amplified ? slots.amplifiedAmount() : slots.amount();
			if (value > best) {
				best = value;
			}
		}
		return best;
	}
}
