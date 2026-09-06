package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 보급이 왔을 때 띄우는 채팅 문구.
 *
 * <p>지키는 것은 넷이다 — 받은 것을 <b>전부</b> 적을 것, 같은 아이템은 합칠 것, <b>빈손도
 * 알릴</b> 것, 그리고 아이템 이름을 <b>번역 키가 살아 있는 채로</b> 이어 붙일 것.
 *
 * <p>마지막 하나가 이 시험의 핵심이다. 서버에서 {@code getHoverName().getString()} 을 부르면
 * 서버가 들고 있는 언어로 번역돼 「Coal」이 박히고, 그 뒤로는 받는 사람이 한국어를 써도
 * 영어로 뜬다. 여기서는 <b>이어 붙인 조각이 그대로 살아 있는지</b>를 본다.
 *
 * <p>게임을 띄우지 않는다. 아이템 레지스트리와 컴포넌트만 있으면 되므로
 * {@link TestBootstrap#ensureInitialized()} 로 충분하다.
 */
class SupplyDropAnnouncementTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static SupplyDropAnnouncement.Item item(String name, int count) {
		return new SupplyDropAnnouncement.Item(Component.literal(name), count);
	}

	// ------------------------------------------------------------------ 문구

	@Test
	void 한_가지를_받으면_한_줄이다() {
		assertEquals("[증강] 석탄 14개를 보급받았습니다.",
				SupplyDropAnnouncement.received(List.of(item("석탄", 14))).getString());
	}

	/**
	 * 3단계는 두 번 뽑으므로 두 가지가 함께 온다. <b>전부</b> 적어야 한다.
	 *
	 * <p>조사는 마지막 하나만 붙는데, 앞이 언제나 「개」라 받침이 없어 늘 「를」이다.
	 */
	@Test
	void 여러_가지를_받으면_쉼표로_이어_한_줄이다() {
		assertEquals("[증강] 석탄 14개, 다이아몬드 2개를 보급받았습니다.",
				SupplyDropAnnouncement.received(
						List.of(item("석탄", 14), item("다이아몬드", 2))).getString());
	}

	/** 꽝도 반드시 알린다. 2단계는 열 번 중 세 번이 빈손이라 아무 말이 없으면 고장으로 읽힌다. */
	@Test
	void 빈손도_알린다() {
		assertEquals("[증강] 이번 보급은 빈손입니다.", SupplyDropAnnouncement.nothing().getString());
		assertEquals("[증강] 이번 보급은 빈손입니다.",
				SupplyDropAnnouncement.received(List.of()).getString());
		assertEquals("[증강] 이번 보급은 빈손입니다.",
				SupplyDropAnnouncement.received(null).getString());
	}

	@Test
	void 대기열로_간_묶음도_알린다() {
		assertTrue(SupplyDropAnnouncement.overflow(2).getString()
				.startsWith("[증강] 공유 인벤토리에 자리가 없어 2묶음이"));
	}

	// ------------------------------------------------------------------ 이름과 개수 뜨기

	@Test
	void 같은_아이템은_개수를_합쳐_한_번만_적는다() {
		List<ItemStack> drawn = List.of(
				new ItemStack(Items.COAL, 14),
				new ItemStack(Items.DIAMOND, 2),
				new ItemStack(Items.COAL, 13));

		List<SupplyDropAnnouncement.Item> items = SupplyDropAnnouncement.itemsOf(drawn);
		assertEquals(2, items.size());
		assertEquals(27, items.get(0).count());
		assertEquals(2, items.get(1).count());
	}

	/** 컴포넌트가 다르면 다른 물건이다. 지속시간이 다른 물약이 한 줄로 합쳐지면 안 된다. */
	@Test
	void 컴포넌트가_다르면_따로_적는다() {
		ItemStack plain = new ItemStack(Items.DIAMOND, 1);
		ItemStack named = new ItemStack(Items.DIAMOND, 1);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("표시된 다이아몬드"));

		assertEquals(2, SupplyDropAnnouncement.itemsOf(List.of(plain, named)).size());
	}

	@Test
	void 빈_묶음은_빠진다() {
		List<ItemStack> drawn = new ArrayList<>();
		drawn.add(ItemStack.EMPTY);
		drawn.add(new ItemStack(Items.COAL, 3));
		assertEquals(1, SupplyDropAnnouncement.itemsOf(drawn).size());
		assertTrue(SupplyDropAnnouncement.itemsOf(List.of()).isEmpty());
		assertTrue(SupplyDropAnnouncement.itemsOf(null).isEmpty());
	}

	/**
	 * 아이템 이름을 <b>번역 키가 살아 있는 조각으로</b> 이어 붙인다.
	 *
	 * <p>{@code getString()} 은 서버가 들고 있는 언어로 번역해 버리므로 그 결과를 문자열로
	 * 박아 넣으면 받는 사람의 언어 설정이 무시된다. 여기서는 이어 붙인 조각 자체가 원래의
	 * 이름 성분 그대로인지를 본다 — 그래야 한국어 클라이언트에서 「석탄」으로 뜬다.
	 */
	@Test
	void 아이템_이름은_번역되지_않은_채로_실린다() {
		ItemStack coal = new ItemStack(Items.COAL, 14);
		Component expected = coal.getHoverName();

		List<SupplyDropAnnouncement.Item> items = SupplyDropAnnouncement.itemsOf(List.of(coal));
		assertEquals(expected.getContents(), items.get(0).name().getContents());

		// 조각이 통째로 문구 안에 들어가 있어야 한다. 문자열로 굳혀 넣으면 이 확인이 깨진다.
		boolean carried = false;
		for (Component part : SupplyDropAnnouncement.received(items).getSiblings()) {
			if (expected.getContents().equals(part.getContents())) {
				carried = true;
				break;
			}
		}
		assertTrue(carried, "아이템 이름 성분이 문구에 그대로 실리지 않았습니다");
	}

	/** 묶음에서 곧바로 문구까지. 실제 지급 경로가 쓰는 길이다. */
	@Test
	void 묶음에서_바로_문구가_나온다() {
		String line = SupplyDropAnnouncement
				.receivedFrom(List.of(new ItemStack(Items.COAL, 14)))
				.getString();
		assertTrue(line.startsWith("[증강] "), line);
		assertTrue(line.endsWith(" 14개를 보급받았습니다."), line);
	}

	/**
	 * ⚠ 공유 인벤토리에 넣은 <b>뒤에</b> 문구를 만들면 안 된다.
	 *
	 * <p>{@code TeamState.restoreOverflow} 는 묶음을 제자리에서 깎는다. 다 들어간 묶음은 개수가
	 * 0 이 되고, 개수가 0 인 묶음은 {@code getItem()} 이 공기를 돌려주므로 「공기 0개」가 적힌다.
	 * 여기서는 깎이는 흉내만 내어 <b>먼저 뜬 스냅숏은 멀쩡한지</b>를 지킨다.
	 */
	@Test
	void 넣기_전에_뜬_스냅숏은_깎여도_그대로다() {
		ItemStack coal = new ItemStack(Items.COAL, 14);
		List<SupplyDropAnnouncement.Item> snapshot = SupplyDropAnnouncement.itemsOf(List.of(coal));

		// 공유 인벤토리에 다 들어간 뒤의 모습.
		coal.shrink(14);
		assertTrue(coal.isEmpty());

		assertEquals(14, snapshot.get(0).count());
		assertTrue(SupplyDropAnnouncement.received(snapshot).getString()
				.endsWith(" 14개를 보급받았습니다."));
		// 반대로 깎인 뒤에 뜨면 아무것도 남지 않는다.
		assertTrue(SupplyDropAnnouncement.itemsOf(List.of(coal)).isEmpty());
	}
}
