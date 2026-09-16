package com.sharedfate.storage;

import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 인벤토리가 꽉 차서 못 받은 물건이 쌓이는 <b>팀 창고</b>.
 *
 * <h2>왜 만들었는가</h2>
 *
 * <p>예전에는 넘친 물건이 {@code TeamState.overflowItems} 라는 <b>보이지 않는 대기열</b>에
 * 들어가 매 틱 자동으로 인벤토리로 되돌아갔다. 아이템을 잃지는 않았지만 <b>언제 돌아오는지
 * 아무도 몰랐다.</b> 「보급을 받았다는 채팅은 떴는데 인벤토리 어디에도 없다」가 그것이었고,
 * 대기열에 있는 건지 사라진 건지 구분할 수단조차 없었다.
 *
 * <p>이제 쌓이기만 하고 <b>사람이 열어서 꺼내 간다.</b> 자동 복귀는 껐다.
 *
 * <h2>넣을 수는 없다</h2>
 *
 * <p>{@code SlotTeamStorageMixin} 이 이 컨테이너의 칸에 {@code mayPlace} 를 거짓으로 만든다.
 * 마우스로도 쉬프트 클릭으로도 넣을 수 없다. 창고는 <b>받는 곳이지 보관하는 곳이 아니다</b> —
 * 넣게 두면 「인벤토리가 두 개」가 되어 공유 인벤토리의 뜻이 흐려진다.
 *
 * <h2>꺼낼 자리가 없으면 그냥 안 꺼내진다</h2>
 *
 * <p>바닐라 상자와 같다. 다만 커서에 든 채 창을 닫으면 갈 곳이 없으므로,
 * {@code MenuStorageCloseMixin} 이 그것을 <b>창고로 되돌린다.</b> 바닥에 버리지 않는다.
 *
 * <h2>화면은 여섯 줄, 저장은 무제한</h2>
 *
 * <p>한 번에 {@value #VISIBLE} 칸까지 보여 준다. 그보다 많으면 꺼낸 만큼 다음에 열 때
 * 올라온다. 저장 쪽에는 상한이 없다.
 */
public final class TeamStorage {

	/** 한 번에 보여 주는 칸 수. 바닐라 큰 상자와 같은 여섯 줄이다. */
	public static final int VISIBLE = 54;

	private TeamStorage() {
	}

	/** 창고 화면의 뒷단. 이 타입이 곧 「넣을 수 없는 칸」이라는 표지다. */
	public static final class Container extends SimpleContainer {
		private final TeamState state;

		private Container(TeamState state) {
			super(VISIBLE);
			this.state = state;
		}

		/** 이 창고의 주인 팀. */
		public TeamState state() {
			return state;
		}
	}

	/**
	 * 창고를 연다. 팀이 없으면 알리고 아무것도 하지 않는다.
	 *
	 * <p>열 때 대기열 앞쪽 {@value #VISIBLE} 개를 <b>꺼내 와서</b> 화면에 담는다. 양쪽에 같은
	 * 물건이 동시에 있는 순간을 만들지 않기 위해서다 — 그래야 어느 쪽을 믿을지 헷갈리지 않는다.
	 * 창을 닫을 때 남은 것이 대기열 <b>앞으로</b> 돌아간다.
	 */
	public static boolean open(@Nullable ServerPlayer player) {
		if (player == null) {
			return false;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			player.sendSystemMessage(Component.literal("팀이 없습니다. 창고는 팀마다 하나입니다."));
			return false;
		}
		Container container = takeForScreen(state);
		int waiting = state.overflowItems.size();
		String title = waiting > 0 ? "창고 (뒤에 " + waiting + "개 더)" : "창고";
		player.openMenu(new SimpleMenuProvider(
				(id, inventory, owner) -> ChestMenu.sixRows(id, inventory, container),
				Component.literal(title)));
		return true;
	}

	/**
	 * 대기열 앞쪽 {@value #VISIBLE} 개를 화면 몫으로 <b>꺼내 온다.</b>
	 *
	 * <p>양쪽에 같은 물건이 동시에 있는 순간을 만들지 않는다 — 그래야 어느 쪽을 믿을지
	 * 헷갈리지 않는다. 화면을 여는 부분과 갈라 둔 것은 살아 있는 서버 없이 시험하기 위해서다.
	 */
	static Container takeForScreen(TeamState state) {
		Container container = new Container(state);
		int taken = Math.min(VISIBLE, state.overflowItems.size());
		for (int index = 0; index < taken; index++) {
			container.setItem(index, state.overflowItems.get(index));
		}
		state.overflowItems.subList(0, taken).clear();
		resync(state);
		return container;
	}

	/**
	 * 창을 닫을 때 남은 것을 대기열 <b>앞으로</b> 되돌린다.
	 *
	 * <p>앞으로 넣는 이유는 순서를 지키기 위해서다. 열려 있는 동안 새로 넘친 물건은 뒤에
	 * 붙었으므로, 원래 앞에 있던 것이 다시 앞이어야 다음에 열었을 때 같은 것이 보인다.
	 */
	public static void writeBack(Container container) {
		List<ItemStack> left = new ArrayList<>(VISIBLE);
		for (int index = 0; index < container.getContainerSize(); index++) {
			ItemStack stack = container.getItem(index);
			if (!stack.isEmpty()) {
				left.add(stack);
			}
		}
		container.clearContent();
		if (!left.isEmpty()) {
			container.state().overflowItems.addAll(0, left);
		}
		// 되돌린 것을 「새로 들어온 것」으로 세면 창을 닫을 때마다 알림이 뜬다.
		resync(container.state());
	}

	/**
	 * 커서에 들려 있던 것을 창고로 되돌린다. 바닥에 버리지 않는다.
	 *
	 * <p>바닐라는 창을 닫을 때 커서의 물건을 인벤토리에 넣어 보고, 안 되면 <b>바닥에
	 * 버린다.</b> 창고에서 꺼내다 만 물건이 발밑에 떨어져 5분 뒤 사라지는 것은 사고다.
	 */
	public static void returnCarried(Container container, ItemStack carried) {
		if (!carried.isEmpty()) {
			container.state().overflowItems.addFirst(carried.copy());
		}
	}

	/** 지금 창고에 쌓여 있는 묶음 수. 화면에 올라와 있는 것은 빼고 센다. */
	public static int waiting(@Nullable TeamState state) {
		return state == null ? 0 : state.overflowItems.size();
	}

	// ------------------------------------------------------------------ 들어갈 때 알리기

	/**
	 * 팀마다 마지막에 본 창고 묶음 수.
	 *
	 * <p>{@link TeamState} 를 <b>객체 동일성</b>으로 센다. 팀 식별자를 따로 들고 다닐 필요가
	 * 없고, 팀 상태가 새로 만들어지면(회차 초기화) 자연히 새 칸이 된다.
	 */
	private static final Map<TeamState, Integer> LAST_SIZE = new IdentityHashMap<>();

	/**
	 * 창고에 <b>새로 들어온 것이 있으면</b> 팀 전원에게 알린다. 매 서버 틱 불린다.
	 *
	 * <h2>왜 여기 한 곳인가</h2>
	 *
	 * <p>물건이 창고로 가는 길은 열 곳이 넘는데(즉시 지급·보급·채굴 보너스·광물 교환·
	 * 해시계·소집의 조각·비행 부적이 밀어낸 것·왼손 고정·커서·「유산」·잠긴 칸 비우기)
	 * <b>모두 같은 모양</b>이다 — 대기열에 넣고 되돌려 보고, 안 들어간 것이 남는다.
	 *
	 * <p>그래서 부르는 곳마다 알림을 다는 대신 <b>대기열이 늘어난 것</b>을 여기서 본다. 한 틱
	 * 안에 넣었다 되돌린 몫은 서로 지워지므로, 남는 증가분이 정확히 「자리가 없어 창고로 간
	 * 것」이다. 새 경로가 생겨도 여기는 안 고쳐도 된다.
	 *
	 * <p>줄어드는 쪽(창고를 열어 꺼내 갔을 때)은 세기만 하고 알리지 않는다.
	 */
	public static void tick(@Nullable MinecraftServer server) {
		if (server == null) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		Set<TeamState> living = new HashSet<>();
		for (ShareTeam team : manager.allTeams()) {
			TeamState state = manager.stateByTeamId(team.teamId());
			if (state == null) {
				continue;
			}
			living.add(state);
			int now = state.overflowItems.size();
			Integer before = LAST_SIZE.put(state, now);
			if (before == null || now <= before) {
				continue;
			}
			announce(server, team, state.overflowItems.subList(before, now), now);
		}
		LAST_SIZE.keySet().retainAll(living);
	}

	/**
	 * 화면을 열고 닫을 때 기준을 다시 잡는다.
	 *
	 * <p>창고를 열면 묶음이 화면으로 빠져나가고 닫으면 <b>앞쪽으로</b> 돌아온다. 그 되돌림을
	 * 「새로 들어온 것」으로 잘못 세면 창을 닫을 때마다 알림이 뜬다.
	 */
	private static void resync(TeamState state) {
		LAST_SIZE.put(state, state.overflowItems.size());
	}

	private static void announce(MinecraftServer server, ShareTeam team,
			List<ItemStack> added, int total) {
		if (added.isEmpty()) {
			return;
		}
		ItemStack first = added.getFirst();
		StringBuilder what = new StringBuilder(first.getHoverName().getString())
				.append(' ').append(first.getCount()).append("개");
		if (added.size() > 1) {
			what.append(" 외 ").append(added.size() - 1).append("묶음");
		}
		Component message = Component.literal(
				"[창고] 인벤토리가 꽉 차 " + what + "을(를) 창고에 넣었습니다."
						+ " 창고에 " + total + "묶음 — /창고 로 꺼내세요.");
		for (UUID member : team.members()) {
			ServerPlayer online = server.getPlayerList().getPlayer(member);
			if (online != null) {
				online.sendSystemMessage(message);
			}
		}
	}

	/** 서버가 멈출 때 기준을 버린다. 남겨 두면 다음 월드의 첫 틱이 통째로 새 것으로 보인다. */
	public static void reset() {
		LAST_SIZE.clear();
	}
}
