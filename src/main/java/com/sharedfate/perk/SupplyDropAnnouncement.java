package com.sharedfate.perk;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 보급이 왔을 때 팀에게 띄우는 채팅 문구를 만든다.
 *
 * <h2>아이템 이름은 {@link Component} 로 이어 붙인다</h2>
 * <p><b>{@code getHoverName().getString()} 을 쓰면 안 된다.</b> 서버에서 그 호출은 서버가 들고
 * 있는 언어({@code en_us})로 번역해 「Coal」을 내놓는다. 번역 키를 살린 {@link Component} 그대로
 * 이어 붙이면 <b>번역은 받는 사람의 클라이언트가</b> 하므로 한국어 설정에서는 「석탄」으로 뜬다.
 *
 * <h2>⚠ 반드시 <b>넣기 전에</b> 만든다</h2>
 * <p>{@code TeamState.restoreOverflow} 는 넘겨받은 묶음을 <b>제자리에서 깎는다.</b> 공유
 * 인벤토리에 다 들어간 묶음은 개수가 0 이 되고, 개수가 0 인 {@link ItemStack} 은
 * {@code isEmpty()} 라서 {@code getItem()} 이 공기를 돌려준다. 즉 넣은 <b>뒤에</b> 문구를 만들면
 * <b>「공기 0개」가 적힌다.</b> 그래서 {@link #itemsOf} 로 이름과 개수를 먼저 떠 두고, 그
 * 스냅숏으로 문구를 만든다.
 *
 * <h2>같은 아이템은 하나로 합친다</h2>
 * <p>3단계는 표에서 두 번 뽑고 <b>같은 항목이 두 번 나올 수 있다</b>(복원 추출). 그대로 적으면
 * 「석탄 14개, 석탄 13개」가 되는데, 공유 인벤토리에서는 어차피 한 칸에 합쳐지므로 보이는 것과
 * 적힌 것이 어긋난다. 그래서 <b>같은 아이템·같은 컴포넌트</b>끼리 개수를 더해 한 번만 적는다.
 * 지속시간이 다른 물약처럼 컴포넌트가 다르면 따로 적힌다.
 */
public final class SupplyDropAnnouncement {
	/** 이 모드의 증강 알림에 공통으로 붙는 머리. */
	public static final String PREFIX = "[증강] ";
	/** 아이템 사이를 가르는 글자. */
	public static final String SEPARATOR = ", ";
	/** 마지막 아이템 뒤에 붙는 꼬리. 앞이 언제나 「개」라 조사는 늘 「를」이다. */
	public static final String TAIL = "를 보급받았습니다.";

	private SupplyDropAnnouncement() {
	}

	/**
	 * 문구에 적을 아이템 하나.
	 *
	 * @param name  아이템 이름. <b>번역 키를 살린 그대로</b>여야 받는 사람의 언어로 뜬다
	 * @param count 개수. 같은 아이템이 여러 번 뽑혔으면 이미 더해진 값이다
	 */
	public record Item(Component name, int count) {
	}

	/**
	 * 뽑은 묶음들에서 이름과 개수를 떠 둔다. 같은 아이템은 합친다.
	 *
	 * <p><b>공유 인벤토리에 넣기 전에</b> 불러야 한다.
	 */
	public static List<Item> itemsOf(@Nullable List<ItemStack> stacks) {
		if (stacks == null || stacks.isEmpty()) {
			return List.of();
		}
		// 같은 아이템을 찾으려면 원본 묶음을 들고 있어야 한다. 한 회에 오는 것이 많아야 여덟
		// 묶음이라(rolls 의 상한) 목록을 훑는 것으로 충분하다.
		List<ItemStack> kinds = new ArrayList<>(stacks.size());
		List<Integer> counts = new ArrayList<>(stacks.size());
		for (ItemStack stack : stacks) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			int found = -1;
			for (int index = 0; index < kinds.size(); index++) {
				if (ItemStack.isSameItemSameComponents(kinds.get(index), stack)) {
					found = index;
					break;
				}
			}
			if (found >= 0) {
				counts.set(found, counts.get(found) + stack.getCount());
			} else {
				kinds.add(stack);
				counts.add(stack.getCount());
			}
		}

		List<Item> items = new ArrayList<>(kinds.size());
		for (int index = 0; index < kinds.size(); index++) {
			items.add(new Item(kinds.get(index).getHoverName(), counts.get(index)));
		}
		return List.copyOf(items);
	}

	/**
	 * 「{@code [증강] 석탄 14개, 다이아몬드 2개를 보급받았습니다.}」
	 *
	 * <p>줄 것이 하나도 없으면 {@link #nothing()} 과 같은 문구다. 부르는 쪽에서 빈손을 먼저
	 * 걸러 내지만, 여기서도 같은 답이 나와야 「아무 말도 없는」 경우가 생기지 않는다.
	 */
	public static Component received(@Nullable List<Item> items) {
		if (items == null || items.isEmpty()) {
			return nothing();
		}
		MutableComponent message = Component.literal(PREFIX);
		for (int index = 0; index < items.size(); index++) {
			if (index > 0) {
				message.append(Component.literal(SEPARATOR));
			}
			Item item = items.get(index);
			message.append(item.name()).append(Component.literal(countText(item.count())));
		}
		return message.append(Component.literal(TAIL));
	}

	/** 묶음들을 그대로 받아 문구까지 한 번에. {@link #itemsOf} 를 거쳐 간다. */
	public static Component receivedFrom(@Nullable List<ItemStack> stacks) {
		return received(itemsOf(stacks));
	}

	/**
	 * 꽝일 때의 문구.
	 *
	 * <p><b>꽝도 반드시 알린다.</b> 2단계는 열 번 중 세 번이 빈손인데, 그때 아무 말도 없으면
	 * 「보급이 고장 났나」와 구별할 수가 없다.
	 */
	public static Component nothing() {
		return Component.literal(PREFIX + "이번 보급은 빈손입니다.");
	}

	/**
	 * 자리가 없어 대기열로 간 묶음이 있을 때 덧붙이는 문구.
	 *
	 * <p>{@code PerkItemGrants} 가 쓰는 문구와 같다.
	 */
	public static Component overflow(int leftover) {
		return Component.literal(PREFIX + "공유 인벤토리에 자리가 없어 " + Math.max(0, leftover)
				+ "묶음이 대기열로 갔습니다. 칸을 비우면 자동으로 들어옵니다.");
	}

	/** 「 14개」. 이름 뒤에 그대로 붙는다. */
	public static String countText(int count) {
		return " " + Math.max(0, count) + "개";
	}
}
