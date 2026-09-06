package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.BonusDropEffect;
import com.sharedfate.perk.effect.EchoMiningEffect;
import com.sharedfate.perk.effect.LuckyOreEffect;
import com.sharedfate.perk.effect.MiningSpeedEffect;
import com.sharedfate.perk.effect.OnBreakEffect;
import com.sharedfate.perk.effect.PairedMiningEffect;
import com.sharedfate.perk.effect.SameKindMiningEffect;
import com.sharedfate.sync.TitleMessenger;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * 블록 파괴에 걸리는 증강 효과({@code bonus_drop}, {@code on_break}, {@code mining_speed})의
 * 실행부.
 *
 * <p>효과 클래스들은 "무엇을 얼마나"만 들고 있고, "언제 누가 무엇을 캤는가"와 결과를 어디에
 * 넣는가는 전부 여기서 정한다.
 *
 * <h2>어느 순간에 끼어드는가</h2>
 * <p>{@code PlayerBlockBreakEvents.AFTER} 하나만 쓴다. Fabric 이 이 이벤트를 부르는 자리는
 * {@code ServerPlayerGameMode.destroyBlock} 안의 {@code Block.destroy(...)} 직후, 즉
 * <b>블록은 이미 사라졌지만 도구 손상과 전리품 지급은 아직인</b> 지점이다. 클라이언트에는
 * 같은 이름의 이벤트가 없으므로({@code ClientPlayerBlockBreakEvents} 는 별개다) 이 경로는
 * 서버에서만 지난다.
 *
 * <p>그 순서 때문에 지켜야 하는 것이 하나 있다. 이 시점에 도구를 부러뜨리면 곧바로 이어지는
 * {@code player.getMainHandItem()} 이 빈 손이 되고, {@code hasCorrectToolForDrops} 가 거짓이
 * 되어 <b>블록이 아무것도 떨어뜨리지 않는다</b>. 그래서 추가 내구도 소모는 도구를 절대 부러뜨리지
 * 않도록 최소 1 을 남긴다. 남은 1 은 바로 뒤의 {@code ItemStack.mineBlock} 이 평소대로 가져가고,
 * 도구는 바닐라와 똑같은 자리에서 똑같은 소리와 함께 부러진다.
 *
 * <h2>추가 드롭은 어디로 가는가</h2>
 * <p>{@link Block#popResource} 로 <b>캔 자리에 아이템으로 떨어뜨린다.</b> 공유 인벤토리에 직접
 * 밀어 넣지 않는다.
 *
 * <p>이 모드는 팀원의 인벤토리 칸 목록 자체를 {@code TeamState.mainItems} 로 갈아 끼워
 * 공유한다({@code InventorySwapper.finishJoin}). 즉 <b>바닥에 떨어진 아이템을 주우면 그것이
 * 곧 팀 공유 인벤토리에 들어간 것</b>이다.
 *
 * <h2>증강이 없으면 아무 일도 하지 않는다</h2>
 * <p>블록을 캘 때마다 지나는 자리이므로 빠져나가는 길이 짧아야 한다. 팀이 없거나 보유 증강이
 * 비어 있으면 증강 풀을 들여다보지도 않고 곧바로 돌아간다. 어떤 예외도 밖으로 내보내지 않는다.
 * 증강 하나가 잘못돼 블록 파괴가 멈추면 안 된다.
 *
 * <h2>연쇄는 연쇄를 부르지 않는다</h2>
 * <p>{@code echo_mining}·{@code same_kind_mining} 은 이 사건 안에서 <b>다른 블록을 부순다.</b>
 * 그 파괴가 이 사건을 다시 부르면 고리가 닫히고, <b>둘 다 같은 종류만 캐므로</b> 이웃이 같은
 * 종류일수록 잘 걸린다 — 돌밭 한복판에서 한 번만 캐도 <b>도미노가 끝나지 않아 서버가 멈춘다.</b>
 * 막는 장치가 두 겹이다.
 *
 * <ol>
 *   <li><b>{@code ServerLevel.removeBlock} 을 쓴다.</b> {@code destroyBlock} 이 아니다.
 *       {@code PlayerBlockBreakEvents.AFTER} 는 {@code ServerPlayerGameMode.destroyBlock} 안에서
 *       발화하므로, 그 경로를 지나지 않는 {@code removeBlock} 은 애초에 이 사건을 다시 부르지
 *       않는다.</li>
 *   <li><b>{@link #beginChain()} 재진입 표시.</b> 그래도 이 사건이 처리되는 <b>동안</b> 같은
 *       스레드에서 이 진입점이 다시 불리면 곧바로 돌아간다. 다른 모드가 같은 이벤트를 쏘거나
 *       나중에 누군가 {@code removeBlock} 을 {@code destroyBlock} 으로 바꿔 적어도 고리가 닫히지
 *       않게 하려는 안전판이다.</li>
 * </ol>
 */
public final class PerkBlockBreaks {
	private static volatile boolean warned;

	/**
	 * 지금 이 스레드가 블록 파괴 증강을 처리하는 중인가.
	 *
	 * <p>스레드마다 따로 둔다. 서버 스레드에서만 오가는 것이 정상이지만, 전역 플래그로 두면
	 * 다른 스레드가 켜 놓은 표시 때문에 정작 서버 스레드의 증강이 통째로 사라질 수 있다.
	 * 그런 사고는 조용해서 알아채기가 매우 어렵다.
	 */
	private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> Boolean.FALSE);

	private PerkBlockBreaks() {
	}

	// ------------------------------------------------------------------ 등록 지점

	/**
	 * {@code PlayerBlockBreakEvents.AFTER} 에 붙는 지점.
	 *
	 * @param blockEntity 블록이 사라지기 전에 Fabric 이 잡아 둔 블록 엔티티. 없으면 null
	 */
	public static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity) {
		// 증강이 일으킨 파괴가 이 자리를 다시 밟았다면 아무 일도 하지 않는다. 클래스 문서의
		// 「연쇄는 연쇄를 부르지 않는다」 참고.
		if (!beginChain()) {
			return;
		}
		try {
			handleBreak(level, player, pos, state, blockEntity);
		} catch (RuntimeException error) {
			warnOnce(error);
		} finally {
			endChain();
		}
	}

	/**
	 * 연쇄 처리에 들어간다고 표시하고, 들어가도 되는지 알려 준다.
	 *
	 * <p>{@code false} 를 받으면 <b>{@link #endChain()} 을 부르면 안 된다.</b> 표시를 켠 것은
	 * 바깥쪽 호출이고, 안쪽이 끄면 그 뒤로 고리가 다시 열린다.
	 *
	 * <p>좌표도 월드도 보지 않는 순수한 상태 전이라 살아 있는 서버 없이 시험할 수 있다.
	 *
	 * @return 처음 들어온 것이면 {@code true}, 이미 처리 중이면 {@code false}
	 */
	static boolean beginChain() {
		if (Boolean.TRUE.equals(CHAINING.get())) {
			return false;
		}
		CHAINING.set(Boolean.TRUE);
		return true;
	}

	/** 연쇄 처리에서 빠져나온다. {@link #beginChain()} 이 {@code true} 를 준 쪽만 부른다. */
	static void endChain() {
		CHAINING.set(Boolean.FALSE);
	}

	/** 지금 이 스레드가 연쇄를 처리하는 중인가. */
	static boolean isChaining() {
		return Boolean.TRUE.equals(CHAINING.get());
	}

	private static void handleBreak(Level level, Player player, BlockPos pos, BlockState state,
			@Nullable BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel)
				|| !(player instanceof ServerPlayer breaker)
				|| state == null || pos == null) {
			return;
		}
		TeamState teamState = TeamLookup.stateOf(breaker.getUUID());
		if (teamState == null || !teamState.perksEnabled || teamState.ownedPerks.isEmpty()) {
			return;
		}

		for (String perkId : teamState.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				handleEffect(serverLevel, breaker, pos, state, blockEntity, effect, perk.name());
			}
		}
		// 켜진 세트도 같은 규칙으로 지난다. 채굴 3단계의 bonus_drop 이 이 길을 탄다.
		// 세트가 없으면 빈 목록이라 블록을 캘 때마다 얹히는 부담이 없다.
		for (PerkSets.Tier tier : PerkSetEffects.activeTiersOf(teamState)) {
			for (PerkEffect effect : tier.effects()) {
				handleEffect(serverLevel, breaker, pos, state, blockEntity, effect, tier.name());
			}
		}
	}

	/**
	 * 효과 하나를 이 블록 파괴에 적용한다.
	 *
	 * @param sourceName 알림에 쓸 이름. 증강이면 증강 이름, 세트면 그 단계의 이름이다
	 */
	private static void handleEffect(ServerLevel serverLevel, ServerPlayer breaker, BlockPos pos,
			BlockState state, @Nullable BlockEntity blockEntity, PerkEffect effect,
			String sourceName) {
		if (effect instanceof OnBreakEffect onBreak && onBreak.appliesTo(state)) {
			onBreak.grantTemporaryEffects(breaker);
		} else if (effect instanceof BonusDropEffect bonus && bonus.appliesTo(state)) {
			tryBonusDrop(serverLevel, breaker, pos, state, blockEntity, bonus);
		} else if (effect instanceof PairedMiningEffect) {
			PerkResonantMining.onBreak(serverLevel.getServer(), breaker, state, serverLevel.getGameTime());
		} else if (effect instanceof EchoMiningEffect) {
			tryEchoMining(serverLevel, breaker, pos, state);
		} else if (effect instanceof SameKindMiningEffect sameKind) {
			trySameKindMining(serverLevel, breaker, pos, state, sameKind);
		} else if (effect instanceof LuckyOreEffect lucky && lucky.appliesTo(state)) {
			tryLuckyOre(serverLevel, breaker, pos, state, blockEntity, sourceName, lucky);
		}
	}

	// ------------------------------------------------------------------ 이웃 채굴 공통

	/**
	 * 방금 캔 자리 주변을 훑는 반경(블록). 3×3×3 이웃(가운데 제외) 26칸이다.
	 *
	 * <p>{@code echo_mining} 과 {@code same_kind_mining} 이 같은 값을 쓴다.
	 */
	private static final int NEIGHBOR_SEARCH_RADIUS = 1;

	/**
	 * 이웃을 거를 기준이 될 「방금 캔 블록」. <b>두 이웃 채굴 효과가 함께 쓴다.</b>
	 *
	 * <p>기준을 정할 수 없으면 {@code null} 이고, 그때 부르는 쪽은 <b>아무것도 더 캐지 않는다.</b>
	 * {@code null} 을 그대로 {@link #neighborCandidates} 에 넘기면 뜻이 뒤집혀 「종류를 안 가림」이
	 * 되어 주변 아무 블록이나 캐진다. 그래서 기준이 없을 때는 돌아가는 쪽이 유일하게 안전하다.
	 *
	 * <p>캔 자리를 월드에서 다시 읽으면 안 되기 때문에 상태를 인자로 받는다. 이 시점에 그 자리는
	 * <b>이미 공기</b>다.
	 *
	 * <p>레지스트리도 월드도 보지 않는 순수 판정이라 살아 있는 서버 없이 시험할 수 있다.
	 *
	 * @param originState 방금 캔 블록의 상태
	 * @return 그 상태 그대로. 상태를 못 받았거나 이미 공기면 {@code null}
	 */
	static @Nullable BlockState kindFilterOf(@Nullable BlockState originState) {
		if (originState == null || originState.isAir()) {
			return null;
		}
		return originState;
	}

	/**
	 * 방금 캔 자리를 둘러싼 26칸 중 조건에 맞는 것을 좌표 순서대로 모은다.
	 *
	 * <p><b>순수 계산이다.</b> 월드도 플레이어도 보지 않고, 블록 상태를 어디서 가져오는지와
	 * 「캘 수 있는 칸인가」를 어떻게 판단하는지를 전부 인자로 받는다. {@code echo_mining} 과
	 * {@code same_kind_mining} 이 <b>같은 이 함수</b>를 쓰고, 「같은 종류만 캔다」는 규칙도
	 * 마지막 인자 하나로 <b>함께</b> 쓴다.
	 *
	 * @param origin     방금 캔 자리
	 * @param stateAt    한 칸의 블록 상태를 가져온다. <b>로드되지 않은 자리에는 {@code null} 을
	 *                   돌려준다</b> — 청크를 억지로 불러오지 않는다는 규칙이 여기로 들어온다
	 * @param breakable  그 칸을 실제로 캐도 되는가. 발밑 보호·캘 수 없는 블록·도구 등급을 본다
	 * @param sameKindAs 이 블록과 {@link SameKindMiningEffect#isSameKind 같은 종류}인 칸만
	 *                   남긴다. <b>{@code null} 은 「종류를 안 가림」이라 주변 아무 블록이나
	 *                   걸린다</b> — 두 효과 모두 {@link #kindFilterOf} 로 기준을 잡아 넘기므로
	 *                   실제로 {@code null} 이 들어오는 길은 없다
	 * @return 새로 만든 목록. 부르는 쪽이 마음대로 고쳐도 된다. 없으면 빈 목록
	 */
	static List<BlockPos> neighborCandidates(@Nullable BlockPos origin,
			@Nullable Function<BlockPos, BlockState> stateAt,
			@Nullable BiPredicate<BlockPos, BlockState> breakable,
			@Nullable BlockState sameKindAs) {
		List<BlockPos> candidates = new ArrayList<>();
		if (origin == null || stateAt == null || breakable == null) {
			return candidates;
		}
		for (int dx = -NEIGHBOR_SEARCH_RADIUS; dx <= NEIGHBOR_SEARCH_RADIUS; dx++) {
			for (int dy = -NEIGHBOR_SEARCH_RADIUS; dy <= NEIGHBOR_SEARCH_RADIUS; dy++) {
				for (int dz = -NEIGHBOR_SEARCH_RADIUS; dz <= NEIGHBOR_SEARCH_RADIUS; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					BlockPos candidate = origin.offset(dx, dy, dz);
					BlockState candidateState = stateAt.apply(candidate);
					// null 은 로드되지 않은 자리다.
					if (candidateState == null || candidateState.isAir()) {
						continue;
					}
					if (sameKindAs != null
							&& !SameKindMiningEffect.isSameKind(sameKindAs, candidateState)) {
						continue;
					}
					if (!breakable.test(candidate, candidateState)) {
						continue;
					}
					candidates.add(candidate);
				}
			}
		}
		return candidates;
	}

	/**
	 * 26칸에서 실제로 캘 자리를 무작위로 {@code count} 개까지 고른다.
	 *
	 * <p>다음은 후보에서 뺀다.
	 * <ul>
	 *   <li>공기, 로드되지 않은 자리, 캘 수 없는 블록({@code getDestroySpeed} 가 음수)</li>
	 *   <li>도구가 맞지 않는 블록({@code hasCorrectToolForDrops})</li>
	 *   <li><b>팀원(캔 사람 자신 포함)의 발밑 블록</b> — 까닭은 {@link EchoMiningEffect} 에 적어
	 *       뒀다. 요약하면 밟고 선 칸이 사라지면 그 사람이 떨어져 죽고, 이 모드는 체력을
	 *       공유하므로 그 사고가 팀 전체를 죽인다.</li>
	 *   <li><b>{@code sameKindAs} 와 다른 종류인 블록</b> — 두 이웃 채굴 효과가 모두 방금 캔
	 *       블록을 넘기므로, 돌 사이의 다이아를 캐면 돌은 여기서 전부 빠진다</li>
	 * </ul>
	 *
	 * <p>후보가 {@code count} 보다 적으면 있는 만큼만 돌려준다. 하나도 없으면 빈 목록이다.
	 */
	private static List<BlockPos> pickTargets(ServerLevel level, ServerPlayer breaker, BlockPos origin,
			int count, @Nullable BlockState sameKindAs) {
		if (count <= 0) {
			return List.of();
		}
		List<BlockPos> protectedFeet = feetPositions(level, breaker);
		List<BlockPos> candidates = neighborCandidates(
				origin,
				pos -> level.isLoaded(pos) ? level.getBlockState(pos) : null,
				(pos, state) -> !isUnderFoot(pos, protectedFeet)
						&& state.getDestroySpeed(level, pos) >= 0.0F
						&& breaker.hasCorrectToolForDrops(state),
				sameKindAs);
		if (candidates.isEmpty()) {
			return List.of();
		}

		int wanted = Math.min(count, candidates.size());
		List<BlockPos> picked = new ArrayList<>(wanted);
		for (int i = 0; i < wanted; i++) {
			picked.add(candidates.remove(level.getRandom().nextInt(candidates.size())));
		}
		return picked;
	}

	/**
	 * 고른 자리를 실제로 지우고 전리품을 그 자리에 떨어뜨린다.
	 *
	 * <p>{@code destroyBlock} 이 아니라 {@link ServerLevel#removeBlock} 을 쓴다. 까닭은 클래스
	 * 문서의 「연쇄는 연쇄를 부르지 않는다」에 적어 뒀다.
	 */
	private static void breakExtraBlocks(ServerLevel level, ServerPlayer breaker,
			List<BlockPos> targets) {
		for (BlockPos target : targets) {
			BlockState targetState = level.getBlockState(target);
			BlockEntity targetBlockEntity = level.getBlockEntity(target);
			List<ItemStack> drops = Block.getDrops(
					targetState, level, target, targetBlockEntity, breaker, breaker.getMainHandItem());
			level.removeBlock(target, false);
			for (ItemStack drop : drops) {
				if (drop != null && !drop.isEmpty()) {
					Block.popResource(level, target, drop);
				}
			}
		}
	}

	// ------------------------------------------------------------------ 메아리 채굴

	/**
	 * 방금 캔 것과 <b>같은 종류</b>인 이웃 블록을 {@value EchoMiningEffect#EXTRA_BLOCKS} 개 더
	 * 캔다.
	 *
	 * <p>「같은 종류」의 뜻은 {@link SameKindMiningEffect#isSameKind} 한 곳에만 있다. 요약하면
	 * <b>블록 종류만 보고 블록 상태는 보지 않으며, 딥슬레이트 변종은 다른 종류</b>다. 돌 사이의
	 * 다이아를 캐면 돌은 그대로 있고 붙어 있는 다이아만 함께 캐진다. 같은 종류가 여럿이면 그중
	 * 무작위로 고른다.
	 *
	 * <p>기준이 될 블록은 {@link #kindFilterOf} 로 잡는다. 기준이 없으면(캔 자리를 알 수 없거나
	 * 이미 공기면) <b>아무것도 더 캐지 않는다.</b>
	 *
	 * <p>{@code destroyBlock} 이 아니라 {@link ServerLevel#removeBlock} 을 쓴다. 이 메서드는
	 * {@code PlayerBlockBreakEvents.AFTER} 를 다시 발화시키지 않으므로, 이 사건이 자기 자신을
	 * 다시 부르는 고리가 애초에 생기지 않는다.
	 *
	 * <p>로드되지 않은 청크의 블록은 후보에서 빠진다. 청크를 억지로 불러오지 않는다
	 * ({@link ServerLevel#isLoaded} 는 조회만 하고 불러오지는 않는다).
	 *
	 * <p><b>내구도는 몇 개를 캤든 정확히 1점만 더 먹인다.</b> 원래 소모 1점은 바닐라가 이 사건
	 * 뒤에 처리하므로 합계가 정확히 2배가 된다. 추가 파괴 수를 2개로 늘렸다고 소모까지 2점으로
	 * 올리면 「내구도 2배」라는 약속이 깨지고 도구가 순식간에 사라진다.
	 *
	 * <p>{@code originState} 를 월드에서 다시 읽지 않고 인자로 받는 것이 중요하다. 이 시점에
	 * 캔 자리는 <b>이미 공기</b>여서 월드에서 읽으면 아무것과도 같은 종류가 아니게 된다.
	 */
	private static void tryEchoMining(ServerLevel level, ServerPlayer breaker, BlockPos origin,
			BlockState originState) {
		BlockState kind = kindFilterOf(originState);
		if (kind == null) {
			return;
		}
		List<BlockPos> targets = pickEchoTargets(
				level, breaker, origin, EchoMiningEffect.EXTRA_BLOCKS, kind);
		if (targets.isEmpty()) {
			return;
		}
		breakExtraBlocks(level, breaker, targets);
		spendExtraDurability(breaker, 1);
	}

	/**
	 * 「메아리 채굴」이 캘 자리를 고른다. <b>{@code sameKindAs} 와 같은 종류만 고른다.</b>
	 *
	 * <p>거르는 규칙은 {@link #pickTargets} 에 적어 뒀다. 기준 블록은 부르는 쪽이
	 * {@link #kindFilterOf} 로 잡아서 넘긴다 — 여기로 {@code null} 이 들어오면 종류를 안 가리게
	 * 되므로 그 판단을 이 함수에 맡기지 않는다.
	 */
	static List<BlockPos> pickEchoTargets(ServerLevel level, ServerPlayer breaker, BlockPos origin,
			int count, BlockState sameKindAs) {
		return pickTargets(level, breaker, origin, count, sameKindAs);
	}

	// ------------------------------------------------------------------ 같은 종류 채굴

	/**
	 * 방금 캔 것과 <b>같은 종류</b>인 이웃 블록을 함께 캔다. 세트 「채굴 4단계」가 쓴다.
	 *
	 * <p>「같은 종류」의 뜻은 {@link SameKindMiningEffect#isSameKind} 한 곳에만 있다. 요약하면
	 * <b>블록 종류만 보고 블록 상태는 보지 않으며, 딥슬레이트 변종은 다른 종류</b>다.
	 *
	 * <h2>「메아리 채굴」과 겹치면 어떻게 되는가</h2>
	 * <p><b>둘 다 발동한다. 합쳐서 최대 4칸이고 내구도도 각각 문다(원래 1 + 1 + 1 = 3배).</b>
	 *
	 * <p>같은 칸을 두 번 캐는 일은 없다. {@link #handleEffect} 는 보유 증강을 먼저, 세트를
	 * 나중에 지나므로 「메아리 채굴」이 먼저 두 칸을 지우고, 그 뒤에 이 효과가 후보를 <b>새로
	 * 훑는다</b> — 이미 지워진 칸은 공기라 후보에서 빠진다. 그래서 겹치는 만큼 실제로 캐지는
	 * 수가 줄 뿐, 같은 블록의 전리품이 두 번 나오지는 않는다.
	 *
	 * <p>여기서 {@code originState} 를 다시 읽지 않고 인자로 받는 것이 중요하다. 이 시점에
	 * 캔 자리는 <b>이미 공기</b>여서 월드에서 읽으면 아무것과도 같은 종류가 아니게 된다.
	 */
	private static void trySameKindMining(ServerLevel level, ServerPlayer breaker, BlockPos origin,
			BlockState originState, SameKindMiningEffect effect) {
		BlockState kind = kindFilterOf(originState);
		if (kind == null) {
			return;
		}
		List<BlockPos> targets = pickTargets(
				level, breaker, origin, effect.extraBlocks(), kind);
		if (targets.isEmpty()) {
			return;
		}
		breakExtraBlocks(level, breaker, targets);
		spendExtraDurability(breaker, effect.extraDurability());
	}

	/**
	 * 이 좌표가 누군가의 발밑인가.
	 *
	 * <p>{@code standing} 은 각 플레이어의 {@code blockPosition()} 이다. 그 칸과 <b>그 아래 한
	 * 칸</b> 둘 다 발밑으로 친다 — 보통 서 있을 때 딛고 선 것은 아래 칸이지만, 반 블록·계단·눈처럼
	 * 높이가 1보다 낮은 블록 위에서는 {@code blockPosition()} 자체가 딛고 선 블록이 된다.
	 *
	 * <p>마인크래프트 좌표만 쓰는 순수 계산이라 살아 있는 서버 없이 시험할 수 있다.
	 */
	static boolean isUnderFoot(@Nullable BlockPos candidate, @Nullable List<BlockPos> standing) {
		if (candidate == null || standing == null || standing.isEmpty()) {
			return false;
		}
		for (BlockPos feet : standing) {
			if (feet == null) {
				continue;
			}
			if (candidate.equals(feet) || candidate.equals(feet.below())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 같은 차원에 접속해 있는 팀원(캔 사람 포함)이 서 있는 칸들.
	 *
	 * <p>팀이 없으면 캔 사람 하나뿐이다 — 자기 발밑을 캐서 자기가 떨어지는 것도 똑같이 막아야
	 * 한다. 다른 차원의 팀원은 애초에 이 후보 좌표와 겹칠 일이 없으므로 넣지 않는다.
	 */
	private static List<BlockPos> feetPositions(ServerLevel level, ServerPlayer breaker) {
		List<BlockPos> feet = new ArrayList<>(4);
		feet.add(breaker.blockPosition());

		MinecraftServer server = level.getServer();
		if (server == null) {
			return feet;
		}
		ShareTeam team = TeamManager.get(server).teamOf(breaker.getUUID());
		if (team == null) {
			return feet;
		}
		for (UUID member : team.members()) {
			if (member.equals(breaker.getUUID())) {
				continue;
			}
			ServerPlayer teammate = server.getPlayerList().getPlayer(member);
			if (teammate != null && !teammate.isRemoved() && teammate.level() == level) {
				feet.add(teammate.blockPosition());
			}
		}
		return feet;
	}

	// ------------------------------------------------------------------ 추가 드롭

	/**
	 * 확률을 굴려 성공하면 드롭을 하나 더 떨어뜨리고 도구를 더 닳게 한다.
	 *
	 * <p>바닐라가 아무것도 떨어뜨리지 않을 상황에서는 증강도 아무것도 주지 않는다. 두 가지를
	 * 본다.
	 * <ul>
	 *   <li>{@code preventsBlockDrops()} — 크리에이티브다. 바닐라도 이 뒤에서 곧바로 돌아간다.</li>
	 *   <li>{@code hasCorrectToolForDrops(state)} — 맨손으로 철광석을 캔 것처럼 등급이 모자란
	 *       도구다. 이때 바닐라는 {@code playerDestroy} 를 부르지 않아 전리품이 없다.</li>
	 * </ul>
	 */
	private static void tryBonusDrop(ServerLevel level, ServerPlayer breaker, BlockPos pos,
			BlockState state, @Nullable BlockEntity blockEntity, BonusDropEffect effect) {
		if (breaker.preventsBlockDrops() || !breaker.hasCorrectToolForDrops(state)) {
			return;
		}
		double chance = effect.chanceFor();
		if (chance <= 0.0) {
			return;
		}
		RandomSource random = level.getRandom();
		if (chance < 1.0 && random.nextDouble() >= chance) {
			return;
		}

		// extra 가 1이면(기본값) 한 번만 굴려 하나 준다. 그보다 크면
		// 성공할 때마다 그 횟수만큼 다시 굴려 매번 하나씩 떨어뜨린다("비옥한 땅"의 3배 등).
		int granted = 0;
		for (int i = 0; i < effect.extra(); i++) {
			ItemStack bonus = rollBonusStack(level, breaker, pos, state, blockEntity);
			if (bonus == null || bonus.isEmpty()) {
				// 전리품표가 이번엔 아무것도 주지 않았다(자갈→부싯돌 같은 경우).
				continue;
			}
			Block.popResource(level, pos, bonus);
			granted++;
		}
		if (granted > 0) {
			spendExtraDurability(breaker, effect.extraDurability());
		}
	}

	/**
	 * 이 블록의 전리품표를 지금 든 도구로 한 번 더 굴려 그중 하나를 개수 1 로 잘라 온다.
	 *
	 * <p>전리품표를 쓰므로 섬세한 손길이면 광석 블록이, 아니면 원석이 나온다. 행운은 굴린 값에
	 * 이미 반영돼 있지만 개수를 1 로 자르기 때문에 증강분이 행운 배수만큼 불어나지는 않는다.
	 * 증강은 언제나 정확히 "하나 더"다.
	 */
	private static @Nullable ItemStack rollBonusStack(ServerLevel level, ServerPlayer breaker,
			BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		List<ItemStack> drops = Block.getDrops(
				state, level, pos, blockEntity, breaker, breaker.getMainHandItem());
		if (drops == null) {
			return null;
		}
		for (ItemStack drop : drops) {
			if (drop != null && !drop.isEmpty()) {
				return drop.copyWithCount(1);
			}
		}
		return null;
	}

	/**
	 * 손에 든 도구를 추가로 닳게 한다.
	 *
	 * <p>{@code hurtAndBreak} 에 그대로 넘기므로 내구성(Unbreaking) 마법과 {@code unbreakable}
	 * 성분이 붙은 도구는 바닐라 규칙 그대로 덜 닳거나 아예 닳지 않는다.
	 *
	 * <p>다만 <b>여기서 도구를 부러뜨리지는 않는다.</b> 남은 내구도에서 1 을 남기고 그만큼만
	 * 깎는다. 이 시점에 도구가 사라지면 바로 뒤의 {@code hasCorrectToolForDrops} 가 거짓이 되어
	 * 방금 캔 블록의 전리품이 통째로 사라지기 때문이다. 남긴 1 은 바닐라의 {@code mineBlock} 이
	 * 이어서 가져가므로, 내구도를 다 쓴 도구는 이번 블록에서 평소대로 부러진다.
	 */
	private static void spendExtraDurability(ServerPlayer breaker, int amount) {
		if (amount <= 0) {
			return;
		}
		ItemStack tool = breaker.getMainHandItem();
		if (tool.isEmpty() || !tool.isDamageableItem()) {
			return;
		}
		int allowed = allowedExtraDurability(
				amount, tool.getMaxDamage() - tool.getDamageValue());
		if (allowed <= 0) {
			return;
		}
		tool.hurtAndBreak(allowed, breaker, EquipmentSlot.MAINHAND);
	}

	/**
	 * 도구를 부러뜨리지 않고 추가로 깎을 수 있는 양.
	 *
	 * @param amount    증강이 요구한 추가 소모량
	 * @param remaining 지금 남아 있는 내구도
	 * @return 실제로 깎을 양. 언제나 {@code remaining} 보다 작아 도구가 여기서 부러지지 않는다
	 */
	static int allowedExtraDurability(int amount, int remaining) {
		if (amount <= 0 || remaining <= 1) {
			return 0;
		}
		return Math.max(0, Math.min(amount, remaining - 1));
	}

	// ------------------------------------------------------------------ 운수 좋은 날

	/**
	 * 광물을 캘 때마다 0~3개를 더 떨어뜨리고, 더 나왔을 때만 캔 사람에게 알린다.
	 *
	 * <p>{@link #tryBonusDrop} 과 같은 자리·같은 방식이다. 크리에이티브이거나 도구 등급이
	 * 모자라 바닐라가 아무것도 떨어뜨리지 않을 상황에서는 증강도 아무것도 주지 않고, 난수도
	 * 굴리지 않는다. 추가분은 {@link #rollBonusStack} 이 전리품표를 다시 굴려 만들므로 행운·섬세한
	 * 손길과의 관계도 {@code bonus_drop} 과 똑같다.
	 *
	 * <p>도구를 더 닳게 하지는 않는다.
	 *
	 * <p>알림은 실제로 하나라도 나왔을 때만 나가고, 캔 사람에게만 간다. 이름은 처음 나온 것을
	 * 쓴다 — 같은 블록의 전리품표를 여러 번 굴린 것이라 광석에서는 언제나 같은 아이템이 나온다.
	 */
	private static void tryLuckyOre(ServerLevel level, ServerPlayer breaker, BlockPos pos,
			BlockState state, @Nullable BlockEntity blockEntity, String sourceName,
			LuckyOreEffect effect) {
		if (breaker.preventsBlockDrops() || !breaker.hasCorrectToolForDrops(state)) {
			return;
		}
		int extra = effect.rollExtra(level.getRandom());
		if (extra <= 0) {
			return;
		}

		Component itemName = null;
		int granted = 0;
		for (int i = 0; i < extra; i++) {
			ItemStack bonus = rollBonusStack(level, breaker, pos, state, blockEntity);
			if (bonus == null || bonus.isEmpty()) {
				// 전리품표가 이번엔 아무것도 주지 않았다.
				continue;
			}
			if (itemName == null) {
				itemName = bonus.getHoverName();
			}
			Block.popResource(level, pos, bonus);
			granted++;
		}
		if (granted > 0) {
			TitleMessenger.showActionBar(
					breaker, LuckyOreEffect.announcement(sourceName, itemName, granted));
		}
	}

	// ------------------------------------------------------------------ 채굴 속도

	/**
	 * {@code Player.getDestroySpeed} 가 내놓은 값에 {@code mining_speed} 배율을 곱한다.
	 *
	 * <p>{@code PlayerMiningSpeedMixin} 이 부른다. 팀에 속하지 않았거나 보유 증강이 없으면
	 * 받은 값을 그대로 돌려주므로 바닐라와 완전히 같다.
	 *
	 * <p>{@code TeamLookup.serverStateOf} 는 {@code ServerPlayer} 가 아니면 null 이다. 이
	 * mixin 은 공용 설정에 들어 있어 클라이언트의 {@code LocalPlayer} 에도 걸리지만, 그쪽에서는
	 * 첫 줄에서 곧바로 원래 값이 나온다.
	 *
	 * @param base {@code getDestroySpeed} 의 원래 반환값
	 * @return 배율을 먹인 값. 해당 없으면 {@code base} 그대로
	 */
	public static float scaleDestroySpeed(@Nullable Player player, @Nullable BlockState state,
			float base) {
		if (player == null || state == null || !(base > 0.0F) || !Float.isFinite(base)) {
			return base;
		}
		try {
			double multiplier = multiplierFor(player, state);
			if (multiplier == 1.0) {
				return base;
			}
			float scaled = (float) (base * multiplier);
			// 0 이나 음수가 되면 그 블록을 영영 캘 수 없다. 그럴 바에는 원래 값이 낫다.
			return Float.isFinite(scaled) && scaled > 0.0F ? scaled : base;
		} catch (RuntimeException error) {
			warnOnce(error);
			return base;
		}
	}

	/** 이 플레이어가 속한 팀이 이 블록에 걸어 둔 채굴 속도 배율. 팀이 없으면 1.0. */
	static double multiplierFor(@Nullable Player player, @Nullable BlockState state) {
		return multiplierFor(TeamLookup.serverStateOf(player), state);
	}

	/**
	 * 이 팀이 이 블록에 걸어 둔 채굴 속도 배율의 곱.
	 *
	 * <p>걸리는 효과가 없으면 1.0 이다.
	 */
	static double multiplierFor(@Nullable TeamState teamState, @Nullable BlockState state) {
		if (teamState == null || !teamState.perksEnabled || teamState.ownedPerks.isEmpty()
				|| state == null) {
			return 1.0;
		}
		double multiplier = 1.0;
		for (String perkId : teamState.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof MiningSpeedEffect mining && mining.appliesTo(state)) {
					multiplier *= mining.multiplierFor();
				}
			}
		}
		return multiplier;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"블록 파괴 증강을 처리하지 못해 이번에는 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}

	/** 테스트가 상태를 격리할 때 쓴다. 연쇄 표시도 함께 끈다. */
	static void resetForTesting() {
		warned = false;
		endChain();
	}
}
