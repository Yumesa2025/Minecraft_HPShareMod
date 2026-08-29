package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.TemporaryPerkGrants;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 치명타에 성공하면 때린 팀원에게 잠깐 효과를 얹는다.
 *
 * <pre>{@code
 * { "type": "on_critical", "durationSeconds": 3,
 *   "effects": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
 *                  "operation": "add_multiplied_total", "amount": 0.1 } ] }
 * }</pre>
 *
 * <p>실버 10 급소만 노려가 이 타입을 쓴다.
 *
 * <h2>때린 사람에게만 얹는다</h2>
 * <p>치명타는 한 사람의 손끝에서 나온 일이라 팀 전원에게 퍼뜨리지 않는다. 처치 보상
 * ({@code on_kill})의 하위 효과가 처치한 사람에게만 붙는 것과 같은 규칙이다. 하위 효과가
 * 상태이상이면 {@code EffectSync} 가 알아서 팀에 퍼뜨리고, 속성이면 때린 사람만 세진다.
 *
 * <h2>어디가 치명타인가</h2>
 * <p>{@code ServerPlayer.crit(Entity)} 진입 시점이다. 26.2 의 {@code Player.attack} 은 피해가
 * 실제로 들어간 뒤에만 {@code attackVisualEffects} 를 부르고, 그 안에서 치명타 깃발이 섰을 때만
 * {@code crit} 을 부른다. 즉 "치명타로 실제 피해를 입혔다"와 정확히 같은 자리다. 마법 치명타
 * ({@code magicCrit}, 날카로움 마법부여의 추가 피해)는 점프 치명타가 아니므로 잡지 않는다.
 * 실제로 거는 지점은 {@link com.sharedfate.mixin.ServerPlayerCritMixin} 이고, 여기는 무엇을
 * 얹을지만 들고 있다.
 */
public final class OnCriticalEffect implements PerkEffect {
	private final TemporaryPerkGrants.Window window;

	public OnCriticalEffect(TemporaryPerkGrants.Window window) {
		this.window = window;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		TemporaryPerkGrants.Window window =
				TemporaryPerkGrants.fromJson(perkId, index, "on_critical", json);
		return window == null ? null : new OnCriticalEffect(window);
	}

	/** 치명타를 낸 팀원에게 잠깐 효과를 얹는다. */
	public void grantTo(ServerPlayer player) {
		TemporaryPerkGrants.grant(player, window);
	}

	@Override
	public void remove(ServerPlayer player) {
		TemporaryPerkGrants.revoke(player, window);
	}

	public int durationTicks() {
		return window.durationTicks();
	}

	public List<PerkEffect> effects() {
		return window.effects();
	}
}
