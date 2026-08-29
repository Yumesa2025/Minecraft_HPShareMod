package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 팀원의 {@code max_health} 속성을 팀 공유 상한과 똑같이 맞춘다.
 *
 * <p>공유 체력 풀의 상한은 {@code TeamState.maxHealth} 하나뿐이고 {@code StatMirror} 가 그
 * 값으로 팀 전원의 체력을 자른 뒤 써 준다. 그래서 화면에 보이는 체력 칸도 정확히 그 값이어야
 * 하고, {@link #apply} 는 지금 값이 얼마든 <b>목표값이 되도록</b> 배율 수정자를 건다.
 *
 * <h2>여기에 최대 체력을 올리는 증강을 걸면 안 된다</h2>
 * <p>이 덮어쓰기는 접속·부활·명령·주기 점검마다 다시 돈다. 그래서 {@code attribute} 효과로
 * {@code max_health} 에 +6 을 걸어 두면, 이 계산이 그 +6 을 정확히 상쇄해 증강이 아무 일도
 * 하지 않은 것처럼 보인다. 실제로 그런 버그가 있었다.
 *
 * <p>고친 방법은 "속성을 더 올리는 것"이 아니라 <b>목표값 자체를 올리는 것</b>이다.
 * {@code max_health_bonus} 증강의 보너스는 {@code PerkHealthRules} 가 팀의 기본값에 더해
 * {@code TeamState.maxHealth} 로 만들고, 여기서는 그 값을 그대로 목표로 삼는다. 속성과 공유
 * 상한이 언제나 같은 숫자를 가리키므로 어긋날 자리가 없다.
 *
 * <p>{@code AttributeEffect} 는 {@code minecraft:max_health} 정의를 아예
 * {@code MaxHealthBonusEffect} 로 옮겨 읽어, 예전 형식으로 적힌 설정 파일도 이 규칙을 지키게 한다.
 */
public final class MaxHealthAttribute {
	private static final Identifier MODIFIER_ID = SharedFateMod.id("shared_max_health");

	private MaxHealthAttribute() {
	}

	/**
	 * 이 사람의 최대 체력을 {@code targetMaxHealth} 로 만든다.
	 *
	 * <p>우리 수정자를 먼저 뗀 뒤 남은 값을 읽어, 거기에 걸면 목표가 되는 배율을 새로 건다.
	 * 다른 모드가 걸어 둔 수정자는 비율대로 살아남는다.
	 */
	public static void apply(ServerPlayer player, double targetMaxHealth) {
		AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
		if (instance == null) {
			return;
		}
		double safeTarget = Double.isFinite(targetMaxHealth)
				? Math.max(1.0, Math.min(1024.0, targetMaxHealth)) : 20.0;
		instance.removeModifier(MODIFIER_ID);
		double currentMaxHealth = instance.getValue();
		if (currentMaxHealth > 0.0 && currentMaxHealth != safeTarget) {
			instance.addTransientModifier(
					new AttributeModifier(MODIFIER_ID,
							safeTarget / currentMaxHealth - 1.0,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	public static void remove(ServerPlayer player) {
		AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
		if (instance != null) {
			instance.removeModifier(MODIFIER_ID);
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
	}

	public static void refresh(ServerPlayer player, double targetMaxHealth) {
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			remove(player);
		} else {
			apply(player, state.maxHealth);
		}
	}
}
