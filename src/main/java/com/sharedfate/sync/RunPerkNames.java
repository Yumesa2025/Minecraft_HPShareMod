package com.sharedfate.sync;

import com.sharedfate.perk.Perk;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.team.TeamState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 팀이 지금 가진 증강의 <b>사람이 읽는 이름</b>들.
 *
 * <p>회차가 끝나는 두 자리({@link DeathHandler}·{@link RunProgressManager})가 같은 목록을
 * {@link DamageLedger#noteRunEnd} 에 넘긴다. 두 곳이 각자 훑으면 한쪽만 고쳐진다.
 *
 * <h2>왜 id 가 아니라 이름인가</h2>
 * <p>기록은 <b>읽으라고</b> 남긴다. {@code sharedfate:porter} 는 나중에 책을 펴는 사람에게
 * 아무 말도 하지 않는다. 게다가 증강 풀은 정의 파일이라 언제든 바뀌는데, id 만 적어 두면
 * 사라진 증강의 이름을 되찾을 방법이 없다. 이름을 그때그때 박아 두면 기록이 스스로 완결된다.
 */
public final class RunPerkNames {

	private RunPerkNames() {
	}

	/**
	 * 이 팀이 가진 증강의 이름들. 팀이 없거나 증강을 안 쓰면 빈 목록.
	 *
	 * <p>풀에서 사라진 id 는 id 그대로 남긴다. 버리면 개수가 어긋나 「고른 증강 몇 개」가
	 * 거짓이 된다.
	 */
	public static List<String> of(@Nullable TeamState state) {
		if (state == null || state.ownedPerks.isEmpty()) {
			return List.of();
		}
		List<String> names = new ArrayList<>(state.ownedPerks.size());
		for (String perkId : state.ownedPerks) {
			names.add(PerkRegistry.byId(perkId).map(Perk::name).orElse(perkId));
		}
		return List.copyOf(names);
	}
}
