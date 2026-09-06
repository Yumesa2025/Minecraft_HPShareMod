package com.sharedfate.client.hud;

import com.sharedfate.client.ClientTeamState;
import com.sharedfate.ui.BottomBarMetrics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 핫바 왼쪽 끝 위로 쌓아 올리는 글줄들의 좌표를 한곳에서 계산한다.
 *
 * <p>{@link TeamLevelHud} 와 {@link DamageAlertHud} 가 같은 자리를 쓴다. 각자 좌표를 재면
 * 서로 겹치므로, 바닥선을 여기서 한 번만 구하고 누가 몇 줄을 먹는지도 여기서 정한다.
 *
 * <h2>쌓는 순서</h2>
 * <p>아래에서 위로 <b>바닐라 표시</b>(하트·방어구·배고픔·공기 방울) → <b>증강 게이지</b> →
 * <b>팀 레벨</b> → <b>피격 알림</b> 이다. 반대로 두면 팀 레벨이 피격 때마다 위아래로 흔들린다.
 *
 * <p>{@link PerkProgressHud} 는 화면 가운데에 그리지만 <b>폭이 핫바와 같아</b> 이 글줄들과
 * 세로로 부딪힌다. 그래서 바닥선을 잴 때 그 게이지도 함께 센다.
 *
 * <h2>바닐라 표시 높이는 상수가 아니다</h2>
 * <p>이 모드는 최대 체력이 팀 설정과 증강으로 바뀌므로 하트가 한 줄일 때도 세 줄일 때도
 * 있고, 흡수(노란 하트)와 방어구가 붙으면 더 올라간다. 그 높이 계산은
 * {@link BottomBarMetrics} 에 있다. 여기서는 플레이어에게서 값을 꺼내 넘길 뿐이다.
 */
public final class BottomLeftStack {
	/** 글줄 높이. 바닐라 기본 글꼴 기준이다. */
	public static final int LINE_HEIGHT = 10;

	private BottomLeftStack() {
	}

	/** 핫바 왼쪽 끝 x. */
	public static int left(GuiGraphicsExtractor graphics) {
		return graphics.guiWidth() / 2 - BottomBarMetrics.HOTBAR_HALF_WIDTH;
	}

	/**
	 * 핫바 위 바닐라 표시가 차지하는 높이. 화면 아래끝에서 위로 잰 값이다.
	 *
	 * <p>{@link PerkProgressHud} 도 자기 자리를 잡을 때 이 값을 쓴다.
	 */
	public static int vanillaBarsHeight(Player player) {
		// 바닐라는 최대 체력 특성과 지금 체력 중 큰 쪽으로 줄 수를 센다. 최대 체력이 방금
		// 줄었을 때 하트가 곧바로 사라지지 않게 하려는 것이라, 우리도 같은 쪽을 봐야 한다.
		float maxHealth = Math.max(player.getMaxHealth(), player.getHealth());
		return BottomBarMetrics.height(maxHealth, player.getAbsorptionAmount(),
				player.getArmorValue() > 0, vehicleHearts(player), showsAirBubbles(player));
	}

	/**
	 * 가장 아래 글줄을 그릴 y.
	 *
	 * <p>하트 줄 수·흡수·방어구가 바뀌면 바닐라 표시가 높아지고, 그러면 게이지도 글줄도
	 * 따라 올라간다. 고정된 값은 글줄 높이뿐이다.
	 */
	public static int baseline(Player player, int guiHeight) {
		return PerkProgressHud.clearanceTop(guiHeight, vanillaBarsHeight(player)) - LINE_HEIGHT;
	}

	/**
	 * 팀 레벨 표시가 바닥에서 몇 줄을 차지하는가. 팀이 없으면 0줄이다.
	 *
	 * <p>{@link TeamLevelHud} 가 그리는 줄 수와 반드시 같아야 한다. 피격 알림은 이만큼
	 * 위에서 시작한다.
	 */
	public static int teamLevelLines() {
		if (!ClientTeamState.inTeam()) {
			return 0;
		}
		return ClientTeamState.levelsToNextPerk() < 0 ? 1 : 2;
	}

	/**
	 * 탈것 하트 수. {@code Hud.getVehicleMaxHearts} 를 그대로 옮겼다.
	 *
	 * <p>탈것을 타면 바닐라가 배고픔 대신 이 하트를 그리므로 오른쪽 높이가 달라진다.
	 */
	private static int vehicleHearts(Player player) {
		Entity vehicle = player.getVehicle();
		if (!(vehicle instanceof LivingEntity living) || !living.showVehicleHealth()) {
			return 0;
		}
		return Math.min((int) (living.getMaxHealth() + 0.5F) / 2, BottomBarMetrics.MAX_VEHICLE_HEARTS);
	}

	/** 공기 방울이 떠 있는가. {@code Hud.extractAirBubbles} 의 조건과 같다. */
	private static boolean showsAirBubbles(Player player) {
		int maxAir = player.getMaxAirSupply();
		return player.isEyeInFluid(FluidTags.WATER)
				|| Math.clamp((long) player.getAirSupply(), 0, maxAir) < maxAir;
	}
}
