package com.sharedfate.perk;

import com.sharedfate.SharedFateMod;
import com.sharedfate.net.PerkOfferPayload;
import com.sharedfate.net.PerkSyncPayload;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 증강 시스템의 허브.
 *
 * <p>레벨 구간 감지 → 후보 추첨 → 대기열 적재 → 선택 적용까지를 담당한다. 실제 효과 계산은
 * {@link PerkEffect} 구현체가, 후보 선정 규칙은 {@link PerkDraft} 가 맡는다.
 *
 * <p>모든 상태는 {@link TeamState} 에 들어 있어 월드와 함께 저장된다. 전멸로 월드가 초기화되면
 * 보유 증강과 대기열은 자동으로 사라진다.
 */
public final class PerkManager {
	/** 구간 감지는 매 틱 할 필요가 없다. 1초에 한 번이면 충분하다. */
	private static final int CHECK_INTERVAL_TICKS = 20;
	private static final int OPTION_COUNT = 3;

	private static int tickCounter;

	private PerkManager() {
	}

	/** {@code /shareteam perk} 실행 결과. 명령 쪽에서 안내 문구를 고르는 데 쓴다. */
	public enum OpenResult {
		OPENED,
		SPECTATING,
		NO_TEAM,
		PERKS_DISABLED,
		NOTHING_PENDING
	}

	public static void reset() {
		tickCounter = 0;
	}

