package com.sharedfate.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 「값이 그대로면 다시 안 보낸다」의 <b>비교 키에 무엇이 들어가야 하는가</b>.
 *
 * <h2>왜 이 시험이 있는가</h2>
 *
 * <p>클라이언트는 열린 추가 칸 수를 스스로 셀 수 없다. 팀의 보유 증강을 모르기 때문이다.
 * 그래서 서버가 {@link TeamSyncPayload#unlockedExtraSlots()} 로 밀어 주는 값 하나만 믿는데,
 * <b>그 값이 어긋났을 때 되돌릴 길이 주기 재동기화 하나뿐이다.</b>
 *
 * <p>그런데 그 재동기화의 비교 키가 「레벨」과 「다음 증강 레벨」 둘뿐이었다. 증강을 빼도
 * 레벨은 그대로이므로 <b>키가 안 바뀌어 다시 보내지 않았다.</b> 결과는 이랬다.
 *
 * <ul>
 *   <li>「짐꾼」을 빼도 셋째 줄이 화면에서 안 사라진다</li>
 *   <li>나갔다 들어와도 셋째 줄이 그대로 보인다</li>
 *   <li>그런데 <b>그 줄에는 물건이 안 들어간다</b> — 서버는 18칸으로 잠갔기 때문</li>
 * </ul>
 *
 * <p>키 하나에 칸 수를 넣으면 값이 달라지는 <b>모든</b> 길(증강 제거·환골탈태·가호 단계 변동·
 * 물건이 든 마지막 칸 변동)이 이 한 곳을 지나므로 반 초 안에 스스로 복구된다.
 */
class TeamSyncKeyTest {

	@Test
	void 칸_수만_달라져도_키가_달라진다() {
		assertNotEquals(TeamBroadcaster.packSyncKey(30, 35, 27),
				TeamBroadcaster.packSyncKey(30, 35, 18),
				"칸 수가 키에 없으면 짐꾼을 빼도 다시 보내지 않는다");
	}

	@Test
	void 레벨과_다음_증강_레벨도_여전히_키에_들어간다() {
		assertNotEquals(TeamBroadcaster.packSyncKey(30, 35, 18),
				TeamBroadcaster.packSyncKey(31, 35, 18), "레벨");
		assertNotEquals(TeamBroadcaster.packSyncKey(30, 35, 18),
				TeamBroadcaster.packSyncKey(30, 40, 18), "다음 증강 레벨");
	}

	@Test
	void 셋이_모두_같으면_키도_같다() {
		assertEquals(TeamBroadcaster.packSyncKey(30, 35, 18),
				TeamBroadcaster.packSyncKey(30, 35, 18));
	}

	/**
	 * 세 값이 서로의 자리를 침범하지 않는다.
	 *
	 * <p>비트를 겹쳐 담으면 「레벨이 1 오르고 칸이 9 줄었다」 같은 조합이 우연히 같은 키가 되어
	 * <b>그때만 복구가 안 된다.</b> 그런 버그는 재현이 거의 불가능하므로 여기서 막는다.
	 */
	@Test
	void 세_값이_서로를_덮지_않는다() {
		java.util.Set<Long> keys = new java.util.HashSet<>();
		int[] levels = {0, 1, 30, 100, 1000, Integer.MAX_VALUE};
		int[] nextPerks = {0, 5, 35, 40};
		int[] slots = {0, 9, 18, 27};
		int count = 0;
		for (int level : levels) {
			for (int nextPerk : nextPerks) {
				for (int slot : slots) {
					keys.add(TeamBroadcaster.packSyncKey(level, nextPerk, slot));
					count++;
				}
			}
		}
		assertEquals(count, keys.size(), "서로 다른 조합이 같은 키가 되면 그 조합에서만 복구가 안 된다");
	}
}
