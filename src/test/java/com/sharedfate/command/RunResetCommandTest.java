package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.sharedfate.TestBootstrap;
import com.sharedfate.ui.RunResetMessages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버 초기화 명령의 <b>트리 모양</b>.
 *
 * <p>명령을 실제로 <b>실행</b>하려면 서버와 {@code CommandSourceStack} 이 있어야 해서 단위
 * 시험으로는 닿지 않는다({@code ShareTeamAliasTest} 와 같은 사정이다). 대신 브리가디어가 보는
 * 모양을 확인한다 — 확인 가지가 사라지거나, 확인 문구가 광고하는 낱말과 실제 가지 이름이
 * 어긋나는 사고가 여기서 잡힌다. 어긋나면 사람이 문구대로 쳤는데 <b>아무 일도 일어나지 않고</b>,
 * 그 상태로 30초가 지나 요청이 조용히 만료된다.
 *
 * <p>여기서는 {@code ShareTeamCommand} 를 부르지 않고 가지만 따로 꽂아 본다. 등록 파일은 여러
 * 손이 모이는 자리라, 그쪽이 아직 안 합쳐졌다고 이 시험이 깨질 이유가 없다.
 */
class RunResetCommandTest {
	private CommandDispatcher<CommandSourceStack> dispatcher;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		dispatcher = new CommandDispatcher<>();
		dispatcher.register(Commands.literal("shareteam")
				.then(RunResetCommand.node())
				.then(RunResetCommand.confirmNode(RunResetMessages.CONFIRM_WORD_EN))
				.then(RunResetCommand.confirmNode(RunResetMessages.CONFIRM_WORD_KO)));
		RunResetCommand.registerTopLevel(dispatcher);
		RunResetCommand.reset();
	}

	private CommandNode<CommandSourceStack> shareteam() {
		return dispatcher.getRoot().getChild("shareteam");
	}

	/** {@code /shareteam reset} 은 <b>되묻기만</b> 한다. 뒤에 붙는 것은 없다. */
	@Test
	void 초기화_가지는_되묻기만_하고_뒤에_아무것도_안_받는다() {
		CommandNode<CommandSourceStack> reset = shareteam().getChild(RunResetCommand.LITERAL);

		assertNotNull(reset, "reset 가지가 있어야 한다");
		assertNotNull(reset.getCommand(), "치면 확인 문구가 나와야 한다");
		assertTrue(reset.getChildren().isEmpty(),
				"수락은 별도의 낱말로 받는다. reset confirm 같은 가지를 두지 않는다");
	}

	/** 가지 이름이 문구가 광고하는 명령과 같아야 한다. */
	@Test
	void 초기화_가지의_이름이_문구와_같다() {
		assertEquals("shareteam " + RunResetCommand.LITERAL, RunResetMessages.RESET_COMMAND);
	}

	/** {@code /shareteam yes} 와 {@code /shareteam 수락} 이 둘 다 실행된다. */
	@Test
	void 확인_낱말_둘_다_하위_명령으로_받는다() {
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			CommandNode<CommandSourceStack> node = shareteam().getChild(word);
			assertNotNull(node, word + " 가지가 있어야 한다");
			assertNotNull(node.getCommand(), word + " 가 실제로 실행되어야 한다");
			assertTrue(node.getChildren().isEmpty(), "확인 뒤에 더 붙는 것은 없다");
		}
	}

	/**
	 * {@code /yes} 와 {@code /수락} 은 <b>최상위</b>로도 받는다.
	 *
	 * <p>되돌릴 수 없는 것을 눈앞에 두고 {@code /shareteam} 부터 치게 하면 오타가 난다.
	 */
	@Test
	void 확인_낱말_둘_다_최상위로도_받는다() {
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild(word);
			assertNotNull(node, "/" + word + " 가 최상위에 있어야 한다");
			assertNotNull(node.getCommand(), "/" + word + " 가 실제로 실행되어야 한다");
		}
	}

	/**
	 * 확인 낱말 뒤에는 아무것도 붙지 않는다.
	 *
	 * <p>{@code /yes 어쩌고} 가 통하면 「수락했는데 왜 안 됐지」를 추적할 길이 없어진다.
	 */
	@Test
	void 최상위_확인_뒤에는_아무것도_안_받는다() {
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			assertTrue(dispatcher.getRoot().getChild(word).getChildren().isEmpty());
		}
	}
}
