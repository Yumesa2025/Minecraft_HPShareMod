package com.sharedfate.net;

import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.PerkSetRegistry;
import com.sharedfate.perk.PerkSetType;
import com.sharedfate.perk.PerkSets;
import com.sharedfate.perk.PerkSupplyDrops;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 접속 중인 사람에게 <b>세트 효과의 지금 상태</b>를 알려 주는 자리.
 *
 * <p>주기마다 훑고, <b>값이 달라진 사람에게만</b> 보내고, 접속이 끊기면 「이미 보냈다」는
 * 기록을 버린다.
 *
 * <h2>무엇을 싣는가</h2>
 *
 * <ul>
 *   <li>{@code sets} — 유형마다 「몇 개 가졌나 / 다음 단계는 몇 개인가 / 지금 몇 단계가
 *       켜졌나」를 {@link PerkSetSyncPayload.SetLine} 한 줄로 만든다.
 *       <b>가진 것이 0인 유형도 싣는다</b> — 화면이 무엇을 감출지 정하는 것은 화면 쪽
 *       일이고({@code com.sharedfate.ui.PerkSetLines}), 여기서 미리 빼면 툴팁이 「채굴에 열 개가
 *       있다」를 영영 말하지 못한다.</li>
 *   <li>{@code catalog} — 유형이 붙은 증강 전부를 {@code (유형, 이름, 등급, 가졌는가)} 로
 *       늘어놓는다. 증강 하나가 유형을 여럿 가지면 유형마다 한 줄씩이다.
 *       <b>유형별로 모아서 싣는다</b> — 상한을 넘으면 뒤에서부터 잘리므로, 그래야 한 유형이
 *       통째로 사라지지 유형마다 뒷부분이 조금씩 비지 않는다.</li>
 * </ul>
 *
 * <p>판정은 반드시 {@code ownedPerks} 기준이어야 한다. {@code perkOwners}(주인 기록) 기준으로
 * 하면 요행·은총이 덤으로 준 증강이 세트에서 빠진다 — 그것들은 주인이 없다.
 */
public final class PerkSetBroadcaster {
	/**
	 * 값이 달라졌는지 확인하는 주기.
	 *
	 * <p>0.5초다. 세트는 증강을 얻거나 잃을 때만 바뀌는 드문 값이라 {@code StatSnapshot} 처럼
	 * 촘촘히 볼 필요가 없다.
	 */
	private static final int SCAN_INTERVAL_TICKS = 10;

	/** 사람별로 마지막에 보낸 값. 달라졌을 때만 다시 보낸다. */
	private static final Map<UUID, PerkSetSyncPayload> LAST_SENT = new HashMap<>();

	private static int scanCooldown;

	private PerkSetBroadcaster() {
	}

	/** 매 틱 도는 지점. {@link #SCAN_INTERVAL_TICKS} 틱마다만 실제로 훑는다. */
	public static void flush(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++scanCooldown < SCAN_INTERVAL_TICKS) {
			return;
		}
		scanCooldown = 0;

