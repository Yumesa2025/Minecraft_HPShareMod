package com.sharedfate.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sharedfate.perk.PerkManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * {@code /shareteam perk} 하위 명령.
 *
 * <p>{@link ShareTeamCommand}가 이미 저장소에서 가장 큰 파일이라 증강 관련 가지는 여기로 뺐다.
 * {@link ShareTeamCommand#register}가 {@code .then(PerkCommand.node())} 한 줄로 붙인다.
 * 그래서 이 클래스는 dispatcher에 직접 등록하지 않는다.
 */
public final class PerkCommand {
	private PerkCommand() {
	}

	/** {@code shareteam} 트리에 붙일 {@code perk} 가지. */
	public static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("perk")
				.executes(PerkCommand::open)
				.then(Commands.literal("list").executes(PerkCommand::list));
	}

	/** 대기 중인 선택권을 연다. 선택자가 아니면 관전 화면이 열린다. */
	private static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		PerkManager.OpenResult result = PerkManager.openOffer(self);
		switch (result) {
			case OPENED -> {
				context.getSource().sendSuccess(
						() -> Component.literal("증강 선택 창을 엽니다."), false);
				return 1;
			}
			case SPECTATING -> {
				context.getSource().sendSuccess(
						() -> Component.literal("다른 팀원이 고르는 중입니다. 화면을 읽기 전용으로 엽니다."), false);
				return 1;
			}
			case NO_TEAM -> {
				context.getSource().sendFailure(Component.literal(
						"팀이 없습니다. 증강은 팀 단위로만 얻습니다."));
				return 0;
			}
			case PERKS_DISABLED -> {
				context.getSource().sendFailure(Component.literal(
						"이 팀은 증강이 꺼져 있습니다. 증강 여부는 팀을 만들 때 정해지며 나중에 바꿀 수 없습니다."));
				return 0;
			}
			case NOTHING_PENDING -> {
				context.getSource().sendFailure(Component.literal(
						"대기 중인 선택권이 없습니다. 팀 공유 레벨이 3의 배수에 도달하면 생깁니다."));
				return 0;
			}
			default -> {
				context.getSource().sendFailure(Component.literal("증강 창을 열지 못했습니다."));
				return 0;
			}
		}
	}

	/** 팀이 지금까지 얻은 증강을 나열한다. */
	private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer self = context.getSource().getPlayerOrException();
		List<com.sharedfate.net.PerkSyncPayload.Owned> lines = PerkManager.ownedLines(self);
		if (lines.isEmpty()) {
			context.getSource().sendSuccess(
					() -> Component.literal("보유한 증강이 없습니다."), false);
			return 0;
		}
		// 글로 볼 때도 설명까지 함께 보여 준다. 이름만으로는 무엇을 들고 있는지 알기 어렵다.
		StringBuilder body = new StringBuilder("보유 증강 ").append(lines.size()).append("종");
		for (com.sharedfate.net.PerkSyncPayload.Owned owned : lines) {
			body.append("\n· ").append(owned.name()).append(" — ").append(owned.description());
		}
		String text = body.toString();
		context.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}
}
