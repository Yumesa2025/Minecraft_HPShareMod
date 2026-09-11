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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * 엔더드래곤 처치 뒤 이어지는 엔딩.
 *
 * <p>처치 → (기본 5초) → 팀원 자리에서 폭죽이 오르기 시작하고, 그 위로 화면 한가운데 글이
 * 한 장씩 넘어간다. 폭죽은 <b>마지막 장이 지나갈 때까지</b> 계속 터진다.
 *
 * <pre>
 * 3회차 승리        · 팀 이름
 * 최다 피해         · Kairen  412.5
 * 최다 사망         · Kairen  2회
 * 고른 증강         · 합계 17개
 * 수고하셨습니다      · 제작자 카이렌
 * </pre>
 *
 * <p>가운데 세 장은 <b>전체 회차 누적</b>이고 {@link DamageLedger#summaryFor} 가 센다. 셀
 * 것이 없는 장은 통째로 빠진다 — 「최다 사망 : 없음」은 축하 자리에 어울리지 않는다.
 *
 * <p>바닐라 엔딩 크레딧은 띄우지 않는다.
 *
 * <p>진행 상태는 전부 런타임에만 있고 저장하지 않는다. 서버가 재시작되면 예약이 사라지므로
 * 연출이 다시 재생되지 않는다. 승리 판정 자체는 {@link RunProgressState}가 파일로 들고 있어
 * 드래곤을 또 잡아도 {@link RunProgressManager}가 두 번째 승리를 막는다.
 */
public final class VictoryCelebration {
	/** 드래곤 처치 후 첫 장이 뜰 때까지의 기본 지연. 100틱 = 5초. */
	public static final int DEFAULT_TITLE_DELAY_TICKS = 100;
	/** 장이 넘어가는 기본 간격. 100틱 = 5초. */
	public static final int DEFAULT_FIREWORK_DELAY_TICKS = 100;

	/** 폭죽 한 무리와 다음 무리 사이. 20틱 = 1초. */
	static final int VOLLEY_PERIOD_TICKS = 20;

	/** 글이 뜨고 사라지는 데 쓰는 시간(틱). 나머지는 그대로 떠 있는 시간이다. */
	private static final int TITLE_FADE_IN_TICKS = 8;
	private static final int TITLE_FADE_OUT_TICKS = 8;

	/** 한 무리에 팀원 한 명당 터뜨릴 폭죽 개수. */
	private static final int ROCKETS_PER_PLAYER = 2;

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
	private static List<Card> cards = List.of();
	private static int cardGapTicks = DEFAULT_FIREWORK_DELAY_TICKS;

	private VictoryCelebration() {
	}

	/**
	 * 엔딩을 예약한다.
	 *
	 * @param audience     연출을 볼 사람들. 보통 승리 팀원
	 * @param runNumber    회차 번호
	 * @param winningName  승리 팀 이름
	 * @param summary      전체 회차 누적 기록. {@code null} 이면 가운데 세 장이 빠진다
	 * @param firstDelayTicks 처치 → 첫 장
	 * @param cardGapTicks 장이 넘어가는 간격
	 */
	public static void start(Collection<UUID> audience, int runNumber, String winningName,
			@Nullable DamageLedger.VictorySummary summary,
			int firstDelayTicks, int cardGapTicks) {
		AUDIENCE.clear();
		if (audience != null) {
			AUDIENCE.addAll(audience);
		}
		cards = buildCards(runNumber, winningName, summary);
		VictoryCelebration.cardGapTicks = Math.max(1, cardGapTicks);
		if (AUDIENCE.isEmpty() || cards.isEmpty()) {
			SCHEDULE.cancel();
			return;
		}
		SCHEDULE.start(firstDelayTicks, VictoryCelebration.cardGapTicks, cards.size());
	}

	/**
	 * 엔딩에 띄울 장들을 만든다.
	 *
	 * <p>셀 것이 없는 장은 넣지 않는다. 첫 장과 맺음말은 언제나 있다.
	 */
	static List<Card> buildCards(int runNumber, @Nullable String winningName,
			@Nullable DamageLedger.VictorySummary summary) {
		List<Card> built = new ArrayList<>(5);
		String team = winningName == null || winningName.isBlank() ? "모험가" : winningName;
		built.add(new Card(Math.max(1, runNumber) + "회차 승리", team, Tone.TRIUMPH));
		if (summary != null && summary.hasDamage()) {
			built.add(new Card("최다 피해",
					summary.topDamageName() + "  "
							+ String.format(Locale.ROOT, "%.1f", summary.topDamage()),
					Tone.STAT));
		}
		if (summary != null && summary.hasDeaths()) {
			built.add(new Card("최다 사망",
					summary.mostDeathsName() + "  " + summary.mostDeaths() + "회", Tone.STAT));
		}
		if (summary != null && summary.totalPerks() > 0) {
			built.add(new Card("고른 증강", "합계 " + summary.totalPerks() + "개", Tone.STAT));
		}
		built.add(new Card("수고하셨습니다", "제작자 카이렌", Tone.CLOSING));
		return List.copyOf(built);
	}

	/** 매 서버 틱마다 불린다. 예약이 없으면 곧바로 빠져나간다. */
	public static void tick(MinecraftServer server) {
		Schedule.Outcome outcome = SCHEDULE.advance();
		if (outcome.isQuiet()) {
			if (!SCHEDULE.isRunning()) {
				// 마지막 장의 여운까지 끝났다. 재입장·재시작으로 다시 재생되지 않게 비운다.
				AUDIENCE.clear();
			}
			return;
		}
		List<ServerPlayer> viewers = onlineAudience(server);
		if (outcome.firework()) {
			launchFireworks(viewers);
		}
		if (outcome.cardIndex() >= 0 && outcome.cardIndex() < cards.size()) {
			showCard(viewers, cards.get(outcome.cardIndex()), outcome.cardIndex() == 0);
		}
	}

	/** 서버가 멈추거나 회차가 초기화될 때 예약을 지운다. */
	public static void reset() {
		SCHEDULE.cancel();
		AUDIENCE.clear();
		cards = List.of();
		cardGapTicks = DEFAULT_FIREWORK_DELAY_TICKS;
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

	/**
	 * 한 장을 띄운다.
	 *
	 * <p>떠 있는 시간을 간격에 맞춰 잡아, <b>다음 장이 올 때까지 화면에 남아 있게</b> 한다.
	 * 짧게 잡으면 글이 사라진 빈 화면에 폭죽만 남는 순간이 생긴다.
	 */
	private static void showCard(List<ServerPlayer> viewers, Card card, boolean first) {
		int stay = Math.max(1, cardGapTicks - TITLE_FADE_IN_TICKS - TITLE_FADE_OUT_TICKS);
		TitleMessenger.showTitle(viewers,
				Component.literal(card.title()).withStyle(card.tone().titleStyle()),
				Component.literal(card.subtitle()).withStyle(card.tone().subtitleStyle()),
				TITLE_FADE_IN_TICKS, stay, TITLE_FADE_OUT_TICKS);
		for (ServerPlayer player : viewers) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					first ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_CHIME.value(),
					SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		SharedFateMod.LOGGER.info("[RUN] victory card '{}' viewers={}", card.title(), viewers.size());
	}

	private static void launchFireworks(List<ServerPlayer> viewers) {
		RandomGenerator random = ThreadLocalRandom.current();
		for (ServerPlayer player : viewers) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (int index = 0; index < ROCKETS_PER_PLAYER; index++) {
				double x = player.getX() + (random.nextDouble() - 0.5) * 3.0;
				double y = player.getY() + ROCKET_SPAWN_HEIGHT;
				double z = player.getZ() + (random.nextDouble() - 0.5) * 3.0;
				level.addFreshEntity(
						new FireworkRocketEntity(level, rocketStack(random), x, y, z, false));
			}
		}
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

	/** 장의 성격. 색만 다르다. */
	enum Tone {
		/** 첫 장. 가장 크고 밝다. */
		TRIUMPH(new ChatFormatting[] {ChatFormatting.GOLD, ChatFormatting.BOLD},
				new ChatFormatting[] {ChatFormatting.YELLOW}),
		/** 가운데 기록 장들. */
		STAT(new ChatFormatting[] {ChatFormatting.AQUA, ChatFormatting.BOLD},
				new ChatFormatting[] {ChatFormatting.WHITE}),
		/** 맺음말. */
		CLOSING(new ChatFormatting[] {ChatFormatting.WHITE, ChatFormatting.BOLD},
				new ChatFormatting[] {ChatFormatting.GRAY});

		private final ChatFormatting[] title;
		private final ChatFormatting[] subtitle;

		Tone(ChatFormatting[] title, ChatFormatting[] subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}

		ChatFormatting[] titleStyle() {
			return title;
		}

		ChatFormatting[] subtitleStyle() {
			return subtitle;
		}
	}

	/** 엔딩의 한 장. */
	record Card(String title, String subtitle, Tone tone) {
	}

	/**
	 * 엔딩을 틱 단위로 넘기는 순수 로직.
	 *
	 * <p>글과 폭죽을 <b>따로</b> 센다. 글은 간격마다 한 장씩, 폭죽은 그와 상관없이 1초마다
	 * 한 무리다. 한 줄기로 묶으면 간격을 바꿀 때마다 폭죽이 성기거나 빽빽해진다.
	 */
	public static final class Schedule {
		/** 「이번 틱에 할 일 없음」을 나타내는 장 번호. */
		public static final int NO_CARD = -1;

		/**
		 * 한 틱의 결과.
		 *
		 * @param firework  이번 틱에 폭죽 한 무리를 터뜨리는가
		 * @param cardIndex 이번 틱에 띄울 장 번호. 없으면 {@link #NO_CARD}
		 */
		public record Outcome(boolean firework, int cardIndex) {
			public static final Outcome QUIET = new Outcome(false, NO_CARD);

			/** 이번 틱에 아무 일도 없는가. */
			public boolean isQuiet() {
				return !firework && cardIndex == NO_CARD;
			}
		}

		private int cardCount;
		private int gapTicks;
		private int ticksToNextCard;
		private int ticksToNextVolley;
		private int shownCards;
		private boolean running;

		/**
		 * 엔딩을 예약한다. 0 이하가 들어오면 최소 1틱으로 올려 항상 다음 틱 이후에 시작한다.
		 *
		 * @param firstDelayTicks 처치 → 첫 장
		 * @param gapTicks        장 사이 간격
		 * @param cardCount       띄울 장 수
		 */
		public void start(int firstDelayTicks, int gapTicks, int cardCount) {
			this.cardCount = Math.max(0, cardCount);
			this.gapTicks = Math.max(1, gapTicks);
			this.ticksToNextCard = Math.max(1, firstDelayTicks);
			// 폭죽도 첫 장과 함께 시작한다. 글보다 먼저 터지면 무슨 일인지 모른 채 하늘만 본다.
			this.ticksToNextVolley = this.ticksToNextCard;
			this.shownCards = 0;
			this.running = this.cardCount > 0;
		}

		/**
		 * 한 틱 진행한다.
		 *
		 * <p>마지막 장을 띄운 뒤에도 <b>간격 하나만큼 더</b> 돈다. 그 여운 동안 폭죽이 계속
		 * 터져 맺음말이 화면에 떠 있는 채로 끝난다.
		 */
		public Outcome advance() {
			if (!running) {
				return Outcome.QUIET;
			}
			boolean firework = false;
			if (--ticksToNextVolley <= 0) {
				firework = true;
				ticksToNextVolley = VOLLEY_PERIOD_TICKS;
			}
			if (--ticksToNextCard > 0) {
				return new Outcome(firework, NO_CARD);
			}
			if (shownCards >= cardCount) {
				running = false;
				return new Outcome(firework, NO_CARD);
			}
			ticksToNextCard = gapTicks;
			return new Outcome(firework, shownCards++);
		}

		/** 남은 단계가 있는지. */
		public boolean isRunning() {
			return running;
		}

		/** 예약을 전부 지운다. */
		public void cancel() {
			cardCount = 0;
			gapTicks = 0;
			ticksToNextCard = 0;
			ticksToNextVolley = 0;
			shownCards = 0;
			running = false;
		}
	}
}
