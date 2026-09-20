package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.sharedfate.sync.RunProgressManager;
import com.sharedfate.sync.RunResetConfirmation;
import com.sharedfate.sync.RunResetCoordinator;
import com.sharedfate.sync.WorldResetCoordinator;
import com.sharedfate.team.TeamManager;
import com.sharedfate.ui.RunResetMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 서버를 처음 상태로 되돌리는 운영자 명령({@code /shareteam reset}).
 *
 * <p>{@link ShareTeamCommand#register} 가 {@code .then(RunResetCommand.node())} 로 붙인다
 * ({@link PerkCommand#node()} 와 같은 방식이다). 확인 낱말만은 <b>최상위에도</b> 따로
 * 등록한다 — {@link #registerTopLevel} 을 보라.
 *
 * <h2>왜 확인 낱말을 따로 두는가</h2>
 * <p>{@code disband} 나 {@code start} 는 {@code ... confirm} 을 뒤에 붙이는 방식이다. 여기서는
 * 그 방식을 쓰지 않고 {@code /yes}·{@code /수락}을 받는다. 이 명령이 겨누는 사람은 <b>남의
 * 서버를 받아 처음 여는 사람</b>이라, 확인 문구를 읽은 뒤 긴 명령을 다시 정확히 치게 하면
 * 오타가 난다. 대신 <b>친 사람만 30초 안에</b>로 안전을 건다.
 *
 * <h2>이중 잠금이 아니다</h2>
 * <p>{@link PerkTestCommand} 와 달리 설정 플래그를 두지 않는다. 이것은 시험 도구가 아니라
 * <b>정상적인 운영 절차</b>이고, 꺼 두면 「파일 넷을 손으로 지우세요」로 되돌아간다. 잠금은
 * 운영자 권한(level 2) 하나뿐이고, 되돌릴 수 없다는 사실은 되묻기가 감당한다.
 */
public final class RunResetCommand {
	/** {@code /shareteam} 뒤에 붙는 가지 이름. */
	public static final String LITERAL = "reset";

	/**
	 * 지금 수락을 기다리는 요청. 서버에 하나뿐이다.
	 *
	 * <p>여럿이 동시에 되묻기를 띄우면 누구의 {@code /yes} 가 무엇을 지우는지 알 수 없게 된다.
	 * 먼저 친 사람의 것이 끝나거나 만료될 때까지 뒤에 친 사람은 안내만 받는다.
	 */
	private static final RunResetConfirmation PENDING = new RunResetConfirmation();

	private RunResetCommand() {
	}

	/** {@code shareteam} 트리에 붙일 {@code reset} 가지. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal(LITERAL)
				.requires(RunResetCommand::gameMaster)
				.executes(RunResetCommand::prompt);
	}

	/**
	 * 확인 낱말 가지 하나. {@code shareteam} 트리에도, 최상위에도 같은 것을 쓴다.
	 *
	 * <p>낱말 목록은 {@link RunResetMessages#CONFIRM_WORDS} 하나뿐이다. 문구가 광고하는 낱말과
	 * 실제 가지 이름이 어긋나면 사람이 문구대로 쳤는데 아무 일도 안 일어나고, 그대로 30초가
	 * 지나 요청이 조용히 만료된다.
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> confirmNode(String word) {
		return Commands.literal(word)
				.requires(RunResetCommand::gameMaster)
				.executes(RunResetCommand::confirm);
	}

	/**
	 * {@code /yes} 와 {@code /수락} 을 최상위에 붙인다.
	 *
	 * <p>{@code /창고} 와 같은 이유로 {@code redirect} 를 쓰지 않는다. 리다이렉트는 <b>가지
	 * 전체</b>를 넘기는 장치인데 이것은 인자가 없는 한 줄 명령이다.
	 *
	 * <p>권한이 없는 사람에게는 이 명령이 <b>목록에도 뜨지 않는다.</b> {@code yes} 라는 흔한
	 * 낱말을 일반 플레이어의 명령 목록에 띄우지 않기 위해서이기도 하다.
	 */
	public static void registerTopLevel(CommandDispatcher<CommandSourceStack> dispatcher) {
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			dispatcher.register(confirmNode(word));
		}
	}

	private static boolean gameMaster(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	/**
	 * {@code /shareteam reset} — 무엇이 사라지는지 나열하고 기다리기만 한다.
	 *
	 * <p>이 명령 자체로는 <b>아무것도 바뀌지 않는다.</b> 실제로 지우는 것은 {@link #confirm}
	 * 이고, 거기 닿으려면 확인 낱말을 직접 쳐야 한다.
	 */
	private static int prompt(CommandContext<CommandSourceStack> context) {
		ServerPlayer self = context.getSource().getPlayer();
		if (self == null) {
			context.getSource().sendFailure(Component.literal(RunResetMessages.playerOnly()));
			return 0;
		}
		if (WorldResetCoordinator.countingDown()) {
			context.getSource().sendFailure(
					Component.literal(RunResetMessages.alreadyCountingDown()));
			return 0;
		}
		if (PENDING.pending() && !PENDING.isRequester(self.getUUID())) {
			context.getSource().sendFailure(Component.literal(RunResetMessages.alreadyWaiting(
					PENDING.requesterName(), PENDING.remainingSeconds())));
			return 0;
		}

		PENDING.request(self.getUUID(), self.getPlainTextName());
		int teams = TeamManager.get(context.getSource().getServer()).allTeams().size();
		String text = RunResetMessages.confirmation(RunProgressManager.runNumber(), teams,
				RunResetConfirmation.TIMEOUT_SECONDS);
		context.getSource().sendSuccess(
				() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
		return 1;
	}

	/** {@code /yes} · {@code /수락} — 실제로 지운다. 되돌릴 수 없다. */
	private static int confirm(CommandContext<CommandSourceStack> context) {
		ServerPlayer self = context.getSource().getPlayer();
		if (self == null) {
			context.getSource().sendFailure(Component.literal(RunResetMessages.playerOnly()));
			return 0;
		}
		switch (PENDING.answer(self.getUUID())) {
			case NOTHING_PENDING -> {
				context.getSource().sendFailure(
						Component.literal(RunResetMessages.nothingPending()));
				return 0;
			}
			case NOT_REQUESTER -> {
				// 남의 수락으로는 대기가 비워지지 않는다. 요청한 사람은 그대로 수락할 수 있다.
				context.getSource().sendFailure(Component.literal(
						RunResetMessages.notRequester(PENDING.requesterName())));
				return 0;
			}
			case ACCEPTED -> {
				// 대기는 answer 가 이미 비웠다. 아래로 내려간다.
			}
		}

		RunResetCoordinator.Result result =
				RunResetCoordinator.reset(context.getSource().getServer(), self);
		switch (result) {
			case STARTED -> {
				// 전원에게 나가는 공지는 카운트다운이 이미 뿌렸다. 여기서 또 적으면 친 사람에게만
				// 두 번 보인다.
				return 1;
			}
			case MARKER_FAILED -> context.getSource().sendFailure(
					Component.literal(RunResetMessages.markerFailed()));
			case ALREADY_RUNNING -> context.getSource().sendFailure(
					Component.literal(RunResetMessages.alreadyCountingDown()));
		}
		return 0;
	}

	/**
	 * 대기 시간을 한 틱 흘린다. {@code SharedFateMod} 가 매 틱 부른다.
	 *
	 * <p>만료를 <b>요청한 사람에게만</b> 알린다. 전원에게 뿌리면 운영자가 되묻기를 띄웠다가
	 * 그만둔 사실이 서버 전체에 방송된다.
	 */
	public static void tick(MinecraftServer server) {
		// 비워지기 전에 누구에게 알릴지 집어 둔다. tick 이 만료와 동시에 대기를 비운다.
		UUID requester = PENDING.requester();
		if (!PENDING.tick()) {
			return;
		}
		ServerPlayer player = requester == null ? null : server.getPlayerList().getPlayer(requester);
		if (player != null) {
			player.sendSystemMessage(
					Component.literal(RunResetMessages.expired()).withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * 서버가 멈출 때 대기를 버린다.
	 *
	 * <p>대기는 정적 상태라 한 프로세스에서 서버를 껐다 켜는 경우(개발 환경·싱글플레이)에
	 * 그대로 살아남는다. 다음 서버에서 {@code /yes} 한 번에 초기화가 도는 일을 막는다.
	 */
	public static void reset() {
		PENDING.clear();
	}

	/** 지금 수락을 기다리는 사람. 없으면 {@code null}. 시험과 상태 표시가 본다. */
	public static @Nullable UUID pendingRequester() {
		return PENDING.requester();
	}
}
