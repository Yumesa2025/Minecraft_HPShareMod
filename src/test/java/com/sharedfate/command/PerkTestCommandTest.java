package com.sharedfate.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.sharedfate.SharedFateMod;
import com.sharedfate.TestBootstrap;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.team.TeamState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 운영자 전용 증강 시험 명령.
 *
 * <p>실제로 증강을 넣고 빼는 부분은 서버가 있어야 해서 여기서 닿지 않는다. 대신 <b>안전
 * 장치</b>를 못박아 둔다. 이 기능이 잘못되는 방식은 사실상 「몰래 켜져 있는 것」 하나뿐이라,
 * 기본값이 꺼짐인지와 잠금이 정말 둘 다 걸려 있는지가 가장 중요하다.
 */
class PerkTestCommandTest {
	private CommandDispatcher<CommandSourceStack> dispatcher;
	private SharedFateConfig previousConfig;

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void setUp() {
		previousConfig = SharedFateMod.config;
		dispatcher = new CommandDispatcher<>();
		ShareTeamCommand.register(dispatcher, new SharedFateConfig());
	}

	@AfterEach
	void restoreConfig() {
		SharedFateMod.config = previousConfig;
	}

	private void withFlag(boolean value) {
		SharedFateConfig config = new SharedFateConfig();
		config.perkTestCommands = value;
		SharedFateMod.config = config;
	}

	private CommandNode<CommandSourceStack> shareteam() {
		return dispatcher.getRoot().getChild("shareteam");
	}

	private CommandNode<CommandSourceStack> perkTest() {
		return shareteam().getChild("perktest");
	}

	private static CompoundTag encode(TeamState state) {
		return (CompoundTag) TeamState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
	}

	// ------------------------------------------------------------------ 잠금

	@Test
	void 설정_기본값은_꺼짐이다() {
		assertFalse(new SharedFateConfig().perkTestCommands,
				"실제로 노는 서버에 조용히 켜져 있으면 안 된다");
	}

	@Test
	void 설정을_못_읽었으면_꺼진_것으로_본다() {
		SharedFateMod.config = null;

		assertFalse(PerkTestCommand.enabled());
	}

	@Test
	void 설정_플래그가_그대로_반영된다() {
		withFlag(false);
		assertFalse(PerkTestCommand.enabled());

		withFlag(true);
		assertTrue(PerkTestCommand.enabled());
	}

	@Test
	void 두_번째_잠금인_권한_요구가_노드에_걸려_있다() {
		assertNotNull(perkTest(), "perktest 가지가 있어야 한다");

		// 브리가디어의 기본 요구조건은 source 를 아예 보지 않는 s -> true 다. 여기 걸린
		// 요구조건은 source 의 권한을 읽으므로 null 을 주면 터진다. 그 차이로 잠금이 실제로
		// 걸렸는지를 가른다. 비교군은 잠금이 없는 status 가지다.
		assertThrows(NullPointerException.class, () -> perkTest().getRequirement().test(null),
				"권한을 안 보는 요구조건이면 잠금이 하나뿐이다");
		assertTrue(shareteam().getChild("status").getRequirement().test(null),
				"보통 가지는 아무나 쓸 수 있어야 한다");
	}

	// ------------------------------------------------------------------ 트리 모양

	@Test
	void 하위_명령_넷이_모두_있다() {
		assertNotNull(perkTest().getChild("give"));
		assertNotNull(perkTest().getChild("remove"));
		assertNotNull(perkTest().getChild("clear"));
		assertNotNull(perkTest().getChild("list"));
	}

	@Test
	void give_와_remove_는_증강id_를_받는다() {
		assertNotNull(perkTest().getChild("give").getChild("perkId"));
		assertNotNull(perkTest().getChild("remove").getChild("perkId"));
	}

	@Test
	void 증강id_둘_다_자동_완성이_붙어_있다() {
		// 일흔 몇 개를 손으로 치게 하면 시험 수단으로 쓸모가 없다.
		for (String branch : new String[] {"give", "remove"}) {
			CommandNode<CommandSourceStack> argument =
					perkTest().getChild(branch).getChild("perkId");
			assertNotNull(
					((ArgumentCommandNode<CommandSourceStack, ?>) argument).getCustomSuggestions(),
					branch + " 의 증강 id 에 자동 완성이 있어야 한다");
		}
	}

	@Test
	void clear_는_confirm_이_있어야_지운다() {
		CommandNode<CommandSourceStack> clear = perkTest().getChild("clear");

		assertNotNull(clear.getChild("confirm"), "되돌릴 수 없으므로 한 번 더 물어야 한다");
		assertNotNull(clear.getCommand(), "confirm 없이 치면 확인을 요구하는 안내가 나와야 한다");
	}

	@Test
	void list_는_all_로도_부를_수_있다() {
		CommandNode<CommandSourceStack> list = perkTest().getChild("list");

		assertNotNull(list.getCommand());
		assertNotNull(list.getChild("all"));
	}

	// ------------------------------------------------------------------ 표식

	@Test
	void 새_팀은_시험_명령_표식이_없다() {
		assertFalse(TeamState.fresh(20.0F).perkTestUsed);
	}

	@Test
	void 표식이_붙은_팀은_상태에_그대로_드러난다() {
		withFlag(false);
		TeamState used = TeamState.fresh(20.0F);
		used.perkTestUsed = true;

		assertTrue(PerkTestCommand.statusLine(used).contains("정상 회차가 아닙니다"));
	}

	@Test
	void 켜져_있기만_해도_상태에_한_줄이_남는다() {
		withFlag(true);

		assertTrue(PerkTestCommand.statusLine(TeamState.fresh(20.0F)).contains("시험 명령"));
	}

	@Test
	void 꺼져_있고_쓴_적도_없으면_아무것도_안_붙인다() {
		withFlag(false);

		assertEquals("", PerkTestCommand.statusLine(TeamState.fresh(20.0F)));
		assertEquals("", PerkTestCommand.statusLine(null));
	}

	// ------------------------------------------------------------------ 저장

	@Test
	void 표식은_월드_저장을_왕복한다() {
		TeamState state = TeamState.fresh(20.0F);
		state.perkTestUsed = true;

		TeamState round = TeamState.CODEC.parse(NbtOps.INSTANCE, encode(state)).getOrThrow();

		assertTrue(round.perkTestUsed);
	}

	@Test
	void 쓴_적_없는_팀은_저장에_항목을_남기지_않는다() {
		assertFalse(encode(TeamState.fresh(20.0F)).contains("perkTestUsed"),
				"안 썼으면 저장 형태가 이 기능 도입 전과 같아야 한다");
	}
}
