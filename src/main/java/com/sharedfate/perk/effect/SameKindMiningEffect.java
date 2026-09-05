package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 블록을 캐면 바로 옆의 <b>같은 종류</b> 블록이 {@value #DEFAULT_EXTRA_BLOCKS}개 함께 캐진다.
 * 대신 쓰는 도구가 그만큼 더 닳는다.
 *
 * <p>정의는 {@code { "type": "same_kind_mining" }} 하나면 충분하고, 값을 손보고 싶으면
 * {@code "extra"}(더 캐는 개수)와 {@code "extraDurability"}(추가 내구도 소모)를 적는다.
 * 세트 「채굴 4단계 — 곡괭이가 알아서」가 쓴다.
 *
 * <h2>「같은 종류」의 뜻 — 블록 종류만 본다</h2>
 * <p>판정은 {@link #isSameKind} 한 줄이고, 규칙은 <b>{@code BlockState} 가 아니라 그 상태가
 * 딛고 있는 {@code Block} 이 같은 것인가</b>다. 세 가지가 여기서 갈린다.
 *
 * <ul>
 *   <li><b>블록 상태(속성)는 보지 않는다.</b> 눕혀 놓은 참나무 원목과 세워 둔 참나무 원목은
 *       같은 종류다. 상태까지 맞춰야 한다고 하면 이 효과는 <b>조용히 아무 일도 하지 않는</b>
 *       자리가 너무 많아진다 — 원목의 {@code axis}, 계단의 {@code facing}·{@code shape},
 *       무엇보다 <b>레드스톤 광석의 {@code lit}</b> 이 그렇다. 레드스톤 광석은 사람이 밟거나
 *       치기만 해도 {@code lit=true} 로 바뀌므로, 상태까지 따지면 방금 캔 광석과 바로 옆
 *       광석이 "다른 종류"가 되어 광맥 한복판에서 효과가 사라진다. 플레이어가 눈으로 보는
 *       "같은 블록"은 언제나 종류이지 상태가 아니다.</li>
 *   <li><b>딥슬레이트 변종은 다른 종류다.</b> {@code minecraft:diamond_ore} 와
 *       {@code minecraft:deepslate_diamond_ore} 는 서로 다른 {@code Block} 이므로 함께 캐지지
 *       않는다. 둘을 묶으려면 "이것과 이것은 사실 같다"는 표를 코드에 들고 있어야 하는데,
 *       그 표는 광석 여덟 종에서 끝나지 않는다 — 돌/화강암, 구리 블록의 산화 4단계, 다른
 *       모드가 넣은 블록까지 누군가 계속 손으로 채워야 하고, 빠뜨린 항목은 아무 경고 없이
 *       "안 걸리는 블록"이 된다. <b>표가 없는 규칙 하나</b>가 표가 있는 규칙보다 설명하기도
 *       쉽고 틀릴 구석도 없다. 실제로도 다이아 광석은 전부 딥슬레이트 층에서 나므로 두 변종이
 *       한 광맥에 섞이는 일 자체가 드물다.</li>
 *   <li><b>전리품이 같은지도 보지 않는다.</b> 원석 구리와 구리 광석은 같은 것을 떨어뜨리지만
 *       같은 종류가 아니다. 위와 같은 이유다.</li>
 * </ul>
 *
 * <h2>「메아리 채굴」과 무엇이 다른가</h2>
 * <p>{@link EchoMiningEffect} 는 이웃 26칸에서 <b>종류를 가리지 않고</b> 2개를 더 캔다. 이쪽은
 * 같은 26칸에서 <b>같은 종류만</b> 고른다. 훑는 범위와 지켜야 할 규칙(발밑·미로드 청크·도구
 * 등급)은 완전히 같고, 후보를 거르는 마지막 조건 하나만 다르다.
 *
 * <p>둘을 <b>같이 가지고 있으면 둘 다 발동한다.</b> 합쳐서 최대 4칸이고 내구도도 각각 문다.
 * 한쪽을 죽이지 않는 이유와 겹칠 때의 순서는
 * {@link com.sharedfate.perk.PerkBlockBreaks#trySameKindMining} 에 적어 뒀다.
 *
 * <h2>무한 연쇄를 막는 방법</h2>
 * <p>{@link EchoMiningEffect} 와 똑같이 {@code ServerLevel.removeBlock} 으로 지운다. 이 메서드는
 * {@code PlayerBlockBreakEvents.AFTER} 를 다시 발화시키지 않는다. 그 위에
 * {@link com.sharedfate.perk.PerkBlockBreaks#beginChain()} 이 스레드마다 재진입을 한 번 더
 * 막는다 — 「같은 종류」는 이웃이 같은 종류일수록 잘 걸리는 효과라, 만에 하나 연쇄가 열리면
 * 광맥 하나가 아니라 <b>돌밭 전체</b>가 도미노로 무너져 서버가 멈춘다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "몇 개를, 도구를 얼마나 더 닳게 하며, 무엇을 같은 종류로 볼 것인가"만 들고 있는
 * 자료 그릇이다. 실제로 이웃을 훑고 블록을 지우는 일은
 * {@link com.sharedfate.perk.PerkBlockBreaks} 가 맡는다. {@code bonus_drop} 과 같은 구도이며,
 * 그래서 새 mixin 이 필요 없다 — 이미 쓰고 있는 {@code PlayerBlockBreakEvents.AFTER} 한 자리를
 * 그대로 쓴다.
 */
public final class SameKindMiningEffect implements PerkEffect {
	/** {@code extra} 를 안 적었을 때 더 캐는 블록 수. 후보가 모자라면 있는 만큼만 캔다. */
	public static final int DEFAULT_EXTRA_BLOCKS = 2;

	/**
	 * {@code extraDurability} 를 안 적었을 때 추가로 닳는 내구도.
	 *
	 * <p>1 이다. 원래 소모 1점은 바닐라가 뒤이어 처리하므로 합계가 정확히 2배가 된다.
	 * 「메아리 채굴」과 같은 값을 골랐다 — 두 효과가 하는 일(추가 2칸 파괴)이 같은데 한쪽만
	 * 공짜면, 골드 증강을 주고 산 「메아리 채굴」쪽이 손해를 본다.
	 */
	public static final int DEFAULT_EXTRA_DURABILITY = 1;

	/** 한 번에 더 캘 수 있는 개수 상한. 이웃이 26칸뿐이라 그보다 크게 적을 이유가 없다. */
	static final int MAX_EXTRA_BLOCKS = 8;

	/** 추가 내구도 소모 상한. {@link BonusDropEffect} 와 같은 값이다. */
	static final int MAX_EXTRA_DURABILITY = 64;

	private final int extraBlocks;
	private final int extraDurability;

	public SameKindMiningEffect(int extraBlocks, int extraDurability) {
		this.extraBlocks = extraBlocks;
		this.extraDurability = extraDurability;
	}

	/**
	 * JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 {@code null}.
	 *
	 * <p>필수 필드가 없어 {@code { "type": "same_kind_mining" }} 만으로도 만들어진다. 값을
	 * 코드가 아니라 정의 파일에서 조절할 수 있게 두 필드를 열어 두었다 — 세트 보상 값은 자주
	 * 바뀌고, 그때마다 이 파일을 고치게 하면 안 된다.
	 */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int extraBlocks = PerkEffectType.readInt(json, "extra", DEFAULT_EXTRA_BLOCKS);
		if (extraBlocks < 1 || extraBlocks > MAX_EXTRA_BLOCKS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: same_kind_mining 의 extra 값이 1~{} 범위를 벗어났습니다 ({})",
					perkId, MAX_EXTRA_BLOCKS, extraBlocks);
			return null;
		}

		int extraDurability = PerkEffectType.readInt(json, "extraDurability", DEFAULT_EXTRA_DURABILITY);
		if (extraDurability < 0 || extraDurability > MAX_EXTRA_DURABILITY) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: same_kind_mining 의 extraDurability 값이 0~{} 범위를 벗어났습니다 ({})",
					perkId, MAX_EXTRA_DURABILITY, extraDurability);
			return null;
		}
		return new SameKindMiningEffect(extraBlocks, extraDurability);
	}

	/** 한 번 캘 때 덤으로 더 캐지는 블록 수. */
	public int extraBlocks() {
		return Math.max(1, Math.min(MAX_EXTRA_BLOCKS, extraBlocks));
	}

	/** 실제로 하나라도 더 캤을 때 도구를 추가로 닳게 할 양. 몇 개를 캤든 이 값 그대로다. */
	public int extraDurability() {
		return Math.max(0, Math.min(MAX_EXTRA_DURABILITY, extraDurability));
	}

	// ------------------------------------------------------------------ 같은 종류 판정

	/**
	 * 두 블록이 「같은 종류」인가. <b>이 효과의 정의 그 자체이므로 여기 한 곳에만 둔다.</b>
	 *
	 * <p>{@code BlockState} 를 통째로 비교하지 않고 {@code getBlock()} 만 본다. 왜 그렇게
	 * 정했는지는 이 클래스 맨 위에 적어 뒀다. 요약하면 <b>블록 상태가 달라도 같은 종류이고,
	 * 딥슬레이트 변종은 다른 종류</b>다.
	 *
	 * <p>레지스트리만 읽는 순수 판정이라 살아 있는 서버 없이 그대로 시험할 수 있다.
	 *
	 * @param origin    방금 캔 블록의 상태
	 * @param candidate 함께 캘지 따져 보는 이웃 칸의 상태
	 * @return 둘 중 하나라도 {@code null} 이면 {@code false}
	 */
	public static boolean isSameKind(@Nullable BlockState origin, @Nullable BlockState candidate) {
		if (origin == null || candidate == null) {
			return false;
		}
		return origin.getBlock() == candidate.getBlock();
	}
}
