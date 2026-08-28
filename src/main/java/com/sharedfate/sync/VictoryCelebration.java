package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * 엔더드래곤 처치 뒤 이어지는 승리 연출.
 *
 * <p>처치 → (기본 5초) → 화면 중앙에 "엔더드래곤 토벌" 타이틀 → (기본 5초) → 팀원 각자의
 * 위치에 폭죽. 엔딩 크레딧은 띄우지 않는다.
 *
 * <p>진행 상태는 전부 런타임에만 있고 저장하지 않는다. 서버가 재시작되면 예약이 사라지므로
 * 연출이 다시 재생되지 않는다. 승리 판정 자체는 {@link RunProgressState}가 파일로 들고 있어
 * 드래곤을 또 잡아도 {@link RunProgressManager}가 두 번째 승리를 막는다.
 */
public final class VictoryCelebration {
	/** 드래곤 처치 후 타이틀이 뜰 때까지의 기본 지연. 100틱 = 5초. */
	public static final int DEFAULT_TITLE_DELAY_TICKS = 100;
	/** 타이틀이 뜬 뒤 폭죽이 터질 때까지의 기본 지연. 100틱 = 5초. */
	public static final int DEFAULT_FIREWORK_DELAY_TICKS = 100;

	/** 타이틀 페이드 인 / 유지 / 페이드 아웃 (틱). 합쳐서 5초 남짓 보인다. */
	private static final int TITLE_FADE_IN_TICKS = 10;
	private static final int TITLE_STAY_TICKS = 70;
	private static final int TITLE_FADE_OUT_TICKS = 20;

	/** 팀원 한 명당 터뜨릴 폭죽 개수. */
	private static final int ROCKETS_PER_PLAYER = 3;

	/**
	 * 폭죽을 플레이어 머리 위 몇 블록에서 띄울지.
	 *
	 * <p>바닐라 폭죽은 터질 때 반경 5블록 안의 생명체에게 {@code 5 + 폭죽효과수 * 2} 피해를 준다.
	 * 이 모드는 체력을 팀이 공유하므로 축하 폭죽이 팀을 몰살시킬 수 있다. 그래서 피해 반경(5블록)
	 * 보다 확실히 높은 곳에서 띄운다. 천장에 막혀 즉시 터져도 거리가 5블록을 넘어 피해가 없다.
	 */
	private static final double ROCKET_SPAWN_HEIGHT = 8.0;

	/** 폭죽 색. 순서대로 빨강·금색·하늘색·연두·자주. */
	private static final int[] ROCKET_COLORS = {
			0xE8453C, 0xF2C43D, 0x4FA8E8, 0x63C74D, 0xC353D1
	};

	private static final FireworkExplosion.Shape[] ROCKET_SHAPES = {
			FireworkExplosion.Shape.LARGE_BALL,
			FireworkExplosion.Shape.STAR,
			FireworkExplosion.Shape.BURST
	};

	private static final Schedule SCHEDULE = new Schedule();
	private static final Set<UUID> AUDIENCE = new LinkedHashSet<>();
	private static int runNumber;
	private static String winningName = "";

	private VictoryCelebration() {
	}

	/**
	 * 승리 연출을 예약한다.
	 *
	 * @param audience          연출을 볼 사람들. 보통 승리 팀원.
	 * @param runNumber         회차 번호. 부제에 쓴다.
	 * @param winningName       승리 팀 이름. 부제에 쓴다.
	 * @param titleDelayTicks   처치 → 타이틀 지연
	 * @param fireworkDelayTicks 타이틀 → 폭죽 지연
	 */
	public static void start(Collection<UUID> audience, int runNumber, String winningName,
			int titleDelayTicks, int fireworkDelayTicks) {
		AUDIENCE.clear();
		if (audience != null) {
			AUDIENCE.addAll(audience);
		}
		VictoryCelebration.runNumber = Math.max(1, runNumber);
		VictoryCelebration.winningName = winningName == null || winningName.isBlank()
				? "모험가" : winningName;
		if (AUDIENCE.isEmpty()) {
			SCHEDULE.cancel();
			return;
		}
		SCHEDULE.start(titleDelayTicks, fireworkDelayTicks);
	}

	/** 매 서버 틱마다 불린다. 예약이 없으면 곧바로 빠져나간다. */
	public static void tick(MinecraftServer server) {
		Schedule.Step step = SCHEDULE.advance();
		if (step == Schedule.Step.NONE) {
			return;
		}
		List<ServerPlayer> viewers = onlineAudience(server);
		if (step == Schedule.Step.TITLE) {
			showVictoryTitle(viewers);
			return;
		}
		launchFireworks(viewers);
		// 연출이 끝났으니 관객 목록을 비워 재입장·재시작으로 다시 재생되지 않게 한다.
		AUDIENCE.clear();
	}

