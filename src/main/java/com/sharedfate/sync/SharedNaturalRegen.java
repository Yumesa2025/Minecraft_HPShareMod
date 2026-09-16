package com.sharedfate.sync;

import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 허기에 의한 자연 회복이 팀 인원수만큼 곱해지는 것을 막는다.
 *
 * <p>{@link StatMirror} 는 팀원 <b>각자의</b> 체력 변화를 재서 공유 풀 하나에 합산한다. 서로
 * 다른 원인이면 그 합산이 맞다 — 한 명이 좀비에게, 다른 한 명이 스켈레톤에게 맞았다면 팀은
 * 진짜로 두 번 맞은 것이다. 틀린 것은 <b>한 원인이 공유 때문에 여러 번으로 보이는</b> 경우다.
 *
 * <p>자연 회복이 정확히 그 경우다. 허기와 포만감도 공유라 {@code StatMirror.writeBack} 이
 * 팀원 전원에게 같은 값을 써 준다. 그래서 {@code FoodData.tick} 의 회복 조건이 <b>같은 틱에
 * 전원에게 동시에</b> 성립하고, 네 명이 각자 {@code player.heal(...)} 을 불러 공유 체력이
 * 4인분씩 차오른다. 공유 풀은 한 사람 몫 크기라 몇 번 만에 만피가 된다.
 *
 * <h2>{@link SharedEffectDamage} 와 같은 방식, 다른 조건</h2>
 *
 * <p>공유 상태이상은 이미 「대표 한 명만 실제로 겪는다」로 풀려 있다. 여기서도 그 방식을
 * 그대로 쓰고 <b>대표도 같은 것을 고른다</b>({@link StatMirror#sharedEffectRepresentative}).
 * 대표가 갈리면 재생과 자연 회복이 동시에 도는 팀에서 셈이 어긋난다.
 *
 * <p>다른 점은 <b>상태이상 틱 구간을 보지 않는다</b>는 것뿐이다. 자연 회복은
 * {@code FoodData.tick} 안에서 나므로 그 구간 밖이고, 그래서 {@link SharedEffectDamage} 의
 * 판정에 걸리지 않아 지금까지 새고 있었다.
 *
 * <h2>왜 {@code heal} 이 아니라 {@code isHurt} 자리에서 막는가</h2>
 *
 * <p>{@code FoodData.tick} 의 회복 갈래는 회복과 함께 소모도({@code addExhaustion})를 쌓고
 * 타이머를 돌린다. {@code heal} 만 막으면 회복은 없는데 배만 고파진다. 진입 조건인
 * {@code isHurt} 를 거짓으로 만들면 갈래에 아예 들어가지 않아 <b>회복도 허기 소모도 함께
 * 1인분</b>이 된다. {@code FoodDataNaturalRegenMixin} 이 그 자리다.
 *
 * <h2>안전장치</h2>
 *
 * <p>대표를 못 고르면(전원 오프라인·사망) 아무도 막지 않는다. 막을 기준이 없는데 막으면 팀이
 * 자연 회복을 통째로 잃는다. 팀에 속하지 않은 플레이어도 언제나 통과라 바닐라와 완전히 같다.
 */
public final class SharedNaturalRegen {

	private SharedNaturalRegen() {
	}

	/**
	 * 지금 이 사람에게 일어나려는 자연 회복이 「대표가 이미 대신 겪을 몫」인가.
	 *
	 * <p>{@code FoodData.tick} 의 회복 조건을 볼 때마다 불린다. 팀에 속하지 않으면 팀 조회
	 * 한 번에 끝난다.
	 */
	public static boolean isDuplicate(@Nullable ServerPlayer player) {
		if (player == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
		if (team == null) {
			return false;
		}
		ServerPlayer representative = StatMirror.sharedEffectRepresentative(server, team);
		// 대표를 못 고르면 모두를 대표로 본다 — 그러면 아무도 막히지 않는다.
		boolean subjectIsRepresentative =
				representative == null || representative.getUUID().equals(player.getUUID());
		return isDuplicateNaturalRegen(true, subjectIsRepresentative);
	}

	/**
	 * 판정의 알맹이.
	 *
	 * @param subjectInTeam           대상이 공유 팀에 속해 있는가
	 * @param subjectIsRepresentative 대상이 이 팀의 대표인가
	 * @return 이 회복을 버려야 하면 {@code true}
	 */
	static boolean isDuplicateNaturalRegen(boolean subjectInTeam, boolean subjectIsRepresentative) {
		return subjectInTeam && !subjectIsRepresentative;
	}
}
