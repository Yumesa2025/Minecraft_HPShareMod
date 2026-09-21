package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
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
 * <h2>실제 배선을 그대로 쓴다</h2>
 * <p>예전에는 여기서 {@code shareteam} 가지를 <b>손으로 만들어</b> 꽂았다. 그러면 이 파일의
 * 시험이 전부 통과하면서도 {@link ShareTeamCommand#register} 가 초기화 가지를 <b>아예 안 달고
 * 있을 수</b> 있었다 — 시험은 자기가 만든 트리를 보고 있었기 때문이다. 명령이 통째로 등록되지
 * 않아도 초록이었다.
 *
 * <p>지금은 {@link ShareTeamCommand#register} 를 그대로 부른다. 서버가 없어도 되는 일이었다
 * ({@code ShareTeamAliasTest} 와 {@code PerkTestCommandTest} 가 이미 그렇게 한다).
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
		// 생산 경로 그대로 등록한다. 가지를 손으로 꽂으면 등록이 빠진 것을 못 본다.
		ShareTeamCommand.register(dispatcher, new SharedFateConfig());
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

	// ------------------------------------------------------------------ 남의 모드와 겹칠 때

	/**
	 * <b>{@code /yes} 는 흔한 낱말이라 남이 먼저 가져갈 수 있다.</b>
	 *
	 * <p>브리가디어는 같은 이름의 최상위 가지를 <b>합치면서 나중에 등록한 쪽의 명령으로
	 * 덮어쓴다</b>({@code CommandNode.addChild}). 모드 로딩 순서는 우리가 못 정하므로,
	 * 어느 날 {@code /yes} 가 남의 것이 되어 있을 수 있다. 빌드도 로그도 조용하다.
	 *
	 * <p>그래서 지키는 것은 최상위가 아니라 <b>물러설 자리</b>다 — {@code /shareteam yes} 와
	 * {@code /st 수락} 이 살아 있으면 초기화는 여전히 끝까지 갈 수 있다. 되묻기 문구도 그 긴
	 * 쪽을 함께 안내한다.
	 */
	@Test
	void 남이_최상위_확인_낱말을_가져가도_하위_명령은_살아_있다() {
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			// 다른 모드가 우리 뒤에 같은 이름을 등록한 상황.
			dispatcher.register(Commands.literal(word).executes(context -> 0));
		}

		for (String word : RunResetMessages.CONFIRM_WORDS) {
			CommandNode<CommandSourceStack> fallback = shareteam().getChild(word);
			assertNotNull(fallback, "shareteam " + word + " 가 남아 있어야 한다");
			assertNotNull(fallback.getCommand(),
					"최상위를 빼앗기면 이 길이 유일한 수락 경로다");
		}
	}

	/**
	 * 되묻기 문구가 <b>물러설 자리까지</b> 알려 준다.
	 *
	 * <p>위 시험이 지키는 길은 사람이 그 길을 알아야 쓸모가 있다. 문구가 {@code /yes} 하나만
	 * 광고하면, 그것이 남의 것이 된 날 사람은 수락할 방법을 못 찾는다.
	 */
	@Test
	void 되묻기_문구가_긴_쪽_명령도_함께_알려_준다() {
		String prompt = String.join("\n", RunResetMessages.confirmationLines(
				3, 2, RunResetMessages.TIMEOUT_SECONDS));
		for (String word : RunResetMessages.CONFIRM_WORDS) {
			assertTrue(prompt.contains("shareteam " + word) || prompt.contains("st " + word),
					"문구가 " + word + " 의 긴 쪽 경로를 안내하지 않는다");
		}
	}
}