	/** 서버가 멈추거나 회차가 초기화될 때 예약을 지운다. */
	public static void reset() {
		SCHEDULE.cancel();
		AUDIENCE.clear();
		runNumber = 0;
		winningName = "";
	}

	/** 연출이 아직 남아 있는지. 테스트와 로그용. */
	public static boolean isRunning() {
		return SCHEDULE.isRunning();
	}

	private static List<ServerPlayer> onlineAudience(MinecraftServer server) {
		List<ServerPlayer> result = new ArrayList<>();
		if (server == null) {
			return result;
		}
		for (UUID playerId : AUDIENCE) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null && !player.isRemoved()) {
				result.add(player);
			}
		}
		return result;
	}

	private static void showVictoryTitle(List<ServerPlayer> viewers) {
		Component title = Component.literal("엔더드래곤 토벌")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		Component subtitle = Component.literal(runNumber + "회차 · " + winningName)
				.withStyle(ChatFormatting.YELLOW);
		TitleMessenger.showTitle(viewers, title, subtitle,
				TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS);
		for (ServerPlayer player : viewers) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		SharedFateMod.LOGGER.info("[RUN] victory title shown viewers={}", viewers.size());
	}

	private static void launchFireworks(List<ServerPlayer> viewers) {
		RandomGenerator random = ThreadLocalRandom.current();
		int launched = 0;
		for (ServerPlayer player : viewers) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (int index = 0; index < ROCKETS_PER_PLAYER; index++) {
				double x = player.getX() + (random.nextDouble() - 0.5) * 3.0;
				double y = player.getY() + ROCKET_SPAWN_HEIGHT;
				double z = player.getZ() + (random.nextDouble() - 0.5) * 3.0;
				FireworkRocketEntity rocket =
						new FireworkRocketEntity(level, rocketStack(random), x, y, z, false);
				if (level.addFreshEntity(rocket)) {
					launched++;
				}
			}
		}
		SharedFateMod.LOGGER.info("[RUN] victory fireworks viewers={} rockets={}",
				viewers.size(), launched);
	}

	/** 무작위 색·모양의 폭죽 로켓 아이템을 만든다. 26.2 는 아이템 컴포넌트로 폭죽을 정의한다. */
	private static ItemStack rocketStack(RandomGenerator random) {
		FireworkExplosion explosion = new FireworkExplosion(
				ROCKET_SHAPES[random.nextInt(ROCKET_SHAPES.length)],
				IntList.of(ROCKET_COLORS[random.nextInt(ROCKET_COLORS.length)],
						ROCKET_COLORS[random.nextInt(ROCKET_COLORS.length)]),
				IntList.of(ROCKET_COLORS[random.nextInt(ROCKET_COLORS.length)]),
				true, true);
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));
		return stack;
	}

	/**
	 * 연출 단계를 틱 단위로 넘기는 순수 로직.
	 *
	 * <p>마인크래프트 클래스에 전혀 기대지 않아 단위 테스트로 타이밍을 검증할 수 있다.
	 */
	public static final class Schedule {
		/** {@link #advance()}가 이번 틱에 할 일. */
		public enum Step {
			/** 할 일 없음. */
			NONE,
			/** 타이틀을 띄울 차례. */
			TITLE,
			/** 폭죽을 터뜨릴 차례. */
			FIREWORK
		}

		private int remainingTicks;
		private int fireworkDelayTicks;
		private boolean awaitingTitle;
		private boolean awaitingFirework;

		/** 두 단계를 예약한다. 0 이하가 들어오면 최소 1틱으로 올려 항상 다음 틱 이후에 터지게 한다. */
		public void start(int titleDelayTicks, int fireworkDelayTicks) {
			this.remainingTicks = Math.max(1, titleDelayTicks);
			this.fireworkDelayTicks = Math.max(1, fireworkDelayTicks);
			this.awaitingTitle = true;
			this.awaitingFirework = true;
		}

		/** 한 틱 진행한다. 단계가 도래한 틱에만 {@link Step#TITLE} 또는 {@link Step#FIREWORK}를 돌려준다. */
		public Step advance() {
			if (!isRunning()) {
				return Step.NONE;
			}
			if (--remainingTicks > 0) {
				return Step.NONE;
			}
			if (awaitingTitle) {
				awaitingTitle = false;
				remainingTicks = fireworkDelayTicks;
				return Step.TITLE;
			}
			awaitingFirework = false;
			remainingTicks = 0;
			return Step.FIREWORK;
		}

		/** 남은 단계가 있는지. */
		public boolean isRunning() {
			return awaitingTitle || awaitingFirework;
		}

		/** 예약을 전부 지운다. */
		public void cancel() {
			remainingTicks = 0;
			fireworkDelayTicks = 0;
			awaitingTitle = false;
			awaitingFirework = false;
		}
	}
}
