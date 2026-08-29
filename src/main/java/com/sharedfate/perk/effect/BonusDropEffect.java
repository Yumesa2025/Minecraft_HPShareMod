package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.BlockSelector;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 정해진 블록을 캘 때 확률로 드롭을 하나 더 준다.
 *
 * <p>예: {@code { "type": "bonus_drop", "chance": 0.15, "blocks": ["#c:ores"],
 * "extraDurability": 1 }} 는 광석을 캘 때 15% 확률로 하나 더 떨어뜨리고, 성공한 그 순간에만
 * 손에 든 도구를 1 더 닳게 한다. 실버 6 욕심 많은 곡괭이가 이 타입을 쓴다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "얼마의 확률로, 어떤 블록에서, 도구를 얼마나 더 닳게 하는가"만 들고 있는 자료
 * 그릇이다. 언제 누가 무엇을 캤는지 보고 실제로 아이템을 떨어뜨리는 일은
 * {@link com.sharedfate.perk.PerkBlockBreaks} 가 맡는다. {@code on_kill} 과
 * {@link com.sharedfate.perk.PerkKillRewards} 의 관계와 같은 구도다.
 *
 * <h2>행운·섬세한 손길과 어떻게 겹치는가</h2>
 * <p>추가 드롭은 그 블록의 전리품표를 <b>지금 든 도구로</b> 한 번 더 굴려 나온 것 중 하나를
 * 개수 1 로 잘라 준다. 그래서 행운이 붙어 있으면 바닐라 드롭 쪽이 이미 불어나 있고, 증강은
 * 그 위에 언제나 정확히 +1 을 얹는다. 행운 배수가 증강분에까지 곱해져 눈덩이처럼 커지지
 * 않는다. 섬세한 손길이면 전리품표가 광석 블록 자체를 주므로 추가분도 광석 블록 하나다.
 */
public final class BonusDropEffect implements PerkEffect {
	/** 확률 하한. 0 이면 아무 일도 일어나지 않는 정의라 받아 주지 않는다. */
	static final double MIN_CHANCE = 0.0001;
	/** 확률 상한. 1.0 이면 항상 하나 더다. */
	static final double MAX_CHANCE = 1.0;
	/** 추가 내구도 소모 상한. 한 번 캘 때 도구가 이보다 더 닳으면 도구가 아니라 소모품이다. */
	static final int MAX_EXTRA_DURABILITY = 64;

	private final double chance;
	private final int extraDurability;
	private final BlockSelector blocks;

	public BonusDropEffect(double chance, int extraDurability, BlockSelector blocks) {
		this.chance = chance;
		this.extraDurability = extraDurability;
		this.blocks = blocks == null ? BlockSelector.ALL : blocks;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		Double chance = PerkEffectType.readDouble(json, "chance");
		if (chance == null || chance < MIN_CHANCE || chance > MAX_CHANCE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: bonus_drop 의 chance 값이 없거나 범위를 벗어났습니다 ({})", perkId, chance);
			return null;
		}

		int extraDurability = PerkEffectType.readInt(json, "extraDurability", 0);
		if (extraDurability < 0 || extraDurability > MAX_EXTRA_DURABILITY) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: bonus_drop 의 extraDurability 값이 범위를 벗어났습니다 ({})",
					perkId, extraDurability);
			return null;
		}

		BlockSelector blocks = BlockSelector.fromJson(perkId, "bonus_drop", json);
		if (blocks == null) {
			return null;
		}
		return new BonusDropEffect(chance, extraDurability, blocks);
	}

	/** 이 블록에 걸리는 효과인가. */
	public boolean appliesTo(BlockState state) {
		return blocks.matches(state);
	}

	/** 안전한 범위로 자른 확률. */
	public double chanceFor() {
		double value = chance;
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(MAX_CHANCE, value));
	}

	/** 추가 드롭에 성공했을 때 도구를 더 닳게 할 양. */
	public int extraDurability() {
		return Math.max(0, Math.min(MAX_EXTRA_DURABILITY, extraDurability));
	}

	public double chance() {
		return chance;
	}

	public BlockSelector blocks() {
		return blocks;
	}
}
