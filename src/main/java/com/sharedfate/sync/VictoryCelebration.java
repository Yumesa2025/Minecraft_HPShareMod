package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.config.SharedFateConfig;
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
 * <p>처치 → (기본 10초) → 팀원 자리에서 폭죽이 오르기 시작하고, 그 위로 화면 한가운데 글이
 * 한 장씩 넘어간다. 폭죽은 <b>마지막 장이 지나갈 때까지</b> 계속 터진다.
 *
 * <pre>
 * 3회차 승리        · 팀 이름
 * 받은 피해량        · 플레이어1  412.5
 * 최다 사망         · 플레이어1  2회
 * 고른 증강         · 합계 17개
 * 수고하셨습니다      · 제작자 카이렌
 * </pre>
 *
 * <p>한 장은 <b>두 걸음</b>으로 나뉜다. 먼저 제목만 뜨고, 기본 3.5초 뒤에 그 아래로 부제가
 * 따라 붙는다. 그러고 나서 다시 장 간격만큼 있다가 다음 장으로 넘어간다 — 한 장의 길이는
 * {@code 부제 지연 + 장 간격}이다. 둘을 한꺼번에 띄우면 읽는 사람이 어느 쪽을 볼지 정하기도
 * 전에 장이 넘어간다.
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
	/**
	 * 드래곤 처치 후 첫 장이 뜰 때까지의 기본 지연. 200틱 = 10초.
	 *
	 * <p>처치 직후에는 승리 채팅과 책 지급이 한꺼번에 쏟아지고 사람들은 아직 드래곤이 떨어지는
	 * 것을 보고 있다. 그 위에 정산을 얹으면 아무도 읽지 않는다.
	 */
	public static final int DEFAULT_TITLE_DELAY_TICKS = 200;
	/** 장이 넘어가는 기본 간격. 100틱 = 5초. */
	public static final int DEFAULT_FIREWORK_DELAY_TICKS = 100;
	/** 제목이 뜬 뒤 부제가 따라 붙기까지의 기본 지연. 70틱 = 3.5초. */
	public static final int DEFAULT_SUBTITLE_DELAY_TICKS = 70;

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
	private static int subtitleDelayTicks = DEFAULT_SUBTITLE_DELAY_TICKS;

	private VictoryCelebration() {
	}

	/**
	 * 엔딩을 예약한다. 부제 지연은 설정값을 그대로 쓴다.
	 *
	 * @param audience     연출을 볼 사람들. 보통 승리 팀원
	 * @param runNumber    회차 번호
	 * @param winningName  승리 팀 이름
	 * @param summary      전체 회차 누적 기록. {@code null} 이면 가운데 세 장이 빠진다
	 * @param firstDelayTicks 처치 → 첫 장
	 * @param cardGapTicks 부제가 뜬 뒤 다음 장까지의 간격
	 */
	public static void start(Collection<UUID> audience, int runNumber, String winningName,
			@Nullable DamageLedger.VictorySummary summary,
			int firstDelayTicks, int cardGapTicks) {
		start(audience, runNumber, winningName, summary,
				firstDelayTicks, configuredSubtitleDelayTicks(), cardGapTicks);
	}

	/**
	 * 엔딩을 예약한다.
	 *
	 * @param subtitleDelayTicks 제목만 뜬 뒤 부제가 따라 붙기까지의 시간
	 */
	public static void start(Collection<UUID> audience, int runNumber, String winningName,
			@Nullable DamageLedger.VictorySummary summary,
			int firstDelayTicks, int subtitleDelayTicks, int cardGapTicks) {
		AUDIENCE.clear();
		if (audience != null) {
			AUDIENCE.addAll(audience);
		}
		cards = buildCards(runNumber, winningName, summary);
		VictoryCelebration.cardGapTicks = Math.max(1, cardGapTicks);
		VictoryCelebration.subtitleDelayTicks = Math.max(1, subtitleDelayTicks);
		if (AUDIENCE.isEmpty() || cards.isEmpty()) {
			SCHEDULE.cancel();
			return;
		}
		SCHEDULE.start(firstDelayTicks, VictoryCelebration.subtitleDelayTicks,
				VictoryCelebration.cardGapTicks, cards.size());
	}

	/**
	 * 설정에 적힌 부제 지연. 설정이 아직 없으면(시험·이른 호출) 기본값을 쓴다.
	 *
	 * <p>{@link RunProgressManager} 는 예전 6개짜리 {@code start} 를 그대로 부른다. 그쪽을
	 * 건드리지 않고도 새 설정값이 먹히도록 여기서 읽는다.
	 */
	private static int configuredSubtitleDelayTicks() {
		SharedFateConfig config = SharedFateMod.config;
		return config == null ? DEFAULT_SUBTITLE_DELAY_TICKS : config.victorySubtitleDelayTicks;
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
			built.add(new Card("받은 피해량",
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
			Card card = cards.get(outcome.cardIndex());
			if (outcome.subtitle()) {
				showCardSubtitle(viewers, card);
			} else {
				showCardTitle(viewers, card, outcome.cardIndex() == 0);
			}
		}
	}

	/** 서버가 멈추거나 회차가 초기화될 때 예약을 지운다. */
	public static void reset() {
		SCHEDULE.cancel();
		AUDIENCE.clear();
		cards = List.of();
		cardGapTicks = DEFAULT_FIREWORK_DELAY_TICKS;
		subtitleDelayTicks = DEFAULT_SUBTITLE_DELAY_TICKS;
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
	 * 한 장의 <b>첫 걸음</b>. 제목만 띄운다.
	 *
	 * <p>떠 있는 시간을 부제 지연에 맞춰 잡아, <b>부제가 따라 붙을 때까지 제목이 화면에 남아
	 * 있게</b> 한다. 짧게 잡으면 제목이 사라진 빈 화면에 폭죽만 남는 순간이 생긴다.
	 */
	private static void showCardTitle(List<ServerPlayer> viewers, Card card, boolean first) {
		TitleMessenger.showTitle(viewers,
				Component.literal(card.title()).withStyle(card.tone().titleStyle()),
				Component.empty(),
				TITLE_FADE_IN_TICKS, Math.max(1, subtitleDelayTicks), TITLE_FADE_OUT_TICKS);
		for (ServerPlayer player : viewers) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					first ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_CHIME.value(),
					SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		SharedFateMod.LOGGER.info("[RUN] victory card '{}' viewers={}", card.title(), viewers.size());
	}

	/**
	 * 한 장의 <b>두 번째 걸음</b>. 제목 아래로 부제를 얹는다.
	 *
	 * <p><b>페이드인을 0으로 두고 같은 제목을 함께 다시 보낸다.</b> 바닐라 클라이언트는 제목
	 * 패킷을 받을 때마다 {@code 페이드인 + 머무름 + 페이드아웃} 타이머를 새로 잡는다. 여기서
	 * 페이드인을 8로 두면 이미 떠 있던 제목이 투명해졌다가 다시 나타나 깜빡인다. 0이면 곧바로
	 * 「머무름」 구간에 들어가 제목은 그대로 있고 부제만 새로 붙는다.
	 *
	 * <p>머무름은 장 간격에 맞춘다. 이 장은 다음 장이 올 때까지 이대로 떠 있어야 한다.
	 *
	 * <p>부제가 빈 장이 들어와도 제목은 다시 보낸다. 그래야 장 간격만큼 화면에 남는다.
	 */
	private static void showCardSubtitle(List<ServerPlayer> viewers, Card card) {
		int stay = Math.max(1, cardGapTicks - TITLE_FADE_OUT_TICKS);
		Component subtitle = card.subtitle() == null || card.subtitle().isBlank()
				? Component.empty()
				: Component.literal(card.subtitle()).withStyle(card.tone().subtitleStyle());
		TitleMessenger.showTitle(viewers,
				Component.literal(card.title()).withStyle(card.tone().titleStyle()),
				subtitle,
				0, stay, TITLE_FADE_OUT_TICKS);
	}

	/**
	 * 팀원 머리 위로 폭죽 한 무리를 올린다.
	 *
	 * <p><b>발사음은 사람당 한 번만 낸다.</b> 바닐라 {@link FireworkRocketEntity} 도 첫 틱에
	 * {@code FIREWORK_ROCKET_LAUNCH} 를 {@link SoundSource#AMBIENT} 로 한 발씩 내지만, 그
	 * 범주는 「주변 소리」 슬라이더를 따라가 동굴 소리를 줄여 둔 사람에게는 들리지 않는다.
	 * 그래서 연출의 다른 소리와 같은 {@link SoundSource#PLAYERS} 로 한 번 더 낸다 — 로켓마다
	 * 내면 같은 소리가 겹쳐 찢어지므로 무리마다 한 번이다. 터지는 소리는 클라이언트가 폭죽
	 * 입자에서 알아서 내므로 여기서 건드리지 않는다.
	 */
	private static void launchFireworks(List<ServerPlayer> viewers) {
		RandomGenerator random = ThreadLocalRandom.current();
		for (ServerPlayer player : viewers) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			double spawnY = player.getY() + ROCKET_SPAWN_HEIGHT;
			for (int index = 0; index < ROCKETS_PER_PLAYER; index++) {
				double x = player.getX() + (random.nextDouble() - 0.5) * 3.0;
				double z = player.getZ() + (random.nextDouble() - 0.5) * 3.0;
				level.addFreshEntity(
						new FireworkRocketEntity(level, rocketStack(random), x, spawnY, z, false));
			}
			level.playSound(null, player.getX(), spawnY, player.getZ(),
					SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
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
	 * <p>글과 폭죽을 <b>따로</b> 센다. 글은 정해진 걸음마다 한 번씩, 폭죽은 그와 상관없이
	 * 1초마다 한 무리다. 한 줄기로 묶으면 간격을 바꿀 때마다 폭죽이 성기거나 빽빽해진다.
	 *
	 * <p>한 장은 두 걸음이다 — 제목만 뜨는 걸음, 그리고 {@code 부제 지연} 뒤에 부제가 붙는
	 * 걸음. 다음 장은 부제가 붙고 {@code 장 간격} 이 더 지나야 온다.
	 *
	 * <pre>
	 * 0 ─ 첫 지연 ─▶ [장0 제목] ─ 부제지연 ─▶ [장0 부제] ─ 장간격 ─▶ [장1 제목] ─ …
	 * </pre>
	 */
	public static final class Schedule {
		/** 「이번 틱에 할 일 없음」을 나타내는 장 번호. */
		public static final int NO_CARD = -1;

		/**
		 * 한 틱의 결과.
		 *
		 * @param firework  이번 틱에 폭죽 한 무리를 터뜨리는가
		 * @param cardIndex 이번 틱에 손댈 장 번호. 없으면 {@link #NO_CARD}
		 * @param subtitle  그 장의 두 번째 걸음(부제)인가. {@code false} 면 제목만 띄우는 첫 걸음
		 */
		public record Outcome(boolean firework, int cardIndex, boolean subtitle) {
			public static final Outcome QUIET = new Outcome(false, NO_CARD, false);

			/** 이번 틱에 아무 일도 없는가. */
			public boolean isQuiet() {
				return !firework && cardIndex == NO_CARD;
			}
		}

		private int cardCount;
		private int gapTicks;
		private int subtitleDelayTicks;
		private int ticksToNextStep;
		private int ticksToNextVolley;
		private int shownCards;
		/** 다음 걸음이 「방금 제목만 띄운 장에 부제를 얹는 것」인가. */
		private boolean awaitingSubtitle;
		private boolean running;

		/**
		 * 엔딩을 예약한다. 0 이하가 들어오면 최소 1틱으로 올려 항상 다음 틱 이후에 시작한다.
		 *
		 * @param firstDelayTicks    처치 → 첫 장의 제목
		 * @param subtitleDelayTicks 제목 → 그 장의 부제
		 * @param gapTicks           부제 → 다음 장의 제목
		 * @param cardCount          띄울 장 수
		 */
		public void start(int firstDelayTicks, int subtitleDelayTicks, int gapTicks, int cardCount) {
			this.cardCount = Math.max(0, cardCount);
			this.gapTicks = Math.max(1, gapTicks);
			this.subtitleDelayTicks = Math.max(1, subtitleDelayTicks);
			this.ticksToNextStep = Math.max(1, firstDelayTicks);
			// 폭죽도 첫 장과 함께 시작한다. 글보다 먼저 터지면 무슨 일인지 모른 채 하늘만 본다.
			this.ticksToNextVolley = this.ticksToNextStep;
			this.shownCards = 0;
			this.awaitingSubtitle = false;
			this.running = this.cardCount > 0;
		}

		/**
		 * 한 틱 진행한다.
		 *
		 * <p>마지막 장의 부제까지 얹은 뒤에도 <b>간격 하나만큼 더</b> 돈다. 그 여운 동안 폭죽이
		 * 계속 터져 맺음말이 화면에 떠 있는 채로 끝난다. 한 장이 {@code 부제 지연 + 장 간격} 으로
		 * 길어졌으므로 <b>폭죽이 오르는 시간도 그만큼 함께 길어진다</b> — 맺음말 한 장만 놓고 봐도
		 * 예전 「간격 하나」가 아니라 「부제 지연 + 간격」 동안 하늘이 비지 않는다.
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
			if (--ticksToNextStep > 0) {
				return new Outcome(firework, NO_CARD, false);
			}
			if (awaitingSubtitle) {
				awaitingSubtitle = false;
				ticksToNextStep = gapTicks;
				return new Outcome(firework, shownCards - 1, true);
			}
			if (shownCards >= cardCount) {
				running = false;
				return new Outcome(firework, NO_CARD, false);
			}
			awaitingSubtitle = true;
			ticksToNextStep = subtitleDelayTicks;
			return new Outcome(firework, shownCards++, false);
		}

		/** 남은 단계가 있는지. */
		public boolean isRunning() {
			return running;
		}

		/** 예약을 전부 지운다. */
		public void cancel() {
			cardCount = 0;
			gapTicks = 0;
			subtitleDelayTicks = 0;
			ticksToNextStep = 0;
			ticksToNextVolley = 0;
			shownCards = 0;
			awaitingSubtitle = false;
			running = false;
		}
	}
}
