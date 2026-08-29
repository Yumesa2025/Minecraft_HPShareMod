package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.sync.InventorySwapper;
import com.sharedfate.sync.EffectSync;
import com.sharedfate.sync.MaxHealthAttribute;
import com.sharedfate.sync.StatMirror;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
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
				.executes(ShareTeamCommand::help)
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
				.then(Commands.literal("accept")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										TeamManager.get(context.getSource().getServer())
												.invitedTeams(context.getSource().getPlayerOrException().getUUID()).stream()
												.map(ShareTeam::name), builder))
								.executes(context -> accept(context, config))))
				.then(Commands.literal("invites").executes(ShareTeamCommand::invites))
				.then(Commands.literal("decline")
						.then(Commands.argument("name", StringArgumentType.greedyString())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										TeamManager.get(context.getSource().getServer())
												.invitedTeams(context.getSource().getPlayerOrException().getUUID()).stream()
												.map(ShareTeam::name), builder))
								.executes(ShareTeamCommand::decline)))
				.then(Commands.literal("leave").executes(ShareTeamCommand::leave))
				.then(Commands.literal("disband")
						.executes(ShareTeamCommand::disbandPrompt)
						.then(Commands.literal("confirm").executes(ShareTeamCommand::disband)))
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
							+ "\n/shareteam accept <이름> 으로 초대를 수락하거나, "
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
		if (manager.hasInvite(target.getUUID(), team.teamId())) {
			context.getSource().sendFailure(Component.literal("이미 초대한 플레이어입니다."));
			return 0;
		}

		manager.invite(team.teamId(), target.getUUID());
		target.sendSystemMessage(Component.literal(
				self.getPlainTextName() + "님이 '" + team.name() + "' 팀에 초대했습니다. "
						+ "/shareteam accept " + team.name() + " 으로 수락하세요.\n"
						+ "주의: 수락하면 개인 인벤토리·장비"
						+ (config.shareEnderChest ? "·엔더상자" : "")
						+ " 아이템은 현재 위치에 드랍되고, 개인 경험치는 공유 풀에 합쳐집니다."));
		context.getSource().sendSuccess(
				() -> Component.literal(target.getPlainTextName() + "님을 초대했습니다."), false);
		return 1;
	}

	private static int accept(CommandContext<CommandSourceStack> context, SharedFateConfig config)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		String name = StringArgumentType.getString(context, "name").trim();
		ShareTeam team = manager.teamByName(name);

		if (team == null) {
			context.getSource().sendFailure(Component.literal("그런 팀이 없습니다: " + name));
			return 0;
		}
		if (!manager.hasInvite(self.getUUID(), team.teamId())) {
			context.getSource().sendFailure(Component.literal("초대받지 않았습니다."));
			return 0;
		}
		if (manager.teamOf(self.getUUID()) != null) {
			context.getSource().sendFailure(Component.literal("이미 팀에 속해 있습니다."));
			return 0;
		}
		if (team.size() >= config.maxTeamSize) {
			context.getSource().sendFailure(Component.literal(
					"팀 정원이 찼습니다 (최대 " + config.maxTeamSize + "명)."));
			return 0;
		}

		int personalExperience = config.shareExperience
				? StatMirror.currentExperiencePoints(self) : 0;
		InventorySwapper.prepareJoin(self);
		if (!manager.addMember(team.teamId(), self.getUUID(), config.maxTeamSize)) {
			context.getSource().sendFailure(Component.literal(
					"팀 가입 처리 중 상태가 바뀌었습니다. 드랍된 개인 아이템을 회수해 주세요."));
			return 0;
		}

		TeamState joinedState = manager.stateOf(self.getUUID());
		if (config.shareExperience) {
			StatMirror.addSharedExperience(joinedState, personalExperience);
		}
		InventorySwapper.finishJoin(self, joinedState);
		MaxHealthAttribute.apply(self, joinedState.maxHealth);
		StatMirror.syncPlayerNow(team.teamId(), manager.stateOf(self.getUUID()), self);
		EffectSync.refreshPlayer(self);
		TeamBroadcaster.broadcast(context.getSource().getServer(), manager.teamOf(self.getUUID()));
		context.getSource().sendSuccess(() -> Component.literal("'" + team.name() + "' 팀에 들어왔습니다."), false);
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

	private static int help(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("""
				SharedFate 팀 명령
				/shareteam create <이름> — 현재 상태로 팀 생성 (증강 끔)
				/shareteam create perks <on|off> <이름> — 증강 사용 여부를 정해서 생성
				/shareteam invite <플레이어> | invites | accept <이름> | decline <이름>
				/shareteam status | list | leave | disband confirm
				/shareteam health <20~40> — 팀 공유 최대 체력 설정 (리더)
				/shareteam swap on <1~120분> | off | status — 주기적 위치 교환
				/shareteam perk | perk list — 증강 선택 창 열기 / 보유 증강 보기
				가입하면 개인 아이템은 드랍되고 개인 경험치는 공유 풀에 합쳐집니다.
				""".strip()), false);
		return 1;
	}

	private static int invites(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		String names = manager(context).invitedTeams(self.getUUID()).stream()
				.map(ShareTeam::name).sorted(String.CASE_INSENSITIVE_ORDER)
				.collect(Collectors.joining(", "));
		context.getSource().sendSuccess(() -> Component.literal(
				names.isEmpty() ? "대기 중인 팀 초대가 없습니다." : "받은 초대: " + names), false);
		return names.isEmpty() ? 0 : 1;
	}

	private static int decline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		String name = StringArgumentType.getString(context, "name").trim();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamByName(name);
		if (team == null || !manager.declineInvite(self.getUUID(), team.teamId())) {
			context.getSource().sendFailure(Component.literal("그 팀의 초대가 없습니다: " + name));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal("'" + team.name() + "' 팀 초대를 거절했습니다."), false);
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
					"팀이 없습니다. /shareteam invites 로 초대를 확인할 수 있습니다."), false);
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
		state.maxHealth = maximum;
		state.health = Math.min(state.health, state.maxHealth);
		for (UUID memberId : team.members()) {
			ServerPlayer member = context.getSource().getServer().getPlayerList().getPlayer(memberId);
			if (member != null) {
				MaxHealthAttribute.apply(member, state.maxHealth);
				StatMirror.syncPlayerNow(team.teamId(), state, member);
				member.sendSystemMessage(Component.literal(
						"팀 공유 최대 체력이 " + maximum + "으로 설정되었습니다."));
			}
		}
		manager.setDirty();
		return 1;
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
