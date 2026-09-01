package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.RarityRerollEffect;
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
 * {@code rarity_reroll} 증강(프리즘 「환골탈태」)의 즉시 개편을 맡는다.
 *
 * <p>{@link PerkGambler}·{@link PerkRarityGrant}와 같은 자리, 같은 시점 —
 * {@link PerkGrantChain}이 증강을 고른(또는 연쇄로 더 받은) 그 순간 처리한다. 다만 저 둘은
 * <b>더하기만</b> 하는 반면 이건 <b>있던 것을 지우고 다시 채운다</b> — {@code ownedPerks}를
 * 비웠다 다시 채우는 유일한 자리다.
 */
public final class PerkRarityReroll {
	private PerkRarityReroll() {
	}

	/**
	 * 증강 하나가 가진 {@code rarity_reroll} 효과를 실행한다.
	 *
	 * <p>지금 가진 증강(이 증강 포함 — {@code commit}이 이미 {@code ownedPerks}에 넣어 둔
	 * 뒤에 이 메서드를 부른다) 개수만큼, 지정한 등급의 무작위 증강으로 목록을 다시 채운다.
	 * 이 증강 자신의 id는 <b>기록으로만</b> 목록에 남는다 — {@link RarityRerollEffect}가
	 * {@code apply}/{@code remove}를 재정의하지 않으므로 남아 있어도 아무 효과가 없다.
	 *
	 * <p>지정 등급에 뽑을 후보가 하나도 없으면(정의 파일이 비정상인 경우뿐이다 — 실제
	 * 골드 풀은 30개다) 아무것도 손대지 않고 0을 돌려준다. 있으면 {@code count}개를
	 * 목표로 하되, 그 등급 전체 종류 수를 넘지 못한다(중복 없이 뽑을 수 있는 상한).
	 *
	 * @return 실제로 새로 채운 증강 수. 대상 등급에 후보가 없었으면 0
	 */
	public static int rerollOnChoice(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk, @Nullable RandomSource random) {
		return rerollOnChoiceDetailed(server, team, state, perk, random).size();
	}

	/**
	 * {@link #rerollOnChoice}와 같은 일을 하지만, 새로 채운 증강 목록을 그대로 돌려준다.
	 *
	 * <p>{@link PerkGrantChain}이 이 목록을 받아 그중에도 다른 즉시 지급 효과가 있으면 마저
	 * 처리한다. 다시 채운 것들은 이 개편이 끝난 <b>뒤의</b> {@code ownedPerks} 이므로, 연쇄
	 * 도중 더 앞서 받았던 증강의 id 가 이 개편으로 함께 지워졌더라도 이상하지 않다 —
	 * 「환골탈태」는 "가진 증강 전부"를 바꾸는 증강이고, 방금 연쇄로 받은 것도 지금 가진
	 * 것이기 때문이다.
	 */
	static List<Perk> rerollOnChoiceDetailed(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk perk, @Nullable RandomSource random) {
		if (state == null || perk == null || random == null) {
			return List.of();
		}
		RarityRerollEffect reroll = rerollEffect(perk);
		if (reroll == null) {
			return List.of();
		}
		int count = state.ownedPerks.size();
		if (count == 0) {
			return List.of();
		}
		List<Perk> pool = eligiblePool(reroll.rarity());
		if (pool.isEmpty()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: rarity_reroll 대상 등급({})에 후보가 하나도 없어 개편을 건너뜁니다.",
					perk.id(), reroll.rarity().displayName());
			return List.of();
		}

		// 기존에 걸려 있던 효과(속성·상태이상)를 먼저 걷어낸다. ownedPerks 가 아직 옛 목록인
		// 지금이 그 목록 기준으로 remove 를 부를 수 있는 유일한 시점이다.
		if (server != null && team != null) {
			PerkManager.setPerksEnabled(server, team, state, false);
		}

		state.ownedPerks.clear();
		// 이 증강 자신은 "무엇을 골랐었는가"의 기록으로만 남긴다. 효과는 없다.
		state.ownedPerks.add(perk.id());

		List<Perk> granted = new ArrayList<>(Math.min(count, pool.size()));
		for (int i = 0; i < count && !pool.isEmpty(); i++) {
			Perk picked = pool.remove(random.nextInt(pool.size()));
			granted.add(picked);
			state.ownedPerks.add(picked.id());
		}

		if (server != null && team != null) {
			PerkManager.setPerksEnabled(server, team, state, true);
		}

		SharedFateMod.LOGGER.info(
				"[PERK] 증강 {} 로 가진 증강 {}개가 무작위 {} 등급 {}개로 바뀌었습니다: {}",
				perk.id(), count, reroll.rarity().displayName(), granted.size(),
				granted.stream().map(Perk::id).toList());

		if (server != null && team != null) {
			String names = granted.stream().map(Perk::name)
					.reduce((a, b) -> a + ", " + b).orElse("(없음)");
			Component message = Component.literal("[증강] 「" + perk.name() + "」으로 가진 증강이 전부 "
					+ reroll.rarity().displayName() + " 등급 " + names + " 으로 바뀌었습니다.");
			for (UUID member : team.members()) {
				ServerPlayer online = server.getPlayerList().getPlayer(member);
				if (online != null) {
					online.sendSystemMessage(message);
				}
			}
		}
		return List.copyOf(granted);
	}

	private static @Nullable RarityRerollEffect rerollEffect(Perk perk) {
		for (PerkEffect effect : perk.effects()) {
			if (effect instanceof RarityRerollEffect reroll) {
				return reroll;
			}
		}
		return null;
	}

	/**
	 * 지정 등급 전부(중복 id 제거). 이 시점엔 이미 {@code ownedPerks}를 비웠으므로(또는 비울
	 * 예정이므로) "이미 가진 것 제외"가 필요 없다 — 방금까지 가졌던 증강도 다시 뽑힐 수 있다.
	 */
	private static List<Perk> eligiblePool(PerkRarity rarity) {
		Set<String> seen = new HashSet<>();
		List<Perk> pool = new ArrayList<>();
		for (Perk candidate : PerkRegistry.all()) {
			if (candidate == null || candidate.id() == null || candidate.rarity() != rarity) {
				continue;
			}
			if (!seen.add(candidate.id())) {
				continue;
			}
			pool.add(candidate);
		}
		return pool;
	}
}
