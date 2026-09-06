package com.sharedfate.ui;

/**
 * 화면 아래 바닐라 표시들이 차지하는 높이를 다시 계산한다.
 *
 * <p>핫바 위에는 왼쪽에 하트와 방어구가, 오른쪽에 배고픔·탈것 체력·공기 방울이 쌓인다.
 * 우리 HUD 는 그 위에 그려야 하는데, <b>이 모드는 최대 체력이 팀 설정과 증강으로 바뀌므로</b>
 * 하트가 한 줄일 때도 세 줄일 때도 있다. 높이를 상수로 박으면 반드시 어딘가에서 겹친다.
 *
 * <h2>어디서 가져온 값인가</h2>
 * <p>26.2 의 {@code net.minecraft.client.gui.Hud} 를 그대로 옮겼다. Mixin 없이 같은 규칙을
 * 다시 계산할 뿐이라 바닐라 코드를 건드리지 않는다.
 *
 * <ul>
 *   <li>{@code extractPlayerHealth} — 맨 아랫줄의 윗변이 {@code guiHeight - 39},
 *       줄 수 {@code ceil((최대체력 + 흡수) / 2 / 10)}, 줄 간격 {@code max(10 - (줄수 - 2), 3)}</li>
 *   <li>{@code extractArmor} — 방어구 줄의 윗변이 {@code 아랫줄 - (줄수 - 1) * 줄간격 - 10}</li>
 *   <li>{@code extractHearts} — 하트를 {@code 아랫줄 - 줄번호 * 줄간격} 에 9픽셀 크기로 찍고,
 *       한 줄에 10개씩 놓는다</li>
 *   <li>{@code extractAirBubbles} / {@code getAirBubbleYLine} — 공기 방울은 배고픔(또는 탈것
 *       체력) 위 한 줄</li>
 *   <li>{@code extractVehicleHealth} — 탈것 하트는 간격이 늘 10 으로 고정이다</li>
 * </ul>
 *
 * <p>여기서 돌려주는 값은 모두 <b>화면 아래끝에서 위로 잰 거리</b>다. 화면 높이를 몰라도
 * 된다. 실제 y 는 {@code guiHeight - 여기서_받은_값} 이다.
 */
public final class BottomBarMetrics {
	/** 핫바 왼쪽 끝은 화면 가운데에서 이만큼 왼쪽이다. */
	public static final int HOTBAR_HALF_WIDTH = 91;
	/**
	 * 맨 아랫줄(하트·방어구·배고픔)의 윗변이 화면 아래끝에서 떨어진 거리.
	 *
	 * <p>{@code Hud.extractPlayerHealth} 의 {@code guiHeight - 39} 다.
	 */
	public static final int BOTTOM_ROW = 39;
	/** 한 줄에 놓이는 하트 수({@code Hud.NUM_HEARTS_PER_ROW}). */
	public static final int HEARTS_PER_ROW = 10;
	/** 하트가 한 줄일 때의 줄 간격({@code Hud.LINE_HEIGHT}). */
	public static final int BASE_ROW_SPACING = 10;
	/** 줄이 늘어나도 이보다 촘촘해지지는 않는다. */
	public static final int MIN_ROW_SPACING = 3;
	/** 방어구 줄은 하트 맨 윗줄에서 이만큼 더 올라간다. */
	public static final int ARMOR_ROW_OFFSET = 10;
	/** 공기 방울은 배고픔(또는 탈것 체력) 맨 윗줄에서 이만큼 더 올라간다. */
	public static final int AIR_ROW_OFFSET = 10;
	/** 탈것 하트의 줄 간격. 이쪽은 줄이 늘어도 좁아지지 않는다. */
	public static final int VEHICLE_ROW_SPACING = 10;
	/** 탈것 하트는 30개를 넘지 않는다({@code Hud.getVehicleMaxHearts}). */
	public static final int MAX_VEHICLE_HEARTS = 30;

	/**
	 * 재생 효과가 걸리면 하트 하나가 2픽셀 튀어오른다.
	 *
	 * <p>튀는 하트는 차례로 옮겨 다니므로 맨 윗줄도 언젠가 튄다. 그때만 겹치는 표시는
	 * "가끔 겹치는" 표시라 더 나쁘다. 2픽셀은 늘 비워 둔다.
	 */
	public static final int REGENERATION_LIFT = 2;
	/**
	 * 포만감이 0이면 배고픔 아이콘이 위아래로 1픽셀씩 떨린다.
	 *
	 * <p>{@code extractFood} 의 {@code random.nextInt(3) - 1} 이다. 위로 가는 1픽셀만 센다.
	 */
	public static final int FOOD_SHAKE = 1;

	private BottomBarMetrics() {
	}

	/**
	 * 바닐라가 하트 줄 간격과 방어구 자리를 정할 때 세는 줄 수.
	 *
	 * <p>{@code Hud.extractPlayerHealth} 의 {@code Mth.ceil((최대체력 + 흡수) / 2.0F / 10.0F)}.
	 *
	 * @param maxHealth  최대 체력(하트 한 개가 2)
	 * @param absorption 흡수 체력. 음수면 0으로 본다
	 */
	public static int spacingRows(float maxHealth, float absorption) {
		float total = Math.max(0.0F, maxHealth) + ceil(Math.max(0.0F, absorption));
		// 최대 체력이 0인 플레이어는 없지만, 0이 들어오면 줄 수가 0이 되어 아래 계산이
		// 음수 줄을 세게 된다. 한 줄을 밑바닥으로 둔다.
		return Math.max(1, ceil(total / 2.0F / HEARTS_PER_ROW));
	}

