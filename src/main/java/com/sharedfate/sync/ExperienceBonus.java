package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.effect.ExperienceBonusEffect;
import com.sharedfate.perk.effect.ExperienceBonusEffect.Source;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 경험치에 배율을 먹이는 규칙 두 겹.
 *
 * <h2>1. 상시 규칙 — 모두에게, 출처를 가리지 않고</h2>
 * <p>증강이 아니다. 팀이 무엇을 골랐든, 팀에 속해 있지 않든 똑같이 걸린다. 배율은
 * {@link SharedFateConfig#experienceMultiplier}(기본 1.2배)이고 설정 파일에서 바꿀 수 있다.
 * 적용하는 자리는 {@code ExperienceOrbAwardMixin} 이다.
 *
 * <h2>2. 출처별 규칙 — 그 팀에게만, 정해진 출처에서만</h2>
 * <p>{@code experience_bonus}({@link ExperienceBonusEffect})를 가진 팀은 광물이나 몹에서
 * 나오는 경험치에만 추가 배율을 받는다. 세트 보상 「채굴 2단계」·「사냥 2단계」가 이것을 쓴다.
 * 이쪽 배율은 <b>상시 규칙과 곱해진다</b> — 설정 1.2 배에 세트 1.5 배면 결과는 1.8 배다.
 *
 * <h2>왜 「줍는 순간」이 아니라 「떨어지는 순간」인가</h2>
 * <p>경험치 오브는 바닥에 있는 동안 서로 <b>합쳐진다</b>({@code ExperienceOrb.scanForMerges}).
 * 줍는 자리({@code playerTouch})에서 배율을 먹이면 이미 합쳐진 덩어리에 곱하게 되어, 같은 양을
 * 캐도 오브가 몇 개로 뭉쳤느냐에 따라 결과가 달라지고 반올림 오차도 덩어리 수만큼 달라진다.
 * 떨어지는 자리에서 한 번만 곱하면 그런 흔들림이 없다.
 *
 * <h2>상시 규칙은 어디를 잡는가</h2>
 * <p>26.2 에서 경험치 오브가 월드에 생기는 길은 {@code ExperienceOrb.awardWithDirection(
 * ServerLevel, Vec3, Vec3, int)} 하나로 모인다. {@code ExperienceOrb.award(ServerLevel, Vec3,
 * int)} 는 방향을 {@code Vec3.ZERO} 로 채워 그것을 그대로 부르는 한 줄짜리 메서드고, 몹 처치
 * ({@code LivingEntity}) · 광석({@code Block.popExperience}) · 화로 · 낚시 · 번식 · 주민 거래 ·
 * 경험치병 · 숫돌이 전부 그 둘 중 하나를 지난다. 그래서 {@code awardWithDirection} 의 인자
 * 하나만 고치면 모든 경로가 같은 배율을 받는다. 자세한 확인 근거는
 * {@code ExperienceOrbAwardMixin} 에 적어 뒀다.
 *
 * <h2>출처별 규칙은 왜 그 한 자리를 쓰지 않는가</h2>
 * <p>{@code awardWithDirection} 에는 <b>출처도 사람도 남아 있지 않다.</b> 인자는 월드·좌표·양
 * 뿐이다. 그래서 출처별 배율은 「누가·무엇에서」를 아직 알고 있는 <b>더 위쪽</b>에서 곱한다.
 *
 * <ul>
 *   <li><b>몹</b> — {@code LivingEntity.dropExperience(ServerLevel, Entity)} 안에서
 *       {@code getExperienceReward} 가 돌려주는 값을 곱한다. 그 메서드의 두 번째 인자가 곧
 *       처치자라 <b>문맥을 따로 기억할 필요가 없다.</b></li>
 *   <li><b>광물</b> — {@code Block.playerDestroy(...)} 에 들어갈 때 「누가·어디의·무슨 블록을」을
 *       적어 두고({@link #beginBlockExperience}), 그 안에서 불리는
 *       {@code Block.popExperience(ServerLevel, BlockPos, int)} 가 그것을 읽는다. 블록을 캐는
 *       길에는 사람이 인자로 흘러 들어오지 않아 이 한 겹이 불가피하다.</li>
 * </ul>
 *
 * <h2>문맥이 새면 어떻게 되는가</h2>
 * <p>기억해 둔 블록 문맥이 지워지지 않고 남으면 엉뚱한 경험치에 배율이 붙을 수 있다. 그래서
 * 두 겹으로 막았다.
 * <ol>
 *   <li>읽는 쪽이 {@code Block.popExperience} <b>하나뿐</b>이다. 이 메서드에 닿는 것은 블록에서
 *       나오는 경험치밖에 없어서, 문맥이 새더라도 화로·낚시·번식·주민 거래·경험치병 쪽으로는
 *       절대 번지지 않는다. {@code awardWithDirection} 에서 읽었다면 그 전부가 사정권이다.</li>
 *   <li>그 위에 <b>좌표까지 맞아야</b> 곱한다. 남은 문맥은 이미 캔 자리를 가리키고 있으므로,
 *       다음에 다른 자리에서 경험치가 튀어도 좌표가 달라 그냥 지나간다.</li>
 * </ol>
 *
 * <h2>반올림</h2>
 * <p>{@link Math#round} 로 가장 가까운 정수에 맞춘다. 1점짜리 오브가 배율 1.2 에서 1점으로
 * 남는 것은 정수 경험치의 어쩔 수 없는 결과다. 대신 <b>원래 양이 1 이상이었으면 결과도 반드시
 * 1 이상</b>이 되게 막는다. 배율을 낮게 잡은 서버에서 작은 오브가 통째로 사라져 「경험치가 아예
 * 안 나온다」로 보이는 것을 막기 위해서다.
 *
 * <p>출처별 배율과 상시 배율은 <b>서로 다른 자리에서</b> 곱하므로 반올림도 두 번 일어난다.
 * 한 번에 곱했을 때와 최대 1점까지 다를 수 있다. 정수 경험치에서 1점 차이는 눈에 띄지 않고,
 * 대신 상시 배율이 걸리는 자리를 하나로 유지할 수 있어 이쪽을 골랐다.
 */
public final class ExperienceBonus {
	/**
	 * 지금 캐고 있는 블록. 없으면 {@code null}.
	 *
	 * <p>서버 스레드에서만 오간다. 그래도 다른 스레드가 읽을 때 반쯤 쓰인 값을 보지 않도록
	 * {@code volatile} 로 두고, 통째로 갈아 끼우는 불변 기록만 담는다.
	 * {@code ConditionalPerkManager.beginMultiplierLookup} 과 같은 방식이다.
	 */
	private static volatile @Nullable BlockContext blockContext;

	/** 누가 어디의 무슨 블록을 캐고 있는가. */
	private record BlockContext(UUID playerId, BlockPos pos, BlockState state) {
	}

	private ExperienceBonus() {
	}

	// ------------------------------------------------------------------ 상시 배율

	/** 지금 걸려 있는 배율. 설정을 아직 읽지 않았으면(시험·초기화 전) 1.0 이라 아무 일도 없다. */
	public static double multiplier() {
		SharedFateConfig config = SharedFateMod.config;
		if (config == null) {
			return 1.0;
		}
		double multiplier = config.experienceMultiplier;
		return Double.isFinite(multiplier) && multiplier > 0.0 ? multiplier : 1.0;
	}

	/** 설정에 적힌 배율로 경험치 양을 조정한다. */
	public static int scale(int amount) {
		return scale(amount, multiplier());
	}

	/**
	 * 경험치 양에 배율을 먹인다. 설정을 읽지 않는 순수 계산이라 서버 없이 시험할 수 있다.
	 *
	 * @param amount     바닐라가 주려던 양
	 * @param multiplier 곱할 배율
	 * @return 조정된 양. 0 이하는 그대로 두고, 1 이상은 반올림하되 최소 1 을 남긴다
	 */
	public static int scale(int amount, double multiplier) {
		if (amount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0.0 || multiplier == 1.0) {
			return amount;
		}
		long scaled = Math.round(amount * multiplier);
		if (scaled < 1L) {
			return 1;
		}
		return (int) Math.min(scaled, Integer.MAX_VALUE);
	}

	// ------------------------------------------------------------------ 출처별 배율

	/**
	 * 효과 목록에서 이 출처에 걸리는 배율을 <b>전부 곱한</b> 값.
	 *
	 * <p>{@code experience_bonus} 가 아닌 효과와 출처가 다른 효과는 그냥 지나간다. 하나도 없으면
	 * 1.0 이다. 게임 상태를 보지 않는 순수 계산이라 서버 없이 시험할 수 있다.
	 */
	public static double sourceMultiplier(@Nullable Iterable<PerkEffect> effects,
			@Nullable Source source) {
		if (effects == null || source == null) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkEffect effect : effects) {
			if (effect instanceof ExperienceBonusEffect bonus) {
				total *= bonus.multiplierFor(source);
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/**
	 * 이 팀이 이 출처에서 받는 배율.
	 *
	 * <p>보유 증강을 훑은 뒤 <b>세트 효과도 이어서 훑는다.</b> 이 두 번째 순회를 빠뜨리면
	 * 세트 보상만 조용히 아무 일도 하지 않는다 — 빌드도 통과하고 로그도 남지 않는다.
	 */
	public static double teamMultiplier(@Nullable TeamState state, @Nullable Source source) {
		if (state == null || source == null || !state.perksEnabled || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			total *= sourceMultiplier(perk.effects(), source);
		}
		// 세트가 건 배율도 같은 곱에 들어간다. 세트가 없으면 빈 목록이라 값이 그대로다.
		total *= sourceMultiplier(PerkSetEffects.activeEffectsOf(state), source);
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	/** 이 사람이 이 출처에서 받는 배율. 팀이 없거나 증강이 꺼져 있으면 1.0. */
	public static double playerMultiplier(@Nullable UUID playerId, @Nullable Source source) {
		if (playerId == null) {
			return 1.0;
		}
		return teamMultiplier(TeamLookup.stateOf(playerId), source);
	}

	// ------------------------------------------------------------------ 몹

	/**
	 * 몹이 떨어뜨릴 경험치에 사냥 배율을 먹인다.
	 *
	 * <p>{@code MobExperienceSourceMixin} 이 {@code LivingEntity.dropExperience} 안에서 부른다.
	 * 처치자가 사람이 아니거나({@code null}·몹끼리 싸움) 배율을 가진 팀이 아니면 그대로 둔다.
	 *
	 * @param killer {@code DamageSource.getEntity()} — 화살이면 쏜 사람이 들어온다
	 */
	public static int scaleMobExperience(int amount, @Nullable Entity killer) {
		if (amount <= 0 || !(killer instanceof Player player)) {
			return amount;
		}
		return scale(amount, playerMultiplier(player.getUUID(), Source.MOB));
	}

	// ------------------------------------------------------------------ 광물

	/**
	 * 이제부터 이 사람이 이 블록을 캐는 중이라고 적어 둔다.
	 *
	 * <p>{@code BlockExperienceSourceMixin} 이 {@code Block.playerDestroy} 에 들어갈 때 부른다.
	 * 서버의 사람이 아니면 적지 않는다 — 통합 서버의 클라이언트 쪽에서 같은 메서드가 불려도
	 * 서버 쪽 판정을 흔들지 않게 하기 위해서다.
	 */
	public static void beginBlockExperience(@Nullable Player player, @Nullable BlockPos pos,
			@Nullable BlockState state) {
		if (!(player instanceof ServerPlayer) || pos == null || state == null) {
			blockContext = null;
			return;
		}
		blockContext = new BlockContext(player.getUUID(), pos.immutable(), state);
	}

	/**
	 * 블록을 다 캤다고 알린다.
	 *
	 * <p>{@code Block.playerDestroy} 가 예외로 빠져나가면 이것이 불리지 않아 기록이 남는다.
	 * 그래도 다음 판정이 좌표까지 맞춰 보므로 엉뚱한 경험치에 배율이 붙지는 않는다.
	 */
	public static void endBlockExperience() {
		blockContext = null;
	}

	/**
	 * 블록에서 나온 경험치에 채굴 배율을 먹인다.
	 *
	 * <p>{@code BlockExperienceSourceMixin} 이 {@code Block.popExperience} 에서 부른다. 셋이 모두
	 * 맞아야 곱한다 — 캐는 중이고, <b>좌표가 같고</b>, 그 블록이 광물이어야 한다.
	 */
	public static int scaleBlockExperience(@Nullable BlockPos pos, int amount) {
		if (amount <= 0 || pos == null) {
			return amount;
		}
		BlockContext context = blockContext;
		if (context == null || !context.pos().equals(pos)) {
			return amount;
		}
		if (!ExperienceBonusEffect.isOre(context.state())) {
			return amount;
		}
		return scale(amount, playerMultiplier(context.playerId(), Source.ORE));
	}

	/** 지금 적혀 있는 블록 문맥이 있는가. 시험이 문맥이 새지 않는지 볼 때 쓴다. */
	public static boolean hasBlockContext() {
		return blockContext != null;
	}

	/** 서버가 멈출 때나 시험에서 상태를 격리할 때 쓴다. */
	public static void reset() {
		blockContext = null;
	}
}
