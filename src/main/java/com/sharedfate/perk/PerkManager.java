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
		PerkChoiceSession.reset();
	}

	/** 서버가 켜질 때. 이전 실행에서 얼려 둔 시간이 남아 있지 않은지 확인한다. */
	public static void onServerStarted(MinecraftServer server) {
		tickCounter = 0;
		PerkChoiceSession.onServerStarted(server);
	}

	/** 서버가 멈추기 직전. 얼어 있는 채로 종료하지 않는다. */
	public static void onServerStopping(MinecraftServer server) {
		PerkChoiceSession.onServerStopping(server);
	}

	// ------------------------------------------------------------------ 구간 감지

	public static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		// 강제 선택 세션은 제한시간을 세야 하므로 감지 주기와 무관하게 매 틱 돌린다.
		// 시간이 멈춰 있어도 tickServer 는 그대로 도니 이 카운트다운은 절대 멈추지 않는다.
		PerkChoiceSession.tick(server);

		if (++tickCounter < CHECK_INTERVAL_TICKS) {
			return;
		}
		tickCounter = 0;

		TeamManager manager = TeamManager.get(server);
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			TeamState state = manager.stateByTeamId(team.teamId());
			// 「게임 시작」을 누르기 전에는 구간을 세지 않는다. 증강은 회차의 보상인데, 팀원을
			// 기다리며 서 있는 동안 올린 레벨로 증강이 나오면 회차가 시작되기도 전에 판이
			// 정해진다. 시작하는 순간 레벨과 지나온 구간이 함께 0 으로 돌아간다
			// ({@code GameStartManager}).
			if (state == null || !state.perksEnabled || !state.runStarted) {
				continue;
			}
			boolean changed = advanceMilestones(server, team, state);
			changed |= assignMissingChoosers(server, team, state);
			if (changed) {
				manager.setDirty();
				broadcastSync(server, team, state);
			}
		}
		beginSessionIfIdle(server, manager);
	}

	/**
	 * 대기 중인 선택권이 남아 있으면 강제 선택 세션을 연다.
	 *
	 * <p>세션은 서버 전체에 하나뿐이다. 팀이 여럿이면 한 팀이 끝난 뒤 다음 팀 차례가 온다.
	 * 팀원이 전부 접속을 끊어 중단된 선택권도 누군가 돌아오면 이 경로로 다시 열린다.
	 */
	private static void beginSessionIfIdle(MinecraftServer server, TeamManager manager) {
		if (PerkChoiceSession.isActive()) {
			return;
		}
		for (ShareTeam team : List.copyOf(manager.allTeams())) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null || !state.perksEnabled || !state.runStarted || state.pending.isEmpty()) {
				continue;
			}
			if (PerkChoiceSession.begin(server, team, state)) {
				return;
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
			// 예전에는 도박꾼을 가진 팀이 15렙 바로 다음 두 구간(20·25렙)에서 실버로 고정됐지만
			// (2026-09-01 7차에서) 그 대가를 없앴으므로 이제 이 구간도 평소대로 구간 규칙을 따른다.
			List<String> options =
					PerkDraft.draw(milestone, PerkRegistry.all(), state.ownedPerks, random, OPTION_COUNT);
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
			// 알림은 여기서 하지 않는다. 곧바로 강제 선택 세션이 열리면서
			// PerkChoiceSession 이 구간·등급·선택자·제한시간을 한 번에 알려 준다.
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
		// 강제 선택이 진행 중이면 늦게 들어온 사람에게도 창을 띄우고 무적을 걸어 준다.
		PerkChoiceSession.refreshAudience(server);
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
			// 선택자가 바뀌었으니 남은 사람들의 창을 새 권한으로 다시 띄운다. 이걸 빠뜨리면
			// 아무도 고를 수 없는 채로 제한시간까지 방치된다.
			PerkChoiceSession.refreshAudience(server);
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
		if (PerkChoiceSession.isActive()
				&& team.teamId().equals(PerkChoiceSession.activeTeamId())
				&& PerkChoiceSession.activeMilestone() == offer.milestone()) {
			// 강제 선택이 진행 중인데 창을 잃어버린 경우다. 마감이 살아 있는 창으로 되돌려 준다.
			ServerPlayNetworking.send(player, new PerkOfferPayload(offer.milestone(), canChoose,
					true, PerkChoiceSession.remainingTicks(), state.rerollsRemaining,
					describeOptions(offer)));
		} else {
			// 직접 여는 경로. 시간을 멈추지도, 무적을 걸지도 않는 단순 확인용이다.
			ServerPlayNetworking.send(player,
					PerkOfferPayload.manual(offer.milestone(), canChoose, describeOptions(offer)));
		}
		return canChoose ? OpenResult.OPENED : OpenResult.SPECTATING;
	}

	/**
	 * 대기 중인 후보를 화면에 그릴 수 있는 형태로 푼다.
	 *
	 * <p>풀에서 사라진 id 는 빠지므로 결과가 빈 목록일 수 있다. 그런 선택권으로는
	 * 절대 시간을 멈추지 않는다({@link PerkChoiceSession#begin}).
	 */
	static List<PerkOfferPayload.PerkOption> describeOptions(PendingOffer offer) {
		List<PerkOfferPayload.PerkOption> options = new ArrayList<>();
		for (String id : offer.optionIds()) {
			Perk perk = PerkRegistry.byId(id).orElse(null);
			if (perk == null) {
				SharedFateMod.LOGGER.warn("대기 중인 후보 '{}' 를 증강 풀에서 찾을 수 없어 제외합니다.", id);
				continue;
			}
			// 아이콘이 없는 증강은 빈 문자열로 보낸다. 화면이 등급별 기본 아이콘으로 메운다.
			options.add(new PerkOfferPayload.PerkOption(
					perk.id(), perk.name(), perk.description(), perk.rarity().id(),
					perk.icon() == null ? "" : perk.icon().toString()));
		}
		return options;
	}

	/**
	 * 클라이언트가 보낸 선택을 검증하고 반영한다.
	 *
	 * <p>신뢰할 수 없는 입력이므로 팀 소속, 활성 여부, 선택자 본인 여부, 구간 일치, 후보 포함 여부,
	 * 이미 보유했는지를 모두 서버에서 다시 확인한다. 지연·재전송된 패킷은 조용히 무시한다.
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
		if (state.ownedPerks.contains(perkId)) {
			// 이미 가진 증강은 후보에 없어야 하지만, 지연된 패킷이 들어올 수 있다.
			return;
		}

		RandomSource random = server.overworld().getRandom();
		commit(server, manager, team, state, perk,
				player.getGameProfile().name() + "님이 " + gradeAndName(perk) + " 을(를) 골랐습니다.", random);
		// 선택이 끝났으니 시간을 다시 흐르게 하고 팀 전원의 창을 닫는다.
		PerkChoiceSession.onChoiceApplied(server, team.teamId(), milestone,
				perk.id(), player.getGameProfile().name());
	}

	/**
	 * 클라이언트가 보낸 「다시 뽑기」 요청을 검증하고 후보를 갈아 끼운다.
	 *
	 * <p>클라이언트가 보내는 것은 「눌렀다」는 사실과 어느 창인지뿐이다. <b>남은 횟수를 세는
	 * 것도 새 후보를 뽑는 것도 전부 여기서 한다.</b> 그래서 창을 조작해도 무한히 다시 뽑을 수
	 * 없고, 등급을 올리거나 이미 가진 증강을 다시 받게 만들 수도 없다.
	 *
	 * <p>다음 중 하나라도 어긋나면 <b>아무 말 없이 돌아간다.</b> 지연·재전송된 패킷과 조작된
	 * 패킷을 같은 길로 버리기 위해서다 — 실패 이유를 알려 주면 그 자체가 조작의 힌트가 된다.
	 *
	 * <ul>
	 *   <li>팀·상태가 없거나 증강을 쓰지 않는 팀</li>
	 *   <li>지금 진행 중인 강제 선택 세션이 아님 (선택창이 떠 있는 단계가 아닌 경우 포함)</li>
	 *   <li>보낸 사람이 이 선택권의 선택자가 아님 — 관전자는 못 누른다</li>
	 *   <li>이번 회차에 남은 횟수가 0</li>
	 *   <li>같은 등급에서 새로 뽑을 후보가 하나도 없음 — 이때는 <b>횟수도 깎지 않는다</b></li>
	 * </ul>
	 */
	public static void applyReroll(ServerPlayer player, int milestone) {
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
		if (!PerkChoiceSession.acceptsReroll(team.teamId(), milestone)) {
			return;
		}
		PendingOffer offer = state.pending.getFirst();
		if (offer.milestone() != milestone || !offer.isChooser(player.getUUID())) {
			return;
		}
		if (state.rerollsRemaining <= 0) {
			return;
		}
		// 다시 뽑아도 등급은 그대로다. 「골드 라운드에서 다시 뽑았더니 실버가 나왔다」가 되면
		// 안 되고, 반대로 프리즘이 나와도 안 된다. 후보에서 등급을 읽지 못하면 아무것도 하지
		// 않는다 — 등급을 모르는 채로 뽑으면 구간 규칙을 다시 굴리는 셈이 된다.
		PerkRarity rarity = offerRarity(offer);
		if (rarity == null) {
			return;
		}
		RandomSource random = server.overworld().getRandom();
		// 이미 가진 증강은 PerkDraft 가 등급을 가리지 않고 언제나 뺀다. 다시 뽑기도 같은 길을
		// 지나므로 재추첨 결과에 보유 증강이 섞일 수 없다. 구간을 함께 넘겨야 min_level 이
		// 걸린 증강(예: 30렙부터인 프리즘 「환골탈태」)이 이른 구간에 튀어나오지 않는다.
		List<String> options = PerkDraft.draw(
				rarity, milestone, PerkRegistry.all(), state.ownedPerks, random, OPTION_COUNT);
		if (options.isEmpty()) {
			// 뽑을 것이 없으면 창이 비어 버린다. 횟수를 깎지 않고 지금 후보를 그대로 둔다.
			SharedFateMod.LOGGER.warn(
					"{}렙 구간을 다시 뽑으려 했지만 {} 등급에 남은 후보가 없어 그대로 둡니다.",
					milestone, rarity.displayName());
			return;
		}

		state.pending.set(0, new PendingOffer(milestone, offer.chooser(), options));
		state.rerollsRemaining--;
		manager.setDirty();
		// 제한시간을 60초로 되돌리고 바뀐 후보를 팀 전원에게 다시 보낸다. 시간 정지와 무적은
		// 그대로다.
		PerkChoiceSession.onRerolled(server, team.teamId(), milestone);
		broadcastSync(server, team, state);
		broadcast(server, team, Component.literal(
				"[증강] " + player.getGameProfile().name() + "님이 후보를 다시 뽑았습니다. 남은 횟수 "
						+ state.rerollsRemaining + "회."));
	}

	/**
	 * 제한시간이 끝났을 때 후보 중 하나를 무작위로 골라 적용한다.
	 *
	 * <p>{@link PerkChoiceSession} 이 시간을 이미 녹인 뒤에 부른다. <b>고를 수 있는 후보가 하나도
	 * 없더라도 이 선택권은 반드시 대기열에서 사라진다.</b> 남겨 두면 다음 감지 주기에 같은
	 * 선택권으로 다시 얼어붙어 영원히 빠져나오지 못한다.
	 */
	static void applyRandomChoice(MinecraftServer server, UUID teamId, int milestone) {
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamById(teamId);
		TeamState state = manager.stateByTeamId(teamId);
		if (team == null || state == null || state.pending.isEmpty()) {
			return;
		}
		PendingOffer offer = state.pending.getFirst();
		if (offer.milestone() != milestone) {
			return;
		}

		Perk perk = pickRandomTakeable(server, state, offer);
		if (perk == null) {
			state.pending.removeFirst();
			manager.setDirty();
			broadcastSync(server, team, state);
			SharedFateMod.LOGGER.warn(
					"{}렙 구간 선택권에 고를 수 있는 후보가 없어 그대로 버립니다.", milestone);
			broadcast(server, team, Component.literal(
					"[증강] 시간이 다 되었지만 고를 수 있는 후보가 없어 이번 선택권은 사라집니다."));
			return;
		}
		RandomSource random = server.overworld().getRandom();
		commit(server, manager, team, state, perk,
				"시간이 다 되어 " + gradeAndName(perk) + " 이(가) 무작위로 선택되었습니다.", random);
		// 자동 선택도 직접 고른 것과 똑같이 결과를 보여 준다. 고른 사람 이름은 비운다.
		PerkChoiceSession.onChoiceApplied(server, teamId, milestone, perk.id(), "");
	}

	/** 후보 중 지금 실제로 가져갈 수 있는 것 하나를 무작위로 고른다. 하나도 없으면 null. */
	private static @Nullable Perk pickRandomTakeable(MinecraftServer server, TeamState state,
			PendingOffer offer) {
		List<Perk> takeable = new ArrayList<>();
		for (String id : offer.optionIds()) {
			Perk perk = PerkRegistry.byId(id).orElse(null);
			if (perk != null && !state.ownedPerks.contains(id)) {
				takeable.add(perk);
			}
		}
		if (takeable.isEmpty()) {
			return null;
		}
		RandomSource random = server.overworld().getRandom();
		return takeable.get(random.nextInt(takeable.size()));
	}

	/** 선택을 실제로 반영한다. 직접 고른 경우와 자동 선택이 같은 길을 지나게 하는 자리다. */
	private static void commit(MinecraftServer server, TeamManager manager, ShareTeam team,
			TeamState state, Perk perk, String announcement, RandomSource random) {
		if (!state.ownedPerks.contains(perk.id())) {
			state.ownedPerks.add(perk.id());
		}
		state.pending.removeFirst();
		manager.setDirty();

		// 즉시 지급은 여기서만 일어난다. refreshPlayer 는 접속·부활 때마다 다시 도는 길이라
		// 거기에 두면 접속할 때마다 아이템이 불어난다. 한 증강은 한 회차에 한 번만 고를 수 있으므로
		// 이 자리를 지나는 횟수도 증강마다 한 번뿐이다.
		//
		// item_grant·legacy_gear·gambler·rarity_grant·rarity_reroll 다섯 즉시 지급 효과는
		// PerkGrantChain 이 한 곳에서 처리한다. 무작위로 받은 증강이 또 즉시 지급 효과를
		// 가지고 있으면(예: 「숨은 재능」이 뽑은 골드가 하필 「하늘의 은총」인 경우) 그것도
		// 마저 처리해야 실제로 손에 들어온 증강이 전부 발동하기 때문이다. 자세한 내용과
		// 무한 재귀를 막는 방법은 그 클래스에 적어 뒀다.
		PerkGrantChain.run(server, team, state, perk, random);

		applyToTeam(server, team, state);
		// 몹에게 걸리는 증강은 폴링으로도 따라잡지만, 고른 즉시 반영되는 편이 자연스럽다.
		MobPerkModifiers.invalidateNow(server);
		broadcastSync(server, team, state);
		broadcast(server, team, Component.literal(
				"[증강] " + announcement + " 팀 전체에 적용됩니다."));
	}

	private static String gradeAndName(Perk perk) {
		return perk.rarity().displayName() + " 등급 " + perk.name();
	}

	// ------------------------------------------------------------------ 효과 적용

	/** 한 플레이어에게 팀이 보유한 증강 효과를 전부 다시 맞춘다. */
	public static void refreshPlayer(ServerPlayer player) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return;
		}
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				try {
					effect.apply(player);
				} catch (RuntimeException error) {
					SharedFateMod.LOGGER.warn("증강 '{}' 효과 적용에 실패했습니다.", perk.id(), error);
				}
			}
		}
	}

	/**
	 * 팀의 증강 사용 여부를 바꾸고, 이미 붙어 있던 효과까지 정리한다.
	 *
	 * <p>피해 배율이나 교환 규칙처럼 <b>그때그때 계산에 끼어드는</b> 효과는 조회할 때마다
	 * {@code perksEnabled} 를 보므로 플래그만 내리면 곧바로 멈춘다. 반면 속성·상태이상처럼
	 * <b>플레이어에게 붙여 둔</b> 효과는 아무도 걷어내지 않으면 그대로 남는다. 그래서 끌 때는
	 * 여기서 직접 {@link PerkEffect#remove} 를 돌려 준다.
	 *
	 * <p>보유 목록({@code ownedPerks})은 건드리지 않는다. 실수로 껐다가 다시 켰을 때 회차가
	 * 통째로 날아가지 않게 하기 위해서다.
	 *
	 * @param enabled 켤 것인가
	 */
	public static void setPerksEnabled(MinecraftServer server, ShareTeam team, TeamState state,
			boolean enabled) {
		state.perksEnabled = enabled;
		if (enabled) {
			applyToTeam(server, team, state);
			broadcastSync(server, team, state);
			return;
		}
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online == null) {
				continue;
			}
			for (String perkId : state.ownedPerks) {
				Perk perk = PerkRegistry.byId(perkId).orElse(null);
				if (perk == null) {
					continue;
				}
				for (PerkEffect effect : perk.effects()) {
					try {
						effect.remove(online);
					} catch (RuntimeException error) {
						SharedFateMod.LOGGER.warn("증강 '{}' 효과 해제에 실패했습니다.", perk.id(), error);
					}
				}
			}
		}
		broadcastSync(server, team, state);
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
		// 조건부 증강은 배율 조회에 플레이어 인자가 없어 대상을 따로 알려 줘야 한다.
		ConditionalPerkManager.beginMultiplierLookup(player);
		double total = 1.0;
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			if (perk == null) {
				continue;
			}
			for (PerkEffect effect : perk.effects()) {
				total *= dealt
						? effect.damageDealtMultiplier()
						: effect.damageTakenMultiplier();
			}
		}
		return Double.isFinite(total) && total > 0.0 ? total : 1.0;
	}

	// ------------------------------------------------------------------ 조회와 알림

	public static List<PerkSyncPayload.Owned> ownedLines(ServerPlayer player) {
		TeamState state = com.sharedfate.team.TeamLookup.stateOf(player.getUUID());
		if (state == null || state.ownedPerks.isEmpty()) {
			return List.of();
		}
		List<PerkSyncPayload.Owned> lines = new ArrayList<>();
		for (String perkId : state.ownedPerks) {
			Perk perk = PerkRegistry.byId(perkId).orElse(null);
			// 정의가 사라진 증강도 보유 목록에는 남아 있다. 식별자라도 보여 준다.
			lines.add(perk == null
					? new PerkSyncPayload.Owned(perkId, "정의를 찾을 수 없는 증강입니다.", "silver")
					: new PerkSyncPayload.Owned(perk.name(), perk.description(),
							perk.rarity().name().toLowerCase(java.util.Locale.ROOT)));
		}
		return lines;
	}

	/** 선택권이 다른 사람에게 넘어갔을 때만 쓰는 알림. 최초 발동 알림은 세션 쪽이 맡는다. */
	private static void announceOffer(MinecraftServer server, ShareTeam team, PendingOffer offer,
			@Nullable UUID chooser) {
		if (chooser == null) {
			return;
		}
		ServerPlayer picked = server.getPlayerList().getPlayer(chooser);
		String name = picked == null ? "팀원" : picked.getGameProfile().name();
		broadcast(server, team, Component.literal(
				"[증강] " + offer.milestone() + "렙 " + offerGradeLabel(offer) + " 선택권이 "
						+ name + "님에게 넘어갔습니다."));
	}

	/** 이 선택권의 등급 표시 문자열. 등급을 알 수 없으면 그냥 "증강". */
	static String offerGradeLabel(PendingOffer offer) {
		PerkRarity rarity = offerRarity(offer);
		return rarity == null ? "증강" : rarity.displayName() + " 등급 증강";
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
