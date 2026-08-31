package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.PairedMiningEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code paired_mining} 증강(실버 「공명」)의 실행부.
 *
 * <p>{@link PairedMiningEffect}는 값(거리·기록 유효 시간·성급함 세기·페널티 배율)만 들고
 * 있고, "누가 언제 무엇을 캤는가"와 "지금 혼자인가"는 전부 여기서 정한다. {@code on_kill}과
 * {@link PerkKillRewards}의 관계와 같은 구도다.
 *
 * <h2>두 갈래로 나뉜다</h2>
 * <ul>
 *   <li><b>짝 성사(사건)</b> — {@link #onBreak}가 {@code PerkBlockBreaks}에서 캘 때마다
 *       불린다. 플레이어별로 "마지막으로 캔 블록·시각"만 기억해 뒀다가, 16칸 안의 팀원이
 *       5초 안에 같은 블록을 캤으면 둘 다 성급함 I 을 5초 건다.</li>
 *   <li><b>혼자 페널티(주기)</b> — {@link #tick}이 1초마다 이 증강을 가진 팀을 훑어, 16칸 안에
 *       팀원이 아무도 없는 사람에게 채굴 속도 −15% 속성 수정자를 걸거나 뗀다.
 *       {@code AttributeEffect}가 쓰는 것과 같은 수정자 부착·제거 방식이다.</li>
 * </ul>
 *
 * <h2>기억은 저장하지 않는다</h2>
 * <p>"마지막으로 캤다"·"지금 페널티가 걸려 있다"는 서버가 켜져 있는 동안만 뜻이 있는 값이다.
 * {@link PerkHolderManager}의 보유자 기억과 같은 이유로 저장하지 않고, 서버가 멈추면
 * {@link #reset}이 비운다.
 */
public final class PerkResonantMining {
	private static final Identifier SOLO_PENALTY_MODIFIER_ID =
			SharedFateMod.id("perk/paired_mining/solo_penalty");
	private static final Identifier BLOCK_BREAK_SPEED_ID =
			Identifier.fromNamespaceAndPath("minecraft", "block_break_speed");
	/** 혼자 페널티를 다시 확인하는 주기. 1초다. */
	private static final int PENALTY_CHECK_TICKS = 20;

	/** 플레이어별 마지막으로 캔 블록 기록. */
	private static final Map<UUID, LastBreak> LAST_BREAKS = new ConcurrentHashMap<>();
	/** 지금 혼자 페널티가 걸려 있다고 표시해 둔 사람들. 매초 add/remove 를 반복하지 않으려고 둔다. */
	private static final Set<UUID> PENALIZED = ConcurrentHashMap.newKeySet();

	private static int tickCounter;
	private static boolean warned;
	private static @Nullable Holder<Attribute> cachedAttribute;
	private static boolean attributeResolveFailed;

	private PerkResonantMining() {
	}

	/** 패키지 안 시험이 직접 만들 수 있도록 접근 제한자를 두지 않는다. */
	record LastBreak(Block block, long tick) {
	}

	// ------------------------------------------------------------------ 짝 성사

	/** {@code PerkBlockBreaks}가 캘 때마다 부르는 지점. */
	public static void onBreak(MinecraftServer server, ServerPlayer breaker, BlockState state, long gameTime) {
		try {
			handleOnBreak(server, breaker, state, gameTime);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void handleOnBreak(MinecraftServer server, ServerPlayer breaker, BlockState state,
			long gameTime) {
		Block block = state.getBlock();
		UUID breakerId = breaker.getUUID();

		ShareTeam team = TeamManager.get(server).teamOf(breakerId);
		if (team != null) {
			double limitSquared = PairedMiningEffect.DISTANCE * PairedMiningEffect.DISTANCE;
			for (UUID memberId : team.members()) {
				if (memberId.equals(breakerId)) {
					continue;
				}
				ServerPlayer teammate = server.getPlayerList().getPlayer(memberId);
				if (teammate == null || teammate.isRemoved() || teammate.level() != breaker.level()) {
					continue;
				}
				if (!recordMatches(LAST_BREAKS.get(memberId), block, gameTime)) {
					continue;
				}
				if (breaker.distanceToSqr(teammate) > limitSquared) {
					continue;
				}
				grantHaste(breaker);
				grantHaste(teammate);
				break; // 한 쌍만 성사시키면 이 사건의 뜻은 충분하다.
			}
		}

		LAST_BREAKS.put(breakerId, new LastBreak(block, gameTime));
	}

	/**
	 * 최근 캔 기록이 지금 캔 블록과 짝을 이룰 수 있는가. 순수 계산이라 월드 없이 시험할 수 있게
	 * 따로 뗐다.
	 *
	 * @param record    상대방의 마지막 채굴 기록. 없으면(아직 아무것도 안 캤으면) 거짓
	 * @param block     지금 이 사람이 캔 블록
	 * @param gameTime  지금 시각(틱)
	 */
	static boolean recordMatches(@Nullable LastBreak record, Block block, long gameTime) {
		if (record == null || record.block() != block) {
			return false;
		}
		return gameTime - record.tick() <= PairedMiningEffect.MEMORY_TICKS;
	}

	private static void grantHaste(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.HASTE,
				PairedMiningEffect.HASTE_TICKS, PairedMiningEffect.HASTE_AMPLIFIER, false, false, true));
	}

	// ------------------------------------------------------------------ 혼자 페널티

	/** {@code ServerTickEvents.END_SERVER_TICK}에 붙는 지점. 1초에 한 번만 실제로 확인한다. */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < PENALTY_CHECK_TICKS) {
			return;
		}
		tickCounter = 0;
		try {
			tickAll(server);
		} catch (RuntimeException error) {
			warnOnce(error);
		}
	}

	private static void tickAll(MinecraftServer server) {
		TeamManager manager = TeamManager.get(server);
		double limitSquared = PairedMiningEffect.DISTANCE * PairedMiningEffect.DISTANCE;
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled || state.ownedPerks.isEmpty()
					|| !hasPairedMining(state)) {
				continue;
			}
			List<ServerPlayer> online = onlineMembers(server, team);
			for (ServerPlayer player : online) {
				boolean alone = online.stream()
						.filter(other -> other != player)
						.noneMatch(other -> other.level() == player.level()
								&& player.distanceToSqr(other) <= limitSquared);
				applyPenalty(player, alone);
			}
		}
	}

	private static List<ServerPlayer> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<ServerPlayer> result = new ArrayList<>();
		for (UUID memberId : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(memberId);
			if (player != null && !player.isRemoved()) {
				result.add(player);
			}
		}
		return result;
	}

	/** 패키지 안 시험이 직접 부를 수 있도록 접근 제한자를 두지 않는다. */
	static boolean hasPairedMining(TeamState state) {
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof PairedMiningEffect) {
					return true;
				}
			}
		}
		return false;
	}

	private static void applyPenalty(ServerPlayer player, boolean alone) {
		Holder<Attribute> attribute = resolveBlockBreakSpeed();
		if (attribute == null) {
			return;
		}
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		UUID id = player.getUUID();
		if (alone) {
			instance.removeModifier(SOLO_PENALTY_MODIFIER_ID);
			instance.addTransientModifier(new AttributeModifier(SOLO_PENALTY_MODIFIER_ID,
					PairedMiningEffect.SOLO_PENALTY_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			PENALIZED.add(id);
		} else if (PENALIZED.remove(id)) {
			instance.removeModifier(SOLO_PENALTY_MODIFIER_ID);
		}
	}

	private static @Nullable Holder<Attribute> resolveBlockBreakSpeed() {
		if (cachedAttribute != null || attributeResolveFailed) {
			return cachedAttribute;
		}
		Optional<Holder.Reference<Attribute>> found = BuiltInRegistries.ATTRIBUTE.get(BLOCK_BREAK_SPEED_ID);
		if (found.isEmpty()) {
			attributeResolveFailed = true;
			SharedFateMod.LOGGER.warn("공명 증강이 block_break_speed 속성을 찾지 못했습니다");
			return null;
		}
		cachedAttribute = found.get();
		return cachedAttribute;
	}

	/** 서버가 멈출 때 기억을 비운다. 다음 월드의 것을 물려받지 않기 위해서다. */
	public static void reset() {
		LAST_BREAKS.clear();
		PENALIZED.clear();
		tickCounter = 0;
		warned = false;
		cachedAttribute = null;
		attributeResolveFailed = false;
	}

	private static void warnOnce(RuntimeException error) {
		if (warned) {
			return;
		}
		warned = true;
		SharedFateMod.LOGGER.warn(
				"공명 증강을 처리하지 못해 이번엔 건너뜁니다. 이 경고는 한 번만 남습니다.", error);
	}
}