	// ------------------------------------------------------------------ 구간 감지

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled) {
				continue;
			}
			boolean changed = advanceMilestones(server, team, state);
			changed |= assignMissingChoosers(server, team, state);
			if (changed) {
				manager.setDirty();
				broadcastSync(server, team, state);
			}
		}
	}

	/** 아직 처리하지 않은 구간들을 대기열로 밀어넣는다. */
	private static boolean advanceMilestones(MinecraftServer server, ShareTeam team, TeamState state) {
		List<Integer> reached = PerkMilestones.newlyReached(state.lastPerkMilestone, state.xpLevel);
		if (reached.isEmpty()) {
			return false;
		}
		RandomSource random = server.overworld().getRandom();
		for (int milestone : reached) {
			List<String> options = PerkDraft.draw(
					milestone, PerkRegistry.all(), state.ownedPerks, random, OPTION_COUNT);
			state.lastPerkMilestone = milestone;
			if (options.isEmpty()) {
				SharedFateMod.LOGGER.warn(
						"{}렙 구간의 증강 후보를 하나도 뽑지 못해 건너뜁니다. 증강 풀이 비어 있는지 확인하십시오.",
						milestone);
				continue;
			}
			UUID chooser = pickChooser(server, team, random);
			PendingOffer offer = new PendingOffer(milestone, Optional.ofNullable(chooser), options);
			state.pending.add(offer);
			announceOffer(server, team, offer, chooser);
		}
		return true;
	}

	/** 발동 당시 아무도 접속해 있지 않았던 선택권에 뒤늦게 선택자를 붙인다. */
	private static boolean assignMissingChoosers(MinecraftServer server, ShareTeam team, TeamState state) {
		boolean changed = false;
		RandomSource random = server.overworld().getRandom();
		for (int i = 0; i < state.pending.size(); i++) {
			PendingOffer offer = state.pending.get(i);
			if (offer.chooser().isPresent()) {
				continue;
			}
			UUID chooser = pickChooser(server, team, random);
			if (chooser == null) {
				continue;
			}
			state.pending.set(i, offer.withChooser(chooser));
			announceOffer(server, team, offer, chooser);
			changed = true;
		}
		return changed;
	}

	private static @Nullable UUID pickChooser(MinecraftServer server, ShareTeam team, RandomSource random) {
		List<UUID> online = onlineMembers(server, team);
		if (online.isEmpty()) {
			return null;
		}
		return online.get(random.nextInt(online.size()));
	}

	private static List<UUID> onlineMembers(MinecraftServer server, ShareTeam team) {
		List<UUID> online = new ArrayList<>();
		for (UUID member : team.members()) {
			if (server.getPlayerList().getPlayer(member) != null) {
				online.add(member);
			}
		}
		return online;
	}

	// ------------------------------------------------------------------ 접속 이벤트

	public static void onPlayerJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		TeamState state = manager.stateOf(player.getUUID());
		if (team == null || state == null) {
			return;
		}
		refreshPlayer(player);
		if (!state.perksEnabled) {
			return;
		}
		if (assignMissingChoosers(server, team, state)) {
			manager.setDirty();
		}
		broadcastSync(server, team, state);
		remindIfChooser(player, state);
	}

	/** 선택권을 가진 사람이 나가면 접속 중인 다른 팀원에게 넘긴다. 후보는 그대로 유지한다. */
	public static void onPlayerLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		TeamState state = manager.stateOf(player.getUUID());
		if (team == null || state == null || !state.perksEnabled || state.pending.isEmpty()) {
			return;
		}
		UUID leaving = player.getUUID();
		RandomSource random = server.overworld().getRandom();
		boolean changed = false;
		for (int i = 0; i < state.pending.size(); i++) {
			PendingOffer offer = state.pending.get(i);
			if (!offer.isChooser(leaving)) {
				continue;
			}
			List<UUID> candidates = new ArrayList<>(onlineMembers(server, team));
			candidates.remove(leaving);
			UUID next = candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
			state.pending.set(i, offer.withChooser(next));
			changed = true;
			if (next != null) {
				announceOffer(server, team, offer, next);
			}
		}
		if (changed) {
			manager.setDirty();
			broadcastSync(server, team, state);
		}
	}

	// ------------------------------------------------------------------ 선택창

	public static OpenResult openOffer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return OpenResult.NO_TEAM;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		TeamState state = manager.stateOf(player.getUUID());
		if (team == null || state == null) {
			return OpenResult.NO_TEAM;
		}
		if (!state.perksEnabled) {
			return OpenResult.PERKS_DISABLED;
		}
		if (state.pending.isEmpty()) {
			return OpenResult.NOTHING_PENDING;
		}

		PendingOffer offer = state.pending.getFirst();
		boolean canChoose = offer.isChooser(player.getUUID());
		// 관전 화면이 "○○님이 고르는 중입니다"를 띄우려면 선택자 이름이 먼저 가 있어야 한다.
		broadcastSync(server, team, state);
		ServerPlayNetworking.send(player, toOfferPayload(offer, canChoose));
		return canChoose ? OpenResult.OPENED : OpenResult.SPECTATING;
	}

	private static PerkOfferPayload toOfferPayload(PendingOffer offer, boolean canChoose) {
		List<PerkOfferPayload.PerkOption> options = new ArrayList<>();
		for (String id : offer.optionIds()) {
			Perk perk = PerkRegistry.byId(id).orElse(null);
			if (perk == null) {
				SharedFateMod.LOGGER.warn("대기 중인 후보 '{}' 를 증강 풀에서 찾을 수 없어 제외합니다.", id);
				continue;
			}
			options.add(new PerkOfferPayload.PerkOption(
					perk.id(), perk.name(), perk.description(), perk.rarity().id()));
		}
		return new PerkOfferPayload(offer.milestone(), canChoose, options);
	}

	/**
	 * 클라이언트가 보낸 선택을 검증하고 반영한다.
	 *
	 * <p>신뢰할 수 없는 입력이므로 팀 소속, 활성 여부, 선택자 본인 여부, 구간 일치, 후보 포함 여부,
	 * 중첩 상한을 모두 서버에서 다시 확인한다. 지연·재전송된 패킷은 조용히 무시한다.
	 */
	public static void applyChoice(ServerPlayer player, int milestone, String perkId) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(player.getUUID());
		TeamState state = manager.stateOf(player.getUUID());
		if (team == null || state == null || !state.perksEnabled || state.pending.isEmpty()) {
			return;
		}

		PendingOffer offer = state.pending.getFirst();
		if (offer.milestone() != milestone || !offer.isChooser(player.getUUID())) {
			return;
		}
		if (!offer.optionIds().contains(perkId)) {
			return;
		}
		Perk perk = PerkRegistry.byId(perkId).orElse(null);
		if (perk == null) {
			return;
		}
		int current = stackCount(state, perkId);
		if (!perk.canTakeMore(current)) {
			return;
		}

		addStack(state, perkId);
		state.pending.removeFirst();
		manager.setDirty();

		applyToTeam(server, team, state);
		// 몹에게 걸리는 증강은 폴링으로도 따라잡지만, 고른 즉시 반영되는 편이 자연스럽다.
		MobPerkModifiers.invalidateNow(server);
		broadcastSync(server, team, state);
		broadcast(server, team, Component.literal(
				"[증강] " + player.getGameProfile().name() + "님이 "
						+ perk.rarity().displayName() + " 등급 " + perk.name()
						+ " 을(를) 골랐습니다. 팀 전체에 적용됩니다."));
	}

	// ------------------------------------------------------------------ 효과 적용

	/** 한 플레이어에게 팀이 보유한 증강 효과를 전부 다시 맞춘다. */
	public static void refreshPlayer(ServerPlayer player) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return;
		}
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				try {
					effect.apply(player, stack.count());
				} catch (RuntimeException error) {
					SharedFateMod.LOGGER.warn("증강 '{}' 효과 적용에 실패했습니다.", perk.id(), error);
				}
			}
		}
	}

	private static void applyToTeam(MinecraftServer server, ShareTeam team, TeamState state) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				refreshPlayer(online);
			}
		}
	}

	/** 팀이 보유한 증강의 주는 피해 배율을 모두 곱한 값. */
	public static double damageDealtMultiplier(ServerPlayer player) {
		return multiplier(player, true);
	}

	/** 팀이 보유한 증강의 받는 피해 배율을 모두 곱한 값. */
	public static double damageTakenMultiplier(ServerPlayer player) {
		return multiplier(player, false);
	}

	private static double multiplier(ServerPlayer player, boolean dealt) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state == null || state.ownedPerks.isEmpty()) {
			return 1.0;
		}
		double total = 1.0;
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				total *= dealt
						? effect.damageDealtMultiplier(stack.count())
						: effect.damageTakenMultiplier(stack.count());
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	// ------------------------------------------------------------------ 조회와 알림

	public static List<String> ownedLines(ServerPlayer player) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state == null || state.ownedPerks.isEmpty()) {
			return List.of();
		}
		List<String> lines = new ArrayList<>();
		for (PerkStack stack : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(stack.perkId()).orElse(null);
			String label = perk == null ? stack.perkId() : perk.name();
			lines.add(stack.count() > 1 ? label + " x" + stack.count() : label);
		}
		return lines;
	}

	private static int stackCount(TeamState state, String perkId) {
		for (PerkStack stack : state.ownedPerks) {
			if (stack.perkId().equals(perkId)) {
				return stack.count();
			}
		}
		return 0;
	}

	private static void addStack(TeamState state, String perkId) {
		for (int i = 0; i < state.ownedPerks.size(); i++) {
			PerkStack stack = state.ownedPerks.get(i);
			if (stack.perkId().equals(perkId)) {
				state.ownedPerks.set(i, stack.plusOne());
				return;
			}
		}
		state.ownedPerks.add(new PerkStack(perkId, 1));
	}

	private static void announceOffer(MinecraftServer server, ShareTeam team, PendingOffer offer,
			@Nullable UUID chooser) {
		if (chooser == null) {
			return;
		}
		ServerPlayer picked = server.getPlayerList().getPlayer(chooser);
		String name = picked == null ? "팀원" : picked.getGameProfile().name();
		PerkRarity rarity = offerRarity(offer);
		String grade = rarity == null ? "증강" : rarity.displayName() + " 등급 증강";
		broadcast(server, team, Component.literal(
				"[증강] " + offer.milestone() + "렙 달성. " + grade + " 선택권이 " + name
						+ "님에게 생겼습니다. /shareteam perk 로 확인하십시오."));
	}

	/**
	 * 이 선택권의 등급.
	 *
	 * <p>한 라운드의 후보는 전부 같은 등급이므로 찾을 수 있는 첫 후보만 보면 된다.
	 * 풀에서 사라진 id뿐이면 null 이다.
	 */
	private static @Nullable PerkRarity offerRarity(PendingOffer offer) {
		for (String id : offer.optionIds()) {
			Perk perk = PerkRegistry.byId(id).orElse(null);
			if (perk != null) {
				return perk.rarity();
			}
		}
		return null;
	}

	private static void remindIfChooser(ServerPlayer player, TeamState state) {
		for (PendingOffer offer : state.pending) {
			if (offer.isChooser(player.getUUID())) {
				player.sendSystemMessage(Component.literal(
						"[증강] 고르지 않은 선택권이 있습니다. /shareteam perk 로 확인하십시오."));
				return;
			}
		}
	}

	private static void broadcast(MinecraftServer server, ShareTeam team, Component message) {
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				online.sendSystemMessage(message);
			}
		}
	}

	private static void broadcastSync(MinecraftServer server, ShareTeam team, TeamState state) {
		String chooserName = "";
		if (!state.pending.isEmpty()) {
			Optional<UUID> chooser = state.pending.getFirst().chooser();
			if (chooser.isPresent()) {
				ServerPlayer picked = server.getPlayerList().getPlayer(chooser.get());
				if (picked != null) {
					chooserName = picked.getGameProfile().name();
				}
			}
		}
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online == null) {
				continue;
			}
			ServerPlayNetworking.send(online,
					new PerkSyncPayload(ownedLines(online), state.pending.size(), chooserName));
		}
	}
}