		Set<UUID> online = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			online.add(playerId);
			PerkSetSyncPayload now = of(player);
			if (now.equals(LAST_SENT.get(playerId))) {
				continue;
			}
			if (!ServerPlayNetworking.canSend(player, PerkSetSyncPayload.TYPE)) {
				// 이 패킷을 모르는 클라이언트다. 다음 훑기에서 다시 시도한다.
				continue;
			}
			ServerPlayNetworking.send(player, now);
			LAST_SENT.put(playerId, now);
		}
		// 나간 사람의 기록은 버린다.
		LAST_SENT.keySet().retainAll(online);
	}

	/**
	 * 이 사람이 지금 받아야 할 값.
	 *
	 * <p>빈 값도 그대로 보내는 것이 맞다 — 팀에서 나가거나 환골탈태로 세트가 사라졌을 때 화면이
	 * 옛 줄을 그대로 들고 있으면 안 되기 때문이다. 그래서 {@code null} 이 아니라
	 * {@link PerkSetSyncPayload#EMPTY} 다.
	 */
	static PerkSetSyncPayload of(@Nullable ServerPlayer player) {
		if (player == null) {
			return PerkSetSyncPayload.EMPTY;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return PerkSetSyncPayload.EMPTY;
		}

		// 가진 것이 0인 유형도 그대로 싣는다. 무엇을 감출지는 화면이 정한다.
		//
		// 단계 설명도 함께 싣는다. 클라이언트는 세트 정의 파일을 안 읽으므로 「2 단계가 무엇을
		// 하는가」를 스스로 알 방법이 없고, 그래서 툴팁을 서버가 말해 주지 않으면 만들 수 없다.
		// 「보급」 줄에 붙는 시계의 근거. 남은 시간이 아니라 주기와 켜진 시점을 싣는다 —
		// 이 둘은 단계가 바뀌거나 세트가 풀렸다 켜질 때만 달라지므로 아래 「달라졌을 때만
		// 보낸다」가 그대로 살아 있다. 남은 시간을 실으면 값이 초마다 달라져 이름표 백 몇 줄이
		// 0.5초마다 함께 나간다.
		PerkSupplyDrops.Cadence supply = PerkSupplyDrops.cadenceFor(player);

		List<PerkSetSyncPayload.SetLine> sets = new ArrayList<>();
		List<PerkSetSyncPayload.TierLine> tiers = new ArrayList<>();
		for (PerkSets.Status status : PerkSetEffects.statusesOf(state)) {
			// 시계가 어느 유형에 붙는지는 여기서 정한다. 화면은 주기가 실렸는지만 본다.
			int intervalTicks = status.type() == PerkSetType.SUPPLY ? supply.intervalTicks() : 0;
			long anchorTick = status.type() == PerkSetType.SUPPLY ? supply.anchorTick() : 0L;
			sets.add(new PerkSetSyncPayload.SetLine(
					status.type().id(), status.type().displayName(),
					status.owned(), status.nextCount(), highestTier(status),
					intervalTicks, anchorTick));
			// 켜진 단계는 개수로 판별한다. 단계가 열리는 개수는 유형 안에서 겹치지 않는다.
			Set<Integer> activeCounts = new HashSet<>();
			for (PerkSets.Tier tier : status.activeTiers()) {
				activeCounts.add(tier.count());
			}
			for (PerkSets.Tier tier : PerkSetRegistry.tiersOf(status.type())) {
				tiers.add(new PerkSetSyncPayload.TierLine(status.type().id(), tier.count(),
						tier.description(), activeCounts.contains(tier.count())));
			}
		}

		// 유형이 붙은 증강 전부. 유형별로 모아서 실어야 상한에 잘려도 한 유형이 통째로
		// 사라지지, 유형마다 뒷부분이 조금씩 비는 일이 없다.
		//
		// 한 유형 안에서는 <b>등급이 높은 것부터</b> 싣는다. 툴팁은 자리가 모자라면 뒤를 잘라
		// 「… 외 N개」로 접는데, 그때 남아야 할 것은 얻기 어려운 프리즘 쪽이기 때문이다.
		// 채굴처럼 실버가 여섯인 유형에서 순서를 안 정하면 프리즘이 늘 접히는 쪽에 놓인다.
		Set<String> owned = new HashSet<>(state.ownedPerks);
		List<PerkSetSyncPayload.CatalogEntry> catalog = new ArrayList<>();
		for (PerkSetType type : PerkSetType.values()) {
			List<Perk> inType = new ArrayList<>();
			for (Perk perk : PerkRegistry.all()) {
				if (perk.hasSetType(type)) {
					inType.add(perk);
				}
			}
			inType.sort(Comparator.comparingInt((Perk perk) -> perk.rarity().ordinal()).reversed());
			for (Perk perk : inType) {
				catalog.add(new PerkSetSyncPayload.CatalogEntry(
						type.id(), perk.name(), perk.rarity().id(), owned.contains(perk.id())));
			}
		}
		return new PerkSetSyncPayload(sets, tiers, catalog);
	}

	/**
	 * 지금 켜진 것 중 가장 높은 단계. 하나도 안 켜졌으면 0 이다.
	 *
	 * <p>단계는 누적이라 켜진 것이 여럿일 수 있는데({@code 채굴 4} 면 2·3·4 가 전부 켜진다),
	 * 화면에 「◆ 채굴 4/4」로 보여 줄 숫자는 그중 가장 높은 하나다.
	 */
	private static int highestTier(PerkSets.Status status) {
		int highest = 0;
		for (PerkSets.Tier tier : status.activeTiers()) {
			highest = Math.max(highest, tier.count());
		}
		return highest;
	}

	/** 한 사람의 기록을 버린다. 접속이 끊길 때 부른다. */
	public static void forget(@Nullable UUID playerId) {
		if (playerId != null) {
			LAST_SENT.remove(playerId);
		}
	}

	/** 서버가 멈출 때 들고 있던 기록을 버린다. */
	public static void reset() {
		LAST_SENT.clear();
		scanCooldown = 0;
	}
}
