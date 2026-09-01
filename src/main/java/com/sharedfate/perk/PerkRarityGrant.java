package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.RarityGrantEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * {@code rarity_grant} 증강(실버 「숨은 재능」, 골드 「하늘의 은총」)의 즉시 지급을 맡는다.
 *
 * <p>{@link PerkGambler}와 같은 자리, 같은 시점 — {@link PerkGrantChain}이 증강을 고른(또는
 * 연쇄로 더 받은) 그 순간 처리한다. 도박꾼과 달리 등급을 가리므로 {@code PerkDraft}의 등급별
 * 추첨과 같은 모양으로 후보를 좁힌다.
 */
public final class PerkRarityGrant {
	private PerkRarityGrant() {
	}

	/**
	 * 증강 하나가 가진 {@code rarity_grant} 효과를 모두 실행한다.
	 *
	 * @return 실제로 더 준 증강 수. 줄 것이 없었으면 0
	 */
	public static int grantOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk, @Nullable RandomSource random) {
		return grantOnChoiceDetailed(server, team, state, perk, random).size();
	}

	/**
	 * {@link #grantOnChoice}와 같은 일을 하지만, 실제로 더 준 증강 목록을 그대로 돌려준다.
	 *
	 * <p>{@link PerkGrantChain}이 이 목록을 받아 그중에도 다른 즉시 지급 효과가 있으면 마저
	 * 처리한다(예: 「숨은 재능」이 뽑은 골드가 하필 「하늘의 은총」인 경우).
	 */
	static List<Perk> grantOnChoiceDetailed(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk, @Nullable RandomSource random) {
		if (state == null || perk == null || random == null) {
			return List.of();
		}
		List<Perk> total = new ArrayList<>();
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof RarityGrantEffect grant) {
				total.addAll(grantOne(server, team, state, perk, grant, random));
			}
		}
		return List.copyOf(total);
	}

	private static List<Perk> grantOne(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			TeamState state, Perk perk, RarityGrantEffect grant, RandomSource random) {
		List<Perk> pool = eligiblePool(state, grant.rarity());
		List<Perk> granted = new ArrayList<>(grant.count());
		for (int i = 0; i < grant.count() && !pool.isEmpty(); i++) {
			Perk picked = pool.remove(random.nextInt(pool.size()));
			granted.add(picked);
			state.ownedPerks.add(picked.id());
		}
		if (granted.isEmpty()) {
			return List.of();
		}

		SharedFateMod.LOGGER.info("[PERK] 증강 {} 로 무작위 {} 등급 증강 {}개를 더 얻었습니다: {}",
				perk.id(), grant.rarity().displayName(), granted.size(),
				granted.stream().map(Perk::id).toList());

		if (server != null && team != null) {
			String names = granted.stream().map(Perk::name).reduce((a, b) -> a + ", " + b).orElse("");
			Component message = Component.literal("[증강] 「" + perk.name() + "」으로 "
					+ grant.rarity().displayName() + " 등급 " + names + " 을(를) 더 얻었습니다.");
			for (UUID member : team.members()) {
				ServerPlayer online = server.getPlayerList().getPlayer(member);
				if (online != null) {
					online.sendSystemMessage(message);
				}
			}
		}
		return List.copyOf(granted);
	}

	/** 아직 안 가진, 지정 등급의 증강 전부(중복 id 제거). */
	private static List<Perk> eligiblePool(TeamState state, PerkRarity rarity) {
		Set<String> seen = new HashSet<>();
		List<Perk> pool = new ArrayList<>();
		for (Perk candidate : PerkRegistry.all()) {
			if (candidate == null || candidate.id() == null || candidate.rarity() != rarity) {
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