	/**
	 * 실제로 그려지는 하트 줄 수.
	 *
	 * <p>{@link #spacingRows} 와 <b>다를 수 있다.</b> 바닐라는 간격을 정할 때 체력과 흡수를
	 * 더한 뒤 한 번만 올림하지만({@code (체력 + 흡수) / 2}), 하트를 찍을 때는 각각 따로 올림한다
	 * ({@code ceil(체력 / 2) + ceil(흡수 / 2)}). 홀수가 섞이면 여기서 한 줄이 더 생긴다 —
	 * 예를 들어 체력 39, 흡수 1이면 간격은 두 줄 기준으로 잡히지만 하트는 세 줄로 그려진다.
	 * 겹침을 막으려면 <b>실제로 그려지는 줄</b>을 세야 한다.
	 */
	public static int heartRows(float maxHealth, float absorption) {
		int healthHalves = ceil(Math.max(0.0F, maxHealth) / 2.0F);
		int absorptionHalves = Math.ceilDiv(ceil(Math.max(0.0F, absorption)), 2);
		return Math.max(1, Math.ceilDiv(healthHalves + absorptionHalves, HEARTS_PER_ROW));
	}

	/**
	 * 하트 줄 간격.
	 *
	 * <p>{@code max(10 - (줄수 - 2), 3)}. 줄이 늘수록 촘촘해지다가 3픽셀에서 멈춘다.
	 */
	public static int rowSpacing(float maxHealth, float absorption) {
		return Math.max(BASE_ROW_SPACING - (spacingRows(maxHealth, absorption) - 2), MIN_ROW_SPACING);
	}

	/**
	 * 왼쪽(하트·방어구)이 차지하는 높이. 화면 아래끝에서 맨 윗 픽셀까지의 거리다.
	 *
	 * @param hasArmor 방어구 값이 1 이상인가. 0이면 바닐라가 방어구 줄 자체를 건너뛴다
	 */
	public static int leftHeight(float maxHealth, float absorption, boolean hasArmor) {
		int spacing = rowSpacing(maxHealth, absorption);
		int hearts = BOTTOM_ROW + (heartRows(maxHealth, absorption) - 1) * spacing + REGENERATION_LIFT;
		if (!hasArmor) {
			return hearts;
		}
		// 방어구는 그려지는 줄 수가 아니라 바닐라가 간격을 잡을 때 쓴 줄 수를 따라간다.
		int armor = BOTTOM_ROW + (spacingRows(maxHealth, absorption) - 1) * spacing + ARMOR_ROW_OFFSET;
		return Math.max(hearts, armor);
	}

	/**
	 * 오른쪽(배고픔 또는 탈것 체력, 공기 방울)이 차지하는 높이.
	 *
	 * <p>탈것을 타면 바닐라가 <b>배고픔 대신</b> 탈것 하트를 그린다. 둘이 같이 나오지 않는다.
	 *
	 * @param vehicleHearts 탈것 하트 수. 안 탔거나 체력을 보여주지 않는 탈것이면 0
	 * @param showAir       공기 방울이 떠 있는가({@code 물에 눈이 잠겼거나 산소가 덜 찼을 때})
	 */
	public static int rightHeight(int vehicleHearts, boolean showAir) {
		int vehicleRows = Math.ceilDiv(Math.min(Math.max(0, vehicleHearts), MAX_VEHICLE_HEARTS),
				HEARTS_PER_ROW);
		int height = vehicleRows > 0
				? BOTTOM_ROW + (vehicleRows - 1) * VEHICLE_ROW_SPACING
				: BOTTOM_ROW + FOOD_SHAKE;
		if (showAir) {
			// 공기 방울은 아래 줄이 몇 줄이든 그 위 한 줄에 붙는다. 탈것이 없어도 한 줄 위다.
			height = Math.max(height, BOTTOM_ROW + Math.max(vehicleRows, 1) * AIR_ROW_OFFSET);
		}
		return height;
	}

	/**
	 * 핫바 위 바닐라 표시 전체가 차지하는 높이.
	 *
	 * <p>우리 HUD 는 폭 182짜리 게이지처럼 핫바 전체를 가로지르는 것이 있어 왼쪽·오른쪽을
	 * 모두 피해야 한다. 그래서 둘 중 높은 쪽을 돌려준다.
	 */
	public static int height(float maxHealth, float absorption, boolean hasArmor,
			int vehicleHearts, boolean showAir) {
		return Math.max(leftHeight(maxHealth, absorption, hasArmor),
				rightHeight(vehicleHearts, showAir));
	}

	/** {@link #height} 를 실제 y 로 바꾼 값. 이 y 위로는 바닐라가 아무것도 그리지 않는다. */
	public static int top(int guiHeight, float maxHealth, float absorption, boolean hasArmor,
			int vehicleHearts, boolean showAir) {
		return guiHeight - height(maxHealth, absorption, hasArmor, vehicleHearts, showAir);
	}

	/** {@code Mth.ceil(float)} 과 같다. */
	private static int ceil(float value) {
		int truncated = (int) value;
		return value > truncated ? truncated + 1 : truncated;
	}
}
