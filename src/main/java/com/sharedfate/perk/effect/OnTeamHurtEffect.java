package com.sharedfate.perk.effect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.TemporaryPerkGrants;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 팀원 누군가 피해를 받으면 팀에게 잠깐 효과를 얹는다.
 *
 * <pre>{@code
 * { "type": "on_team_hurt", "durationSeconds": 2,
 *   "effects": [ { "type": "status_effect", "effect": "minecraft:resistance" },
 *                { "type": "attribute", "attribute": "minecraft:attack_damage",
 *                  "operation": "add_multiplied_total", "amount": -0.2 } ] }
 * }</pre>
 *
 * <p>실버 8 동병상련이 이 타입을 쓴다.
 *
 * <h2>맞은 본인도 포함한다</h2>
 * <p>{@code includeVictim} 을 {@code false} 로 적으면 맞은 본인은 빼지만, 기본값은 포함이다.
 * 이 모드는 체력을 공유하므로 한 명이 맞으면 팀 전체의 체력이 깎인다. 즉 "팀원이 맞았다"는
 * 사건은 맞은 본인에게도 똑같이 일어난 일이다. 본인을 빼면 혼자인 팀에서는 증강이 아예
 * 발동하지 않아 설명과 어긋나기도 한다.
 *
 * <h2>이 클래스가 하지 않는 일</h2>
 * <p>여기는 "무엇을 얼마 동안 얹을 것인가"만 들고 있는 자료 그릇이다. "누가 언제 맞았는가"를
 * 보고 실제로 얹는 일은 {@link com.sharedfate.perk.PerkTriggers} 가 맡는다.
 * {@code on_kill} 과 {@link com.sharedfate.perk.PerkKillRewards} 의 관계와 같은 구도다.
 *
 * <p>얹는 것은 상태이상과 속성 수정자뿐이라 공유 체력·허기 풀을 건드리지 않는다. 팀원 넷에게
 * 저항을 걸어도 늘어나는 것은 각자의 저항이지 공유 풀이 아니므로, 인원수만큼 배수로 들어가는
 * 문제가 생기지 않는다.
 */
public final class OnTeamHurtEffect implements PerkEffect {
	private final TemporaryPerkGrants.Window window;
	private final boolean includeVictim;

	public OnTeamHurtEffect(TemporaryPerkGrants.Window window, boolean includeVictim) {
		this.window = window;
		this.includeVictim = includeVictim;
	}

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static PerkEffect fromJson(String perkId, int index, JsonObject json) {
		TemporaryPerkGrants.Window window =
				TemporaryPerkGrants.fromJson(perkId, index, "on_team_hurt", json);
		if (window == null) {
			return null;
		}
		Boolean includeVictim = readIncludeVictim(perkId, json);
		if (includeVictim == null) {
			return null;
		}
		return new OnTeamHurtEffect(window, includeVictim);
	}

	/** 이 팀원에게 잠깐 효과를 얹는다. */
	public void grantTo(ServerPlayer player) {
		TemporaryPerkGrants.grant(player, window);
	}

	/**
	 * 상시로 붙는 것이 없으므로 {@link #apply} 는 재정의하지 않는다. 대신 증강을 잃을 때는
	 * 마침 걸려 있던 것을 걷어내야 한다.
	 */
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

	/** 맞은 본인에게도 얹는가. */
	public boolean includesVictim() {
		return includeVictim;
	}

	private static Boolean readIncludeVictim(String perkId, JsonObject json) {
		JsonElement element = json.get("includeVictim");
		if (element == null || element.isJsonNull()) {
			return Boolean.TRUE;
		}
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: on_team_hurt 의 includeVictim 이 참·거짓이 아닙니다 ({})", perkId, element);
			return null;
		}
		return element.getAsBoolean();
	}
}
