package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 증강을 고른 순간의 즉시 지급들이 서로를 부르는 연쇄를 처리한다.
 *
 * <p>「숨은 재능」({@code rarity_grant}, 실버→골드 1개)·「하늘의 은총」({@code rarity_grant},
 * 골드→프리즘 1개)·「도박꾼」({@code gambler}, 등급 무관 2개)·「환골탈태」({@code rarity_reroll},
 * 보유분 재편)는 전부 "이 증강을 고르면 다른 증강을 더(또는 다시) 준다"는 사건을 낸다. 그
 * 사건으로 받은 증강이 <b>또</b> 같은 종류의 사건을 내면(예: 실버 「숨은 재능」이 골드를
 * 하나 주는데 하필 그게 「하늘의 은총」이라 프리즘을 하나 더 주고, 그 프리즘이 하필
 * 「도박꾼」이라 무작위 2개를 더 주는 경우) 그 사건도 마저 처리해야 손에 들어온 증강이 전부
 * 실제로 발동한다. {@link PerkManager#applyChoice}가 부르는 {@code commit}이 원래 고른 증강
 * 하나로 이 처리를 시작한다.
 *
 * <h2>무한 재귀를 막는 두 겹</h2>
 * <ul>
 *   <li><b>방문 표시</b> — 이번 연쇄에서 이미 지급 처리를 한 증강 id 는 {@code visited}에
 *       남고, 같은 id 는 두 번째로 뽑혀도 다시 줄을 세우지 않는다. {@link PerkGambler}·
 *       {@link PerkRarityGrant}는 이미 가진 증강을 후보에서 빼므로 사실 이것만으로도 같은
 *       id 가 두 번 들어올 일은 없다. 유일한 예외가 {@link PerkRarityReroll}이다 — 지우고
 *       다시 뽑으므로 방금까지 가졌던 증강도 다시 나올 수 있는데, 그 경우에도 이 표시가
 *       다시 큐에 넣는 것을 막는다(이미 한 번 지급 처리를 마쳤으므로 다시 할 필요가 없다).</li>
 *   <li><b>단계 상한</b> — 방문 표시만으로도 유한한 풀에서는 반드시 끝나지만, 정의 파일이
 *       잘못돼 그 전제가 깨지는 경우에 대비해 {@value #MAX_STEPS} 단계에서 강제로 멈춘다.
 *       지금 증강 풀(수십 개)보다 넉넉히 크게 잡아, 정상적인 연쇄는 이 상한에 절대 닿지
 *       않는다.</li>
 * </ul>
 *
 * <h2>「환골탈태」가 연쇄 도중에 나오면</h2>
 * <p>{@code rarity_reroll}은 지금 가진 증강 <b>전부</b>를 지우고 다시 채운다. 연쇄 도중에
 * 나와도 다르지 않다 — 그 순간까지 이 연쇄로 받은 것도 이미 {@code ownedPerks}에 들어가 있는
 * "지금 가진 것"이므로 함께 지워지는 것이 맞다. 새로 채운 결과는 이 연쇄에 다시 들어가
 * 마저 처리된다(그중에 또 즉시 지급 효과가 있을 수 있으므로).
 *
 * <h2>화면 동기화는 여기서 하지 않는다</h2>
 * <p>연쇄 도중에는 채팅 알림만 나가고, {@code PerkSyncPayload} 방송은 {@code commit}이 이
 * 클래스를 부르고 돌아온 뒤 <b>한 번만</b> 한다. 매 단계 동기화하면 화면이 여러 번 깜빡이고,
 * 어차피 최종 상태만 보이면 되므로 낭비다.
 */
final class PerkGrantChain {
	/** 안전판. 방문 표시가 있으면 실제로는 이 값에 한참 못 미쳐 끝난다. */
	static final int MAX_STEPS = 64;

	private PerkGrantChain() {
	}

	/**
	 * 증강 하나를 고른 사건에서 시작해, 그로 인한 즉시 지급이 또 다른 지급을 부르는 연쇄를
	 * 끝까지 처리한다.
	 *
	 * <p>{@code chosen}은 부르는 쪽이 이미 {@code state.ownedPerks}에 넣어 둔 뒤 넘겨야 한다.
	 * {@code item_grant}·{@code legacy_gear}·{@code gambler}·{@code rarity_grant}·
	 * {@code rarity_reroll} 다섯 가지 즉시 지급 효과를 연쇄로 처리한다.
	 */
	static void run(@Nullable MinecraftServer server, @Nullable ShareTeam team,
			@Nullable TeamState state, @Nullable Perk chosen, @Nullable RandomSource random) {
		if (state == null || chosen == null || chosen.id() == null) {
			return;
		}

		Set<String> visited = new HashSet<>();
		Deque<Perk> queue = new ArrayDeque<>();
		visited.add(chosen.id());
		queue.add(chosen);

		int steps = 0;
		while (!queue.isEmpty()) {
			if (++steps > MAX_STEPS) {
				SharedFateMod.LOGGER.warn(
						"증강 지급 연쇄가 {}단계를 넘어 강제로 멈췄습니다. 시작: {}", MAX_STEPS, chosen.id());
				break;
			}
			Perk current = queue.poll();

			// 즉시 지급은 정확히 이 다섯 곳에서만 일어난다. item_grant 와 legacy_gear 는
			// 서로를 부르지 않는(더 받게 하지 않는) 단순 지급·몰수라 큐에 넣을 것이 없다.
			PerkItemGrants.grantOnChoice(server, team, state, current);
			PerkLegacyGear.sacrificeOnChoice(server, team, state, current);

			for (Perk granted : PerkGambler.grantOnChoiceDetailed(server, team, state, current, random)) {
				enqueue(queue, visited, granted);
			}
			for (Perk granted
					: PerkRarityGrant.grantOnChoiceDetailed(server, team, state, current, random)) {
				enqueue(queue, visited, granted);
			}
			for (Perk granted
					: PerkRarityReroll.rerollOnChoiceDetailed(server, team, state, current, random)) {
				enqueue(queue, visited, granted);
			}
		}
	}

	private static void enqueue(Deque<Perk> queue, Set<String> visited, @Nullable Perk perk) {
		if (perk == null || perk.id() == null || !visited.add(perk.id())) {
			return;
		}
		queue.add(perk);
	}
}
