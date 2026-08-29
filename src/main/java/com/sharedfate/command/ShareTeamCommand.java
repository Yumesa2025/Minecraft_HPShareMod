package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.net.OpenTeamScreenPayload;
import com.sharedfate.perk.PerkHealthRules;
import com.sharedfate.perk.PerkManager;
import com.sharedfate.sync.InventorySwapper;
import com.sharedfate.sync.EffectSync;
import com.sharedfate.sync.MaxHealthAttribute;
import com.sharedfate.sync.StatMirror;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.UUID;
import java.util.stream.Collectors;

public final class ShareTeamCommand {
	private static final int MAX_TEAM_NAME_LENGTH = 32;

	private ShareTeamCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SharedFateConfig config) {
		dispatcher.register(Commands.literal("shareteam")
				.executes(ShareTeamCommand::openScreen)
				.then(Commands.literal("help").executes(ShareTeamCommand::help))
				// create 는 name 이 greedyString 이라 뒤에 인자를 못 붙인다.
				// 그래서 증강 켜고끄기는 이름 앞에 오는 별도 가지로 둔다.
				// 기존 /shareteam create <이름> 은 그대로 동작하고 증강은 꺼진 상태가 된다.
				.then(Commands.literal("create")
						.then(Commands.literal("perks")
								.then(Commands.literal("on")
										.then(Commands.argument("name", StringArgumentType.greedyString())
												.executes(context -> create(context, config, true))))
								.then(Commands.literal("off")
										.then(Commands.argument("name", StringArgumentType.greedyString())
												.executes(context -> create(context, config, false)))))
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.executes(context -> create(context, config, false))))
				.then(Commands.literal("invite")
						.then(Commands.argument("target", EntityArgument.player())
								.executes(context -> invite(context, config))))
				.then(Commands.literal("leave").executes(ShareTeamCommand::leave))
				.then(Commands.literal("disband")
						.executes(ShareTeamCommand::disbandPrompt)
						.then(Commands.literal("confirm").executes(ShareTeamCommand::disband)))
				.then(Commands.literal("perks")
						.then(Commands.literal("on")
								.executes(context -> setPerksEnabled(context, true)))
						.then(Commands.literal("off")
								.executes(context -> setPerksEnabled(context, false))))
				.then(Commands.literal("health")
						.then(Commands.argument("value", IntegerArgumentType.integer(20, 40))
								.executes(ShareTeamCommand::setTeamHealth)))
				.then(Commands.literal("swap")
						.executes(ShareTeamCommand::positionSwapStatus)
						.then(Commands.literal("status").executes(ShareTeamCommand::positionSwapStatus))
						.then(Commands.literal("off").executes(ShareTeamCommand::disablePositionSwap))
						.then(Commands.literal("on")
								.then(Commands.argument("minutes", IntegerArgumentType.integer(
										TeamState.PositionSwapLimits.MIN_MINUTES,
										TeamState.PositionSwapLimits.MAX_MINUTES))
										.executes(ShareTeamCommand::enablePositionSwap)))
						.then(Commands.literal("start")
								.then(Commands.argument("minutes", IntegerArgumentType.integer(
										TeamState.PositionSwapLimits.MIN_MINUTES,
										TeamState.PositionSwapLimits.MAX_MINUTES))
										.executes(ShareTeamCommand::enablePositionSwap))))
				.then(PerkCommand.node())
				.then(Commands.literal("list").executes(ShareTeamCommand::list))
				.then(Commands.literal("status").executes(context -> status(context, config))));
	}

	private static int create(CommandContext<CommandSourceStack> context, SharedFateConfig config,
			boolean perksEnabled) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);

		if (manager.teamOf(self.getUUID()) != null) {
			context.getSource().sendFailure(Component.literal(
					"이미 팀에 속해 있습니다. 먼저 /shareteam leave를 사용하세요."));
			return 0;
		}
		// 팀이 여럿이면 몹 증강처럼 월드 전체에 걸리는 효과가 팀끼리 충돌한다.
		// 이미 있는 팀은 그대로 두고 새로 만드는 것만 막는다. 해체하면 다시 만들 수 있다.
		if (!manager.canCreateNewTeam(config.singleTeamOnly)) {
			String existing = manager.allTeams().stream().map(ShareTeam::name)
					.collect(Collectors.joining(", "));
			context.getSource().sendFailure(Component.literal(
					"이 서버에는 팀을 하나만 만들 수 있습니다. 이미 있는 팀: " + existing
							+ "\n그 팀의 리더에게 /shareteam invite 로 불러 달라고 하거나, "
							+ "리더가 /shareteam disband confirm 으로 해체한 뒤 다시 만드세요."));
			return 0;
		}

		String name = StringArgumentType.getString(context, "name").trim();
		if (name.isEmpty()) {
			context.getSource().sendFailure(Component.literal("팀 이름은 비어 있을 수 없습니다."));
			return 0;
		}
		if (name.length() > MAX_TEAM_NAME_LENGTH) {
			context.getSource().sendFailure(Component.literal(
					"팀 이름은 " + MAX_TEAM_NAME_LENGTH + "자 이하여야 합니다."));
			return 0;
		}
		if (manager.teamByName(name) != null) {
			context.getSource().sendFailure(Component.literal("같은 이름의 팀이 이미 있습니다: " + name));
			return 0;
		}

		TeamState initialState = initialState(self, config);
		// 증강 사용 여부는 팀 생성 시에만 정해지고 그 뒤로는 바꿀 수 없다.
		initialState.perksEnabled = perksEnabled;
		InventorySwapper.prepareJoin(self);
		ShareTeam team = manager.createTeam(name, self.getUUID(), initialState);
		if (team == null) {
			context.getSource().sendFailure(Component.literal("팀을 만들지 못했습니다."));
			return 0;
		}
		InventorySwapper.finishJoin(self, manager.stateOf(self.getUUID()));
		MaxHealthAttribute.apply(self, manager.stateOf(self.getUUID()).maxHealth);
		StatMirror.syncPlayerNow(team.teamId(), manager.stateOf(self.getUUID()), self);
		EffectSync.refreshPlayer(self);
		TeamBroadcaster.broadcast(context.getSource().getServer(), manager.teamOf(self.getUUID()));

		context.getSource().sendSuccess(() -> Component.literal(
				"팀 '" + name + "'을 만들었습니다. 증강: " + (perksEnabled ? "켬" : "끔")), false);
		return 1;
	}

	private static int invite(CommandContext<CommandSourceStack> context, SharedFateConfig config)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(context, "target");
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());

		if (team == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		if (!self.getUUID().equals(team.leader())) {
			context.getSource().sendFailure(Component.literal("리더만 초대할 수 있습니다."));
			return 0;
		}
		if (team.size() >= config.maxTeamSize) {
			context.getSource().sendFailure(Component.literal(
					"팀 정원이 찼습니다 (최대 " + config.maxTeamSize + "명)."));
			return 0;
		}
		if (self.getUUID().equals(target.getUUID())) {
			context.getSource().sendFailure(Component.literal("자기 자신은 초대할 수 없습니다."));
			return 0;
		}
		if (manager.teamOf(target.getUUID()) != null) {
			context.getSource().sendFailure(Component.literal(
					target.getPlainTextName() + "님은 이미 팀에 속해 있습니다."));
			return 0;
		}

		return joinTeam(context, config, target, team, self.getPlainTextName());
	}

	/**
	 * 초대받은 사람을 곧바로 팀에 넣는다.
	 *
	 * <p>수락 절차가 없으므로 <b>본인 확인 없이 개인 아이템이 드랍된다.</b> 리더만 부를 수
	 * 있다는 점이 유일한 안전장치라, 부르기 전에 정원과 소속을 모두 확인해 두어야 한다.
	 * 그 확인은 {@link #invite} 가 이미 마친 뒤 여기로 넘어온다.
	 *
	 * <p>아이템을 드랍하는 {@code prepareJoin} 뒤에 {@code addMember} 가 실패하면 되돌릴
	 * 방법이 없다. 그래서 실패 메시지가 드랍물을 주우라고 알린다.
	 */
	private static int joinTeam(CommandContext<CommandSourceStack> context, SharedFateConfig config,
			ServerPlayer target, ShareTeam team, String inviterName) {
		int personalExperience = config.shareExperience
				? StatMirror.currentExperiencePoints(target) : 0;
		InventorySwapper.prepareJoin(target);
		TeamManager manager = manager(context);
		if (!manager.addMember(team.teamId(), target.getUUID(), config.maxTeamSize)) {
			context.getSource().sendFailure(Component.literal(
					"팀 가입 처리 중 상태가 바뀌었습니다. 드랍된 개인 아이템을 회수해 주세요."));
			return 0;
		}

		TeamState joinedState = manager.stateOf(target.getUUID());
		if (config.shareExperience) {
			StatMirror.addSharedExperience(joinedState, personalExperience);
		}
		InventorySwapper.finishJoin(target, joinedState);
		MaxHealthAttribute.apply(target, joinedState.maxHealth);
		StatMirror.syncPlayerNow(team.teamId(), manager.stateOf(target.getUUID()), target);
		EffectSync.refreshPlayer(target);
		TeamBroadcaster.broadcast(context.getSource().getServer(), manager.teamOf(target.getUUID()));

		target.sendSystemMessage(Component.literal(
				inviterName + "님이 '" + team.name() + "' 팀에 넣었습니다.\n"
						+ "개인 인벤토리·장비"
						+ (config.shareEnderChest ? "·엔더상자" : "")
						+ " 아이템은 있던 자리에 드랍됐고, 개인 경험치는 공유 풀에 합쳐졌습니다."));
		context.getSource().sendSuccess(
				() -> Component.literal(target.getPlainTextName() + "님을 팀에 넣었습니다."), false);
		return 1;
	}

	private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		if (manager.teamOf(self.getUUID()) == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}

		ShareTeam team = manager.teamOf(self.getUUID());
		if (team.size() == 1) {
			InventorySwapper.disbandTeam(self, team, manager.stateOf(self.getUUID()), manager);
			context.getSource().sendSuccess(() -> Component.literal(
					"마지막 멤버가 탈퇴해 팀을 해체하고 공유 아이템을 드랍했습니다."), false);
			return 1;
		}

		InventorySwapper.prepareLeave(self);
		manager.removeMember(self.getUUID());
		InventorySwapper.finishLeave(self);
		if (com.sharedfate.SharedFateMod.config.shareExperience) {
			StatMirror.setTotalExperience(self, 0);
		}
		MaxHealthAttribute.remove(self);
		StatMirror.forget(self.getUUID());
		EffectSync.clearDetachedPlayer(self);
		TeamBroadcaster.sendEmpty(self);
		ShareTeam remaining = manager.teamById(team.teamId());
		if (remaining != null) {
			TeamBroadcaster.broadcast(context.getSource().getServer(), remaining);
		}
		context.getSource().sendSuccess(() -> Component.literal("팀에서 나왔습니다. 빈손입니다."), false);
		return 1;
	}

	private static int disband(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());

		if (team == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		if (!self.getUUID().equals(team.leader())) {
			context.getSource().sendFailure(Component.literal("리더만 해체할 수 있습니다."));
			return 0;
		}

		InventorySwapper.disbandTeam(self, team, manager.stateOf(self.getUUID()), manager);
		context.getSource().sendSuccess(() -> Component.literal(
				"팀을 해체하고 공유 아이템을 모두 드랍했습니다."), false);
		return 1;
	}

	private static int disbandPrompt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		ShareTeam team = manager(context).teamOf(self.getUUID());
		if (team == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		if (!self.getUUID().equals(team.leader())) {
			context.getSource().sendFailure(Component.literal("리더만 해체할 수 있습니다."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(
				"공유 아이템을 모두 드랍하고 팀을 해체하려면 /shareteam disband confirm 을 입력하세요."), false);
		return 1;
	}

	/**
	 * 인자 없는 {@code /shareteam}. 모드가 있는 클라이언트면 팀 화면을 연다.
	 *
	 * <p>모드가 없으면 창을 띄울 방법이 없으므로 예전처럼 도움말을 글로 찍는다.
	 * {@code /shareteam help} 는 어느 쪽이든 그대로 도움말이다.
	 */
	private static int openScreen(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		if (!ServerPlayNetworking.canSend(self, OpenTeamScreenPayload.TYPE)) {
			return help(context);
		}
		ServerPlayNetworking.send(self, OpenTeamScreenPayload.INSTANCE);
		return 1;
	}

	/**
	 * 팀의 증강 사용 여부를 바꾼다. 리더만 할 수 있다.
	 *
	 * <p>끄면 이미 받은 증강 효과도 함께 걷힌다. 다시 켜면 보유 목록은 남아 있으므로 그대로
	 * 되살아난다. 지우지 않는 이유는 실수로 껐을 때 회차가 통째로 날아가지 않게 하기 위해서다.
	 */
	private static int setPerksEnabled(CommandContext<CommandSourceStack> context, boolean enabled)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (!canManageTeam(context, self, team, state)) {
			return 0;
		}
		if (state.perksEnabled == enabled) {
			context.getSource().sendSuccess(() -> Component.literal(
					"증강은 이미 " + (enabled ? "켜져" : "꺼져") + " 있습니다."), false);
			return 0;
		}
		PerkManager.setPerksEnabled(context.getSource().getServer(), team, state, enabled);
		manager.setDirty();
		TeamBroadcaster.broadcast(context.getSource().getServer(), team);
		broadcastSystemMessage(context, team, "증강을 " + (enabled ? "켰습니다." : "껐습니다."));
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("""
				SharedFate 팀 명령
				/shareteam create <이름> — 현재 상태로 팀 생성 (증강 끔)
				/shareteam create perks <on|off> <이름> — 증강 사용 여부를 정해서 생성
				/shareteam — 팀 화면을 엽니다 (모드가 있는 클라이언트)
				/shareteam invite <플레이어> — 상대를 곧바로 팀에 넣습니다 (리더)
				/shareteam perks <on|off> — 증강 사용 여부 (리더)
				/shareteam status | list | leave | disband confirm
				/shareteam health <20~40> — 팀 공유 최대 체력 설정 (리더)
				/shareteam swap on <1~120분> | off | status — 주기적 위치 교환
				/shareteam perk | perk list — 증강 선택 창 열기 / 보유 증강 보기
				가입하면 개인 아이템은 드랍되고 개인 경험치는 공유 풀에 합쳐집니다.
				""".strip()), false);
		return 1;
	}

	private static int status(CommandContext<CommandSourceStack> context, SharedFateConfig config)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (team == null || state == null) {
			context.getSource().sendSuccess(() -> Component.literal(
					"팀이 없습니다. 팀 리더에게 /shareteam invite 로 불러 달라고 하세요."), false);
			return 0;
		}
		long online = team.members().stream().filter(uuid ->
				context.getSource().getServer().getPlayerList().getPlayer(uuid) != null).count();
		String text = "팀 '" + team.name() + "' — " + online + "/" + team.size() + "명 온라인"
				+ "\n체력 " + String.format(java.util.Locale.ROOT, "%.1f/%.1f", state.health, state.maxHealth)
				+ ", 허기 " + state.foodLevel + "/20, 경험치 " + state.totalExperience
				+ "\n공유: 6줄 인벤토리=" + (config.mainInventoryRows == 6)
				+ ", 엔더상자=" + config.shareEnderChest
				+ ", 경험치=" + config.shareExperience
				+ ", 효과=" + config.shareStatusEffects
				+ "\n위치 교환=" + (state.positionSwapEnabled()
						? state.positionSwapIntervalMinutes() + "분 주기" : "꺼짐");
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int setTeamHealth(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (!canManageTeam(context, self, team, state)) {
			return 0;
		}

		int maximum = IntegerArgumentType.getInteger(context, "value");
		// 명령이 정하는 것은 "팀이 정한 기본값" 이다. 증강 보너스나 고정을 얹어 실제로 걸릴
		// 상한을 내는 계산은 PerkHealthRules 한 곳에만 둔다. 그래야 증강을 잃었을 때 여기서
		// 적어 둔 값이 그대로 돌아온다.
		state.baseMaxHealth = maximum;
		state.maxHealth = PerkHealthRules.effectiveMaxHealth(state);
		// 공유 체력은 일부러 건드리지 않는다. 상한이 줄면 바닐라가 팀원의 현재 체력을 자르고
		// StatMirror 가 그 감소를 이번 틱의 피해로 관측해 공유 체력에서 뺀다. 여기서 미리 깎으면
		// 같은 감소가 두 번 들어가 팀이 전멸한다. 자세한 까닭은 PerkHealthRules 에 적어 뒀다.
		float effective = state.maxHealth;
		for (UUID memberId : team.members()) {
			ServerPlayer member = context.getSource().getServer().getPlayerList().getPlayer(memberId);
			if (member != null) {
				MaxHealthAttribute.apply(member, effective);
				StatMirror.syncPlayerNow(team.teamId(), state, member);
				member.sendSystemMessage(Component.literal(
						"팀 공유 최대 체력이 " + maximum + "으로 설정되었습니다."
								+ (effective == maximum ? ""
										: " (증강이 적용되어 지금은 " + trimZero(effective) + "입니다.)")));
			}
		}
		manager.setDirty();
		TeamBroadcaster.broadcast(context.getSource().getServer(), team);
		return 1;
	}

	/** 20.0 처럼 소수점이 의미 없는 값을 "20" 으로 보여 준다. */
	private static String trimZero(float value) {
		return value == Math.rint(value)
				? String.valueOf((long) value)
				: String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private static int enablePositionSwap(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (!canManageTeam(context, self, team, state)) {
			return 0;
		}
		int minutes = IntegerArgumentType.getInteger(context, "minutes");
		state.enablePositionSwap(minutes);
		manager.setDirty();
		broadcastSystemMessage(context, team,
				"랜덤 위치 교환을 켰습니다. 온라인 생존 팀원의 위치가 " + minutes + "분마다 서로 바뀝니다.");
		TeamBroadcaster.broadcast(context.getSource().getServer(), team);
		return 1;
	}

	private static int disablePositionSwap(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (!canManageTeam(context, self, team, state)) {
			return 0;
		}
		state.disablePositionSwap();
		manager.setDirty();
		broadcastSystemMessage(context, team, "랜덤 위치 교환을 껐습니다.");
		TeamBroadcaster.broadcast(context.getSource().getServer(), team);
		return 1;
	}

	private static int positionSwapStatus(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamState state = manager(context).stateOf(self.getUUID());
		if (state == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		if (!state.positionSwapEnabled()) {
			context.getSource().sendSuccess(() -> Component.literal("랜덤 위치 교환은 꺼져 있습니다."), false);
			return 1;
		}
		int seconds = (state.positionSwapRemainingTicks + 19) / 20;
		context.getSource().sendSuccess(() -> Component.literal(
				"랜덤 위치 교환: " + state.positionSwapIntervalMinutes()
						+ "분 주기, 다음 교환까지 약 " + seconds + "초"), false);
		return 1;
	}

	private static boolean canManageTeam(CommandContext<CommandSourceStack> context,
			ServerPlayer self, ShareTeam team, TeamState state) {
		if (team == null || state == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return false;
		}
		boolean moderator = context.getSource().permissions()
				.hasPermission(Permissions.COMMANDS_MODERATOR);
		if (!self.getUUID().equals(team.leader()) && !moderator) {
			context.getSource().sendFailure(Component.literal("팀 리더만 이 설정을 바꿀 수 있습니다."));
			return false;
		}
		return true;
	}

	private static void broadcastSystemMessage(CommandContext<CommandSourceStack> context,
			ShareTeam team, String message) {
		for (UUID memberId : team.members()) {
			ServerPlayer member = context.getSource().getServer().getPlayerList().getPlayer(memberId);
			if (member != null) {
				member.sendSystemMessage(Component.literal(message));
			}
		}
	}

	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());

		if (team == null) {
			context.getSource().sendSuccess(() -> Component.literal("팀이 없습니다."), false);
			return 0;
		}

		String members = team.members().stream()
				.map(uuid -> nameOf(context, uuid) + (uuid.equals(team.leader()) ? " (리더)" : ""))
				.collect(Collectors.joining(", "));
		context.getSource().sendSuccess(
				() -> Component.literal("팀 '" + team.name() + "' — " + members), false);
		return 1;
	}

	private static TeamManager manager(CommandContext<CommandSourceStack> context) {
		return TeamManager.get(context.getSource().getServer());
	}

	private static TeamState initialState(ServerPlayer player, SharedFateConfig config) {
		float maximum = (float) config.sharedMaxHealth;
		TeamState state = TeamState.fresh(maximum);
		state.health = Math.max(0.0F, Math.min(maximum, player.getHealth()));
		state.absorption = Math.max(0.0F, Math.min(player.getMaxAbsorption(), player.getAbsorptionAmount()));
		state.foodLevel = player.getFoodData().getFoodLevel();
		state.saturation = player.getFoodData().getSaturationLevel();
		if (config.shareExperience) {
			state.totalExperience = StatMirror.currentExperiencePoints(player);
			state.xpLevel = player.experienceLevel;
			state.xpProgress = player.experienceProgress;
		}
		if (config.shareStatusEffects) {
			player.getActiveEffects().forEach(effect -> state.effects.add(
					new net.minecraft.world.effect.MobEffectInstance(effect)));
		}
		return state;
	}

	private static String nameOf(CommandContext<CommandSourceStack> context, UUID uuid) {
		ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(uuid);
		return player != null ? player.getPlainTextName() : uuid.toString().substring(0, 8);
	}
}
