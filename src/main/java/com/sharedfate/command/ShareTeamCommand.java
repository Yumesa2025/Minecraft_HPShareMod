package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.net.OpenTeamScreenPayload;
import com.sharedfate.sync.DifficultyEscalation;
import com.sharedfate.sync.GameStartManager;
import com.sharedfate.sync.InventorySwapper;
import com.sharedfate.sync.RunProgressManager;
import com.sharedfate.sync.EffectSync;
import com.sharedfate.sync.MaxHealthAttribute;
import com.sharedfate.sync.StatMirror;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamCreationSettings;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.stream.Collectors;

public final class ShareTeamCommand {
	private static final int MAX_TEAM_NAME_LENGTH = 32;

	private ShareTeamCommand() {
	}

	/**
	 * {@code /shareteam} 의 짧은 별칭.
	 *
	 * <p>바닐라에도 Fabric API 에도 {@code st} 라는 명령은 없어서 겹치지 않는다.
	 */
	public static final String ALIAS = "st";

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, SharedFateConfig config) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("shareteam")
				.executes(ShareTeamCommand::openScreen)
				.then(Commands.literal("help").executes(ShareTeamCommand::help))
				.then(createNode(config))
				.then(Commands.literal("invite")
						.then(Commands.argument("target", EntityArgument.player())
								.executes(context -> invite(context, config))))
				// 회차를 실제로 시작하는 자리. 아이템이 전부 사라지는 동작이라 disband 와 같이
				// confirm 을 요구한다. 확인 낱말이 없으면 안내만 하고 아무것도 하지 않는다.
				.then(Commands.literal("start")
						.executes(ShareTeamCommand::startPrompt)
						.then(Commands.literal("confirm").executes(ShareTeamCommand::startRun)))
				.then(Commands.literal("leave").executes(ShareTeamCommand::leave))
				.then(Commands.literal("disband")
						.executes(ShareTeamCommand::disbandPrompt)
						.then(Commands.literal("confirm").executes(ShareTeamCommand::disband)))
				// 아래 셋은 팀을 만들 때만 정한다. 가지를 없애 버리면 예전 판에서 쓰던 대로
				// 친 사람에게 「알 수 없는 명령」만 뜨고 왜 안 되는지 알 길이 없다. 그래서
				// 가지는 그대로 두고 안내만 돌려준다. 읽기만 하는 swap status 는 살아 있다.
				.then(Commands.literal("perks")
						.then(Commands.literal("on")
								.executes(context -> lockedSetting(context,
										TeamCreationSettings.Locked.PERKS)))
						.then(Commands.literal("off")
								.executes(context -> lockedSetting(context,
										TeamCreationSettings.Locked.PERKS))))
				.then(Commands.literal("difficulty")
						.executes(ShareTeamCommand::difficultyStatus)
						.then(Commands.literal("status").executes(ShareTeamCommand::difficultyStatus))
						.then(Commands.literal("on").executes(context -> lockedSetting(context,
								TeamCreationSettings.Locked.DIFFICULTY)))
						.then(Commands.literal("off").executes(context -> lockedSetting(context,
								TeamCreationSettings.Locked.DIFFICULTY))))
				.then(Commands.literal("health")
						.executes(context -> lockedSetting(context,
								TeamCreationSettings.Locked.MAX_HEALTH))
						.then(Commands.argument("value", IntegerArgumentType.integer(
										TeamCreationSettings.MIN_MAX_HEALTH,
										TeamCreationSettings.MAX_MAX_HEALTH))
								.executes(context -> lockedSetting(context,
										TeamCreationSettings.Locked.MAX_HEALTH))))
				.then(Commands.literal("swap")
						.executes(ShareTeamCommand::positionSwapStatus)
						.then(Commands.literal("status").executes(ShareTeamCommand::positionSwapStatus))
						.then(Commands.literal("off").executes(context -> lockedSetting(context,
								TeamCreationSettings.Locked.POSITION_SWAP)))
						.then(swapSettingNode("on"))
						.then(swapSettingNode("start")))
				.then(PerkCommand.node())
				// 운영자 전용 증강 시험 명령. 권한이 없으면 이 가지는 아예 보이지 않는다.
				.then(PerkTestCommand.node())
				.then(Commands.literal("list").executes(ShareTeamCommand::list))
				.then(Commands.literal("status").executes(context -> status(context, config))));
		registerAlias(dispatcher, root);
	}

	/**
	 * {@code /st} 를 {@code /shareteam} 의 별칭으로 붙인다.
	 *
	 * <p>{@code redirect} 는 <b>뒤에 뭔가 더 붙었을 때</b> 어디서부터 이어 읽을지만 정한다.
	 * {@code /st} 만 쳤을 때 무엇을 할지는 이 노드 <b>자신의</b> {@code command} 라, 그냥
	 * {@code redirect} 만 걸면 인자 없는 {@code /st} 는 아무 일도 하지 않는다. 그래서 원본의
	 * {@code command} 를 그대로 옮겨 심는다. 이러면 {@code /st} 도 팀 화면을 연다.
	 *
	 * <p>자식은 하나도 달지 않는다. 브리가디어는 {@code redirect} 가 걸린 노드에 자식을
	 * 더하는 것을 금지하고, 어차피 파싱이 원본 트리로 넘어가므로 달 필요도 없다. 덕분에
	 * 나중에 {@code /shareteam} 에 하위 명령이 늘어도 별칭은 손댈 것이 없다.
	 */
	private static void registerAlias(CommandDispatcher<CommandSourceStack> dispatcher,
			LiteralCommandNode<CommandSourceStack> root) {
		dispatcher.register(Commands.literal(ALIAS)
				.executes(root.getCommand())
				.redirect(root));
	}

	/**
	 * {@code create} 가지를 만든다.
	 *
	 * <p>{@code name} 이 greedyString 이라 뒤에는 아무것도 못 붙는다. 그래서 팀이 정할 일곱
	 * 가지는 모두 <b>이름 앞에</b> 정해진 순서로 온다.
	 *
	 * <pre>
	 * /shareteam create [perks on|off] [damagealert on|off] [deathalert on|off]
	 *                   [difficulty on|off] [health &lt;20~40&gt;] [swap off|&lt;1~120&gt;]
	 *                   [reroll &lt;0~10&gt;] &lt;이름&gt;
	 * </pre>
	 *
	 * <p>각 단계에서 곧바로 이름으로 빠져나갈 수 있으므로 {@code /shareteam create 우리팀} 도
	 * {@code /shareteam create perks on 우리팀} 도 그대로 동작한다. 적지 않은 것은 기본값이다 —
	 * 증강은 {@linkplain TeamCreationSettings#DEFAULT_PERKS_ENABLED 켬}, 두 알림과 난이도
	 * 상승은 끔, 최대 체력은 서버 설정값, 위치 교환은 끔, 다시 뽑기는
	 * {@linkplain TeamCreationSettings#DEFAULT_REROLL_COUNT 회차당 3회}.
	 * 팀 화면은 늘 완전한 형태를 보낸다.
	 *
	 * <p>가지를 손으로 다 적으면 갈래가 수십 개가 되어 어디가 빠졌는지 눈으로 못 찾는다.
	 * 그래서 켜고 끄기 한 쌍씩 겹쳐 쌓고, 여기까지 읽은 설정은 {@link TeamCreationSettings}
	 * 하나에 담아 다음 단계로 넘긴다. 각 단계의 꼬리는 {@link #withCreateOptions} 가 붙인다.
	 *
	 * <p><b>주의</b> — {@code perks} {@code damagealert} {@code deathalert} {@code difficulty}
	 * {@code health} {@code swap} 은 이 자리에서 예약어다. 브리가디어는 같은 자리에 리터럴이
	 * 맞으면 인자를 아예 보지 않으므로, 이 여섯 낱말로 <b>시작하는</b> 팀 이름은 만들 수 없다.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> createNode(SharedFateConfig config) {
		TeamCreationSettings base = TeamCreationSettings.defaults((float) config.sharedMaxHealth);
		LiteralArgumentBuilder<CommandSourceStack> create =
				withCreateOptions(Commands.literal("create"), config, base);

		LiteralArgumentBuilder<CommandSourceStack> perks = Commands.literal("perks");
		for (boolean perksEnabled : BOTH) {
			TeamCreationSettings afterPerks = base.withPerks(perksEnabled);
			LiteralArgumentBuilder<CommandSourceStack> perksValue =
					withCreateOptions(onOff(perksEnabled), config, afterPerks);

			LiteralArgumentBuilder<CommandSourceStack> damage = Commands.literal("damagealert");
			for (boolean damageAlert : BOTH) {
				TeamCreationSettings afterDamage = afterPerks.withDamageAlert(damageAlert);
				LiteralArgumentBuilder<CommandSourceStack> damageValue =
						withCreateOptions(onOff(damageAlert), config, afterDamage);

				LiteralArgumentBuilder<CommandSourceStack> death = Commands.literal("deathalert");
				for (boolean deathAlert : BOTH) {
					TeamCreationSettings afterDeath = afterDamage.withDeathAlert(deathAlert);
					LiteralArgumentBuilder<CommandSourceStack> deathValue =
							withCreateOptions(onOff(deathAlert), config, afterDeath);

					LiteralArgumentBuilder<CommandSourceStack> difficulty =
							Commands.literal("difficulty");
					for (boolean escalation : BOTH) {
						difficulty.then(withCreateOptions(onOff(escalation), config,
								afterDeath.withDifficultyEscalation(escalation)));
					}
					deathValue.then(difficulty);
					death.then(deathValue);
				}
				damageValue.then(death);
				damage.then(damageValue);
			}
			perksValue.then(damage);
			perks.then(perksValue);
		}
		create.then(perks);
		return create;
	}

	/**
	 * 켜고 끄기를 여기까지 읽은 자리에 「이름으로 끝내기 · 최대 체력 · 위치 교환 · 다시 뽑기」
	 * 넷을 붙인다.
	 *
	 * <p>네 갈래를 단계마다 손으로 적으면 같은 코드가 마흔 번 나온다.
	 */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withCreateOptions(
			T parent, SharedFateConfig config, TeamCreationSettings settings) {
		parent.then(createName(config, settings));
		parent.then(createHealthNode(config, settings));
		parent.then(createSwapNode(config, settings));
		parent.then(createRerollNode(config, settings));
		return parent;
	}

	/** {@code health <20~40>} 다음에는 이름으로 끝내거나 위치 교환·다시 뽑기를 더 적을 수 있다. */
	private static LiteralArgumentBuilder<CommandSourceStack> createHealthNode(
			SharedFateConfig config, TeamCreationSettings settings) {
		return Commands.literal("health").then(
				Commands.argument("health", IntegerArgumentType.integer(
								TeamCreationSettings.MIN_MAX_HEALTH,
								TeamCreationSettings.MAX_MAX_HEALTH))
						.then(createName(config, settings))
						.then(createSwapNode(config, settings))
						.then(createRerollNode(config, settings)));
	}

	/**
	 * {@code swap off} 또는 {@code swap <1~120>}. 뒤에는 이름이나 다시 뽑기가 온다.
	 *
	 * <p>{@code swap off} 는 적지 않은 것과 결과가 같지만, 팀 화면이 늘 완전한 형태를 보내므로
	 * 「끔」을 적을 자리가 있어야 한다.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> createSwapNode(
			SharedFateConfig config, TeamCreationSettings settings) {
		return Commands.literal("swap")
				.then(Commands.literal("off")
						.then(createName(config, settings))
						.then(createRerollNode(config, settings)))
				.then(Commands.argument("swapMinutes", IntegerArgumentType.integer(
								TeamState.PositionSwapLimits.MIN_MINUTES,
								TeamState.PositionSwapLimits.MAX_MINUTES))
						.then(createName(config, settings))
						.then(createRerollNode(config, settings)));
	}

	/**
	 * {@code reroll <0~10>}. 증강 선택창에서 후보를 다시 뽑을 수 있는 <b>회차당</b> 횟수다.
	 *
	 * <p>순서상 마지막 항목이라 뒤에는 이름만 온다. 적지 않으면
	 * {@linkplain TeamCreationSettings#DEFAULT_REROLL_COUNT 3회}이고, 0 으로 적으면 그 팀은
	 * 다시 뽑기를 아예 쓰지 않는다.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> createRerollNode(
			SharedFateConfig config, TeamCreationSettings settings) {
		return Commands.literal("reroll").then(
				Commands.argument("rerollCount", IntegerArgumentType.integer(
								TeamCreationSettings.MIN_REROLL_COUNT,
								TeamCreationSettings.MAX_REROLL_COUNT))
						.then(createName(config, settings)));
	}

	/** 예전에 위치 교환을 켜던 {@code swap on|start <분>}. 이제는 안내만 돌려준다. */
	private static LiteralArgumentBuilder<CommandSourceStack> swapSettingNode(String literal) {
		return Commands.literal(literal)
				.executes(context -> lockedSetting(context,
						TeamCreationSettings.Locked.POSITION_SWAP))
				.then(Commands.argument("minutes", IntegerArgumentType.integer(
								TeamState.PositionSwapLimits.MIN_MINUTES,
								TeamState.PositionSwapLimits.MAX_MINUTES))
						.executes(context -> lockedSetting(context,
								TeamCreationSettings.Locked.POSITION_SWAP)));
	}

	private static final boolean[] BOTH = {true, false};

	private static String onOffText(boolean value) {
		return TeamCreationSettings.onOff(value);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> onOff(boolean value) {
		return Commands.literal(value ? "on" : "off");
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> createName(
			SharedFateConfig config, TeamCreationSettings settings) {
		return Commands.argument("name", StringArgumentType.greedyString())
				.executes(context -> create(context, config, settings));
	}

	/**
	 * 이번 {@code create} 에서 정해진 설정을 모은다.
	 *
	 * <p>켜고 끄기 넷은 어느 리터럴 가지를 지나왔는지로 정해져 {@code settings} 에 이미 실려
	 * 있고, 숫자 셋은 <b>적었을 때만</b> 인자로 존재한다. 안 적은 단계를 기본값으로 두는 길이
	 * 이것뿐이다.
	 */
	private static TeamCreationSettings creationSettings(CommandContext<CommandSourceStack> context,
			TeamCreationSettings settings) {
		Integer health = optionalInteger(context, "health");
		Integer swapMinutes = optionalInteger(context, "swapMinutes");
		Integer rerollCount = optionalInteger(context, "rerollCount");
		TeamCreationSettings result = health == null
				? settings : settings.withMaxHealth(health.floatValue());
		result = swapMinutes == null ? result : result.withSwapIntervalMinutes(swapMinutes);
		return rerollCount == null ? result : result.withRerollCount(rerollCount);
	}

	/** 그 이름의 인자를 안 지나왔으면 {@code null}. 브리가디어는 없는 인자를 예외로 알린다. */
	private static @Nullable Integer optionalInteger(
			CommandContext<CommandSourceStack> context, String name) {
		try {
			return IntegerArgumentType.getInteger(context, name);
		} catch (IllegalArgumentException notGiven) {
			return null;
		}
	}

	private static int create(CommandContext<CommandSourceStack> context, SharedFateConfig config,
			TeamCreationSettings chosen) throws CommandSyntaxException {
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

		TeamCreationSettings settings = creationSettings(context, chosen);
		TeamState initialState = initialState(self, config);
		settings.applyTo(initialState);
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
				"팀 '" + name + "'을 만들었습니다."
						+ "\n" + settings.summary()
						+ "\n위의 설정은 모두 팀을 만들 때만 정합니다. 나중에 바꿀 수 없습니다."
						// 팀을 만들었다고 회차가 시작되지는 않는다. 다음에 무엇을 해야 하는지
						// 여기서 알려 주지 않으면 아무도 시작되지 않은 채로 돌아다닌다.
						+ "\n아직 회차는 시작되지 않았습니다. 팀원을 모두 부른 뒤"
						+ " /shareteam start 로 게임을 시작하세요."), false);
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

	/**
	 * {@code /shareteam start} — 무엇이 사라지는지 알리기만 한다.
	 *
	 * <p>이 명령 자체로는 <b>아무것도 바뀌지 않는다.</b> 실제로 시작하는 것은
	 * {@link #startRun} 이고, 거기 닿으려면 {@code confirm} 을 직접 쳐야 한다.
	 * 되돌릴 수 없는 동작이라 {@code disband} 와 같은 방식을 그대로 쓴다.
	 */
	private static int startPrompt(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamManager manager = manager(context);
		ShareTeam team = manager.teamOf(self.getUUID());
		TeamState state = manager.stateOf(self.getUUID());
		if (team == null || state == null) {
			context.getSource().sendFailure(Component.literal(
					"팀이 없습니다. 먼저 /shareteam create 로 팀을 만드세요."));
			return 0;
		}
		if (!self.getUUID().equals(team.leader())) {
			context.getSource().sendFailure(Component.literal("리더만 게임을 시작할 수 있습니다."));
			return 0;
		}
		if (state.runStarted) {
			context.getSource().sendFailure(Component.literal(
					"이미 " + RunProgressManager.runNumber() + "회차가 진행 중입니다."
							+ " 다시 시작하려면 팀이 전멸해 회차가 넘어가야 합니다."));
			return 0;
		}

		long online = team.members().stream().filter(uuid ->
				context.getSource().getServer().getPlayerList().getPlayer(uuid) != null).count();
		context.getSource().sendSuccess(() -> Component.literal(
				"게임을 시작하면 되돌릴 수 없습니다."
						+ "\n· 공유 인벤토리·방어구·엔더상자의 모든 아이템이 사라집니다 (드랍되지 않습니다)"
						+ "\n· 공유 경험치가 0이 되고 증강 구간을 처음부터 다시 셉니다"
						+ "\n· 월드 시각이 1일차 아침으로 돌아갑니다"
						+ "\n· 접속 중인 팀원 " + online + "/" + team.size() + "명을 스폰으로 옮깁니다"
						+ (online < team.size()
								? "\n  접속하지 않은 팀원은 옮길 수 없습니다. 다 모인 뒤에 시작하십시오."
								: "")
						+ "\n시작하려면 /shareteam start confirm 을 입력하세요."), false);
		return 1;
	}

	/** {@code /shareteam start confirm} — 실제로 회차를 시작한다. 되돌릴 수 없다. */
	private static int startRun(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		GameStartManager.StartResult result =
				GameStartManager.start(context.getSource().getServer(), self);
		switch (result) {
			case STARTED -> {
				// 안내는 GameStartManager 가 팀 전원에게 이미 보냈다. 여기서 또 적으면 리더에게만
				// 두 번 보인다.
				return 1;
			}
			case NO_TEAM -> context.getSource().sendFailure(Component.literal(
					"팀이 없습니다. 먼저 /shareteam create 로 팀을 만드세요."));
			case NOT_LEADER -> context.getSource().sendFailure(Component.literal(
					"리더만 게임을 시작할 수 있습니다."));
			case ALREADY_STARTED -> context.getSource().sendFailure(Component.literal(
					"이미 " + RunProgressManager.runNumber() + "회차가 진행 중입니다."));
		}
		return 0;
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
	 * 팀을 만든 뒤에는 바꿀 수 없는 설정이라고 알린다.
	 *
	 * <p>증강 사용 여부·공유 최대 체력·위치 교환은 <b>팀을 만드는 순간에만</b> 정한다.
	 * 까닭은 {@link TeamCreationSettings} 에 적어 뒀다. 리더인지 아닌지는 보지 않는다 —
	 * 리더도 못 바꾸는 것이라 「리더만 바꿀 수 있습니다」로 돌려보내면 거짓말이 된다.
	 */
	private static int lockedSetting(CommandContext<CommandSourceStack> context,
			TeamCreationSettings.Locked setting) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		if (manager(context).teamOf(self.getUUID()) == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		context.getSource().sendFailure(Component.literal(setting.message()));
		return 0;
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		context.getSource().sendSuccess(() -> Component.literal("""
				SharedFate 팀 명령
				/shareteam create <이름> — 기본 설정으로 팀 생성
				  (증강 켬 · 두 알림 끔 · 난이도 상승 끔 · 최대 체력은 서버 설정값 · 위치 교환 끔
				   · 증강 다시 뽑기 회차당 3회)
				/shareteam create [perks on|off] [damagealert on|off] [deathalert on|off]
				                  [difficulty on|off] [health <20~40>] [swap off|<1~120>]
				                  [reroll <0~10>] <이름>
				  — 이 일곱은 팀을 만들 때만 정합니다. 만든 뒤에는 바꿀 수 없습니다.
				  difficulty 를 켜면 30분마다 적대적 몹이 4%p 씩 세집니다 (엔더 드래곤 제외).
				  reroll 은 증강 선택창에서 후보 3장을 다시 뽑을 수 있는 회차당 횟수입니다.
				/shareteam start — 회차를 시작합니다 (리더). 무엇이 사라지는지 먼저 보여 줍니다
				/shareteam start confirm — 실제로 시작합니다. 되돌릴 수 없습니다
				  모든 아이템이 사라지고, 시각이 1일차 아침이 되고, 팀원이 스폰으로 모입니다.
				  누르기 전에는 증강 구간·위치 교환·난이도 상승이 돌지 않고 팀원은 죽지 않습니다.
				/shareteam — 팀 화면을 엽니다 (모드가 있는 클라이언트)
				/shareteam invite <플레이어> — 상대를 곧바로 팀에 넣습니다 (리더)
				/shareteam status — 지금 정해져 있는 설정을 봅니다
				/shareteam list | leave | disband confirm
				/shareteam swap status — 다음 위치 교환까지 남은 시간
				/shareteam difficulty status — 지금 몇 %까지 올랐는지
				/shareteam perk | perk list — 증강 선택 창 열기 / 보유 증강 보기
				가입하면 개인 아이템은 드랍되고 개인 경험치는 공유 풀에 합쳐집니다.
				/shareteam 은 /st 로 줄여 쓸 수 있습니다. 하위 명령은 모두 같습니다.
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
				// 회차 줄이 맨 위에 있어야 한다. 「시작 대기」인데 아래의 진행 상황부터 읽으면
				// 이미 회차가 굴러가는 줄 안다.
				+ "\n회차 — " + runLine(state)
				+ "\n체력 " + String.format(java.util.Locale.ROOT, "%.1f/%.1f", state.health, state.maxHealth)
				+ ", 허기 " + state.foodLevel + "/20, 경험치 " + state.totalExperience
				+ "\n공유: 6줄 인벤토리=" + (config.mainInventoryRows == 6)
				+ ", 엔더상자=" + config.shareEnderChest
				+ ", 경험치=" + config.shareExperience
				+ ", 효과=" + config.shareStatusEffects
				+ "\n— 팀을 만들 때 정한 설정 (바꿀 수 없습니다) —"
				+ "\n증강=" + onOffText(state.perksEnabled)
				+ "\n최대 체력=" + TeamCreationSettings.trimZero(state.baseMaxHealth)
				+ (state.maxHealth == state.baseMaxHealth ? ""
						: " (증강이 적용되어 지금은 "
								+ TeamCreationSettings.trimZero(state.maxHealth) + "입니다)")
				+ "\n위치 교환=" + (state.positionSwapEnabled()
						? state.positionSwapIntervalMinutes() + "분 주기" : "끔")
				+ "\n피격 알림=" + onOffText(state.damageAlertEnabled)
				+ ", 사망 알림=" + onOffText(state.deathAlertEnabled)
				+ "\n난이도 상승=" + DifficultyEscalation.describe(state)
				// 「회차당 몇 번」은 팀이 정한 값이라 위와 같은 묶음이지만, 「이번 회차에 몇 번
				// 남았는지」는 회차마다 달라지는 진행 상황이다. 한 줄에 같이 적어야 헷갈리지 않는다.
				+ "\n증강 다시 뽑기=회차당 " + state.rerollAllowance + "회"
				+ (state.rerollAllowance == 0 ? " (쓰지 않는 팀입니다)"
						: " (이번 회차에 " + state.rerollsRemaining + "회 남았습니다)")
				// 시험 명령을 썼거나 쓸 수 있는 서버라면 그 사실이 여기 남아야 한다.
				+ PerkTestCommand.statusLine(state);
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	/**
	 * {@code /shareteam status} 의 회차 한 줄.
	 *
	 * <p>시작 전에는 무엇을 눌러야 하는지까지 적는다. 「시작 대기」라고만 적으면 무엇을 기다리는
	 * 것인지 알 수 없다.
	 */
	private static String runLine(TeamState state) {
		int runNumber = RunProgressManager.runNumber();
		if (state.runStarted) {
			return runNumber + "회차 진행 중";
		}
		return runNumber + "회차 시작 대기 (리더가 /shareteam start 로 시작합니다)";
	}

	/** 난이도 상승이 지금 몇 %까지 올라와 있는지만 보여 준다. 바꾸지는 못한다. */
	private static int difficultyStatus(CommandContext<CommandSourceStack> context)
			throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		TeamState state = manager(context).stateOf(self.getUUID());
		if (state == null) {
			context.getSource().sendFailure(Component.literal("팀이 없습니다."));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.literal(
				"난이도 상승: " + DifficultyEscalation.describe(state)
						+ "\n" + DifficultyEscalation.STEP_MINUTES + "분마다 적대적 몹의 최대 체력과"
						+ " 공격력이 " + Math.round(DifficultyEscalation.STEP_BONUS * 100)
						+ "%p 씩 오릅니다 (엔더 드래곤 제외)."), false);
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
