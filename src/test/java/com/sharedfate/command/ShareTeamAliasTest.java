package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /st} 별칭.
 *
 * <p>명령을 <b>실행</b>하려면 서버와 {@code CommandSourceStack} 이 있어야 해서 단위 시험으로는
 * 닿지 않는다. 대신 브리가디어가 실제로 보는 <b>트리 모양</b>을 확인한다. 별칭이 깨지는 길은
 * 셋뿐이고 셋 다 여기서 잡힌다 — 노드가 아예 없거나, {@code redirect} 가 엉뚱한 곳을
 * 가리키거나, 인자 없는 실행({@code command})을 안 물려받는 경우다.
 */
class ShareTeamAliasTest {
	private CommandDispatcher<CommandSourceStack> dispatcher;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		dispatcher = new CommandDispatcher<>();
		ShareTeamCommand.register(dispatcher, new SharedFateConfig());
	}

	private CommandNode<CommandSourceStack> node(String name) {
		return dispatcher.getRoot().getChild(name);
	}

	@Test
	void 별칭_이름은_st_다() {
		assertEquals("st", ShareTeamCommand.ALIAS);
	}

	@Test
	void 두_이름이_모두_등록된다() {
		assertNotNull(node("shareteam"));
		assertNotNull(node("st"));
	}

	@Test
	void 별칭은_원본_트리로_이어진다() {
		assertSame(node("shareteam"), node("st").getRedirect(),
				"redirect 가 원본을 가리켜야 하위 명령이 그대로 통한다");
	}

	@Test
	void 인자_없는_st_도_원본과_똑같이_동작한다() {
		// redirect 는 "뒤에 뭔가 더 붙었을 때"만 쓰인다. 인자 없이 /st 만 친 경우는 이 노드
		// 자신의 command 가 처리하므로, 물려받지 않으면 아무 일도 일어나지 않는다.
		assertNotNull(node("shareteam").getCommand(), "원본이 인자 없이도 동작해야 한다");
		assertSame(node("shareteam").getCommand(), node("st").getCommand(),
				"/st 만 쳐도 /shareteam 과 같은 일을 해야 한다");
	}

	@Test
	void 별칭에는_자식을_달지_않는다() {
		// 브리가디어는 redirect 가 걸린 노드에 자식을 더하는 것을 금지한다. 하위 명령이
		// 늘어도 별칭을 손댈 일이 없어야 한다.
		assertTrue(node("st").getChildren().isEmpty());
	}

	@Test
	void 하위_명령은_원본에만_달려_있다() {
		CommandNode<CommandSourceStack> root = node("shareteam");
		for (String child : new String[] {"create", "invite", "leave", "disband",
				"status", "list", "help", "perk", "perktest", "swap", "health", "difficulty"}) {
			assertNotNull(root.getChild(child), child + " 가지가 있어야 한다");
		}
	}
}
