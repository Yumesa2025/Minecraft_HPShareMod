package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.team.TeamState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 팀 공유 인벤토리에 <b>몇 칸이 열려 있는가</b>.
 *
 * <p>여기서 지키는 약속은 두 가지다 — 언제나 온전한 줄일 것, 그리고 물건이 든 칸은 절대 닫히지
 * 않을 것. 앞의 것이 없으면 반쪽 줄이 생겨 칸 배경과 칸 자리가 어긋나고, 아래 칸의 물건을
 * 꺼내는 것만으로 창이 한 줄 접혔다 펴진다.
 */
class PerkInventorySlotsTest {
	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void unloadPool() {
		PerkRegistry.clear();
	}

	@Test
	void 아무_증강도_없으면_기본_두_줄이다() {
		assertEquals(18, PerkInventorySlots.unlockedFor(TeamState.fresh(20.0F)));
		assertEquals(18, PerkInventorySlots.unlockedFor(null));
	}

	@Test
	void 짐꾼은_한_줄을_통째로_연다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);

		assertEquals(27, PerkInventorySlots.unlockedFor(teamWith("sharedfate:porter")),
				"18 + 9 = 27 이면 9×7 이 전부 열린다");
	}

	/**
	 * 정의가 줄에 안 떨어지는 값을 적어도 밖으로 나가는 값은 온전한 줄이다.
	 *
	 * <p>이것이 없으면 24칸일 때 셋째 줄에 여섯 칸만 놓이고 오른쪽 세 칸은 배경만 남는다.
	 */
	@Test
	void 줄에_안_떨어지는_값도_줄_단위로_올라간다(@TempDir Path directory) throws IOException {
		loadPool(directory, 6);

		assertEquals(27, PerkInventorySlots.unlockedFor(teamWith("sharedfate:porter")),
				"18 + 6 = 24 는 세 줄로 올린다");
	}

	@Test
	void 열린_칸은_언제나_아홉의_배수다(@TempDir Path directory) throws IOException {
		for (int amount = 1; amount <= ExpandedInventoryManager.EXTRA_SIZE; amount++) {
			Path each = directory.resolve("pool" + amount);
			Files.createDirectories(each);
			loadPool(each, amount);
			int unlocked = PerkInventorySlots.unlockedFor(teamWith("sharedfate:porter"));
			assertEquals(0, unlocked % ExpandedInventoryManager.EXTRA_COLUMNS,
					amount + "칸을 주는 정의에서 " + unlocked + " 이 나왔다");
			assertTrue(unlocked <= ExpandedInventoryManager.EXTRA_SIZE);
			PerkRegistry.clear();
		}
	}

	/**
	 * 증강이 없어도 물건이 든 칸은 열려 있다. 닫으면 그 물건이 갈 곳을 잃는다.
	 *
	 * <p>그리고 <b>그 칸이 속한 줄 전체</b>가 열린다. 스무 번째 칸 하나 때문에 21칸만 열면
	 * 셋째 줄이 또 반쪽이 된다.
	 */
	@Test
	void 물건이_든_칸은_그_줄까지_통째로_열린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.extraItems.set(20, new ItemStack(Items.DIAMOND));

		assertEquals(27, PerkInventorySlots.unlockedFor(state));
	}

	@Test
	void 물건을_꺼내면_다시_기본_두_줄로_돌아온다() {
		TeamState state = TeamState.fresh(20.0F);
		state.extraItems.set(20, new ItemStack(Items.DIAMOND));
		state.extraItems.set(20, ItemStack.EMPTY);

		assertEquals(18, PerkInventorySlots.unlockedFor(state));
	}

	/** 기본 두 줄 안쪽의 물건은 아무것도 바꾸지 않는다. */
	@Test
	void 기본_줄_안의_물건은_칸을_늘리지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.extraItems.set(5, new ItemStack(Items.DIAMOND));

		assertEquals(18, PerkInventorySlots.unlockedFor(state));
	}

	@Test
	void 줄_올림은_경계에서_넘어간다() {
		assertEquals(0, PerkInventorySlots.ceilToRow(0));
		assertEquals(9, PerkInventorySlots.ceilToRow(1));
		assertEquals(9, PerkInventorySlots.ceilToRow(9));
		assertEquals(18, PerkInventorySlots.ceilToRow(10));
		assertEquals(27, PerkInventorySlots.ceilToRow(19));
		assertEquals(27, PerkInventorySlots.ceilToRow(27));
	}

	/** 증강을 끈 팀에게는 짐꾼이 아무것도 열지 않는다. */
	@Test
	void 증강을_끄면_짐꾼도_멎는다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith("sharedfate:porter");
		state.perksEnabled = false;

		assertEquals(18, PerkInventorySlots.unlockedFor(state));
	}

	// ------------------------------------------------- 짐꾼을 잃으면 칸이 바로 닫힌다

	/**
	 * 짐꾼을 잃으면 <b>셋째 줄의 물건을 빼내고 그 자리에서 닫는다.</b>
	 *
	 * <p>예전에는 「물건이 있으면 열어 둔다」였다. 그래서 짐꾼이 사라져도 창이 안 줄어들고,
	 * 그 칸을 사람이 손으로 다 비워야 그제야 닫혔다. 「증강을 잃었는데 칸은 그대로」가 눈에
	 * 이상하게 보인다.
	 *
	 * <p>물건은 넘침 대기열로 간다 — <b>잃지 않는다.</b> 대기열은 칸이 비는 대로 매 틱 다시
	 * 밀어 넣는다.
	 */
	@Test
	void 짐꾼을_잃으면_셋째_줄_물건을_빼내고_닫는다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith("sharedfate:porter");
		state.extraItems.set(20, new ItemStack(Items.DIAMOND, 5));
		state.ownedPerks.clear();

		assertTrue(PerkInventorySlots.evacuateLockedSlots(state), "빼낸 것이 있어야 한다");

		assertEquals(18, PerkInventorySlots.unlockedFor(state), "이제 두 줄이어야 한다");
		assertTrue(state.extraItems.get(20).isEmpty(), "잠긴 칸이 비워져야 한다");
	}

	/** 빼낸 물건은 자리가 있으면 곧바로 돌아온다. 사람 눈에는 「위로 올라왔다」로 보인다. */
	@Test
	void 빼낸_물건은_빈_칸으로_돌아온다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith("sharedfate:porter");
		state.extraItems.set(20, new ItemStack(Items.DIAMOND, 5));
		state.ownedPerks.clear();

		PerkInventorySlots.evacuateLockedSlots(state);

		assertTrue(state.overflowItems.isEmpty(), "자리가 있으면 대기열에 남지 않는다");
		assertEquals(5, state.mainItems.get(0).getCount(), "메인 첫 칸으로 돌아와야 한다");
	}

	/** 짐꾼을 그대로 갖고 있으면 아무것도 건드리지 않는다. */
	@Test
	void 짐꾼이_있는_동안에는_빼내지_않는다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith("sharedfate:porter");
		state.extraItems.set(20, new ItemStack(Items.DIAMOND, 5));

		assertFalse(PerkInventorySlots.evacuateLockedSlots(state));
		assertEquals(5, state.extraItems.get(20).getCount());
		assertEquals(27, PerkInventorySlots.unlockedFor(state));
	}

	/** 열린 두 줄 안쪽은 언제나 그대로다. */
	@Test
	void 기본_두_줄_안의_물건은_건드리지_않는다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith();
		state.extraItems.set(5, new ItemStack(Items.DIAMOND, 5));

		assertFalse(PerkInventorySlots.evacuateLockedSlots(state));
		assertEquals(5, state.extraItems.get(5).getCount());
	}

	/**
	 * 증강을 끈 팀은 건드리지 않는다.
	 *
	 * <p>{@code PerkTestCommand.reapply} 와 {@code PerkManager.setPerksEnabled} 는 <b>껐다
	 * 켜는 것</b>으로 효과를 다시 맞춘다. 그 잠깐 꺼진 순간에 빼내 버리면 짐꾼을 그대로 가진
	 * 팀의 물건이 이유 없이 위로 튀어 오른다.
	 */
	@Test
	void 증강을_끈_팀은_건드리지_않는다(@TempDir Path directory) throws IOException {
		loadPool(directory, 9);
		TeamState state = teamWith("sharedfate:porter");
		state.extraItems.set(20, new ItemStack(Items.DIAMOND, 5));
		state.perksEnabled = false;

		assertFalse(PerkInventorySlots.evacuateLockedSlots(state));
		assertEquals(5, state.extraItems.get(20).getCount());
	}

	private static TeamState teamWith(String... perkIds) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String perkId : perkIds) {
			state.ownedPerks.add(perkId);
		}
		return state;
	}

	private static void loadPool(Path directory, int amount) throws IOException {
		Path file = directory.resolve("sharedfate-perks.json");
		Files.writeString(file, """
				{
				  "perks": [
				    { "id": "sharedfate:porter", "rarity": "silver", "name": "짐꾼",
				      "effects": [ { "type": "inventory_slots", "amount": %d } ] }
				  ]
				}
				""".formatted(amount), StandardCharsets.UTF_8);
		PerkRegistry.load(directory);
	}
}
