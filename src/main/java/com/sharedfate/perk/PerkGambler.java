package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.GamblerEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * {@code gambler} 증강(프리즘 「도박꾼」)의 즉시 지급과, 그로 인한 20·25 구간 실버 고정을 맡는다.
 *
 * <p><b>{@link #grantOnChoice}를 부르는 곳은 {@link PerkManager}의 {@code commit} 하나뿐이다.</b>
 * {@link PerkItemGrants}·{@link PerkLegacyGear}와 같은 자리, 같은 시점이다. 증강은 한 회차에
 * 한 번만 고를 수 있으므로 이 자리를 지나는 것도 한 번뿐이다.
 *
 * <h2>등급 상관없이 2개</h2>
 * <p>{@link PerkDraft}의 등급별 추첨과 달리 이건 <b>전체 풀(프리즘 포함)에서 등급을 가리지
 * 않고</b> 뽑는다. 그래서 {@code PerkDraft}를 재사용하지 않고 여기서 직접 뽑는다. 이미 가진
 * 증강(도박꾼 자기 자신 포함 — {@code commit}이 {@code ownedPerks}에 도박꾼을 넣은 <b>뒤에</b>
 * 이 메서드를 부르므로 이미 걸러진다)과 이번에 같이 뽑힌 것끼리도 중복되지 않는다.
 */
public final class PerkGambler {
	/** 한 번에 더 얻는 증강 수. */
	public static final int GRANT_COUNT = 2;

	/** 도박꾼을 가진 팀이 실버로 고정해서 뽑는 구간. 프리즘(15렙) 바로 다음 두 구간이다. */
	private static final Set<Integer> FORCED_SILVER_MILESTONES = Set.of(20, 25);

	private PerkGambler() {
	}

	/**
	 * 이 팀이 도박꾼을 가졌고 지금 구간이 그 대가로 실버가 고정되는 구간이면 {@link PerkRarity#SILVER}.
	 * 아니면 {@code null}(평소대로 구간 판정을 따른다).
	 *
	 * <p>{@code PerkManager.advanceMilestones}가 {@code PerkDraft.rarityFor} 대신 이 답을 쓸지
	 * 정하는 자리다. 월드 없이 시험하려고 순수 함수로 뗐다.
	 */
	public static @Nullable PerkRarity forcedRarity(@Nullable TeamState state, int milestone) {
		if (state == null || !FORCED_SILVER_MILESTONES.contains(milestone)) {
			return null;
		}
		return ownsGambler(state) ? PerkRarity.SILVER : null;
	}

	/** 이 팀이 지금 도박꾼을 갖고 있는가. id 를 하드코딩하지 않고 효과 타입으로 판정한다. */
	private static boolean ownsGambler(TeamState state) {
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof GamblerEffect) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * 증강 하나가 가진 {@code gambler} 효과를 실행한다. 등급 상관없이 무작위 {@link #GRANT_COUNT}개를
	 * 더 준다.
	 *
	 * @return 실제로 더 준 증강 수. 줄 것이 없었으면(도박꾼이 아니거나 후보 풀이 비었으면) 0
	 */
	public static int grantOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk, @Nullable RandomSource random) {
		if (state == null || perk == null || random == null || !hasGambler(perk)) {
			return 0;
		}

		List<Perk> pool = eligiblePool(state);
		List<Perk> granted = new ArrayList<>(GRANT_COUNT);
		for (int i = 0; i < GRANT_COUNT && !pool.isEmpty(); i++) {
			Perk picked = pool.remove(random.nextInt(pool.size()));
			granted.add(picked);
			state.ownedPerks.add(picked.id());
		}
		if (granted.isEmpty()) {
			return 0;
		}

		SharedFateMod.LOGGER.info("[PERK] 증강 {} 로 무작위 증강 {}개를 더 얻었습니다: {}",
				perk.id(), granted.size(), granted.stream().map(Perk::id).toList());

		if (server != null && team != null) {
			String names = granted.stream()
					.map(g -> g.rarity().displayName() + " 등급 " + g.name())
					.reduce((a, b) -> a + ", " + b).orElse("");
			Component message = Component.literal("[증강] 「" + perk.name() + "」으로 " + names + " 을(를) 더 얻었습니다.");
			for (UUID member : team.members()) {
				ServerPlayer online = server.getPlayerList().getPlayer(member);
				if (online != null) {
					online.sendSystemMessage(message);
				}
			}
		}
		return granted.size();
	}

	private static boolean hasGambler(Perk perk) {
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof GamblerEffect) {
				return true;
			}
		}
		return false;
	}

	/** 아직 안 가진 증강 전부(등급 상관없이, 중복 id 제거). */
	private static List<Perk> eligiblePool(TeamState state) {
		Set<String> seen = new HashSet<>();
		List<Perk> pool = new ArrayList<>();
		for (Perk candidate : PerkRegistry.all()) {
			if (candidate == null || candidate.id() == null) {
				continue;
			}
			if (!seen.add(candidate.id())) {
				continue;
			}
			if (state.ownedPerks.contains(candidate.id())) {
				continue;
			}
			pool.add(candidate);
		}
		return pool;
	}
}
