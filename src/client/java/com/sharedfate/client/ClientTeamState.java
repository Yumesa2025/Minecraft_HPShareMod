package com.sharedfate.client;

import com.sharedfate.net.TeamSyncPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ClientTeamState {
	private static final Map<UUID, Integer> ALLY_SLOTS = new HashMap<>();
	private static final Map<UUID, String> MEMBER_NAMES = new HashMap<>();

	/** 팀이 공유하는 경험치 레벨. 서버가 보낸 값을 그대로 들고 있는다. */
	private static int teamLevel;
	/** 다음 증강이 나오는 레벨. 남은 증강이 없으면 0. */
	private static int nextPerkLevel;

	private ClientTeamState() {
	}

	public static void setAllySlot(UUID player, int slot) {
		if (!MEMBER_NAMES.containsKey(player)) {
			return;
		}
		if (slot < 0 || slot >= 9) {
			ALLY_SLOTS.remove(player);
		} else {
			ALLY_SLOTS.put(player, slot);
		}
	}

	public static void setTeam(TeamSyncPayload payload, UUID localPlayer) {
		MEMBER_NAMES.clear();
		ALLY_SLOTS.clear();
		teamLevel = payload.xpLevel();
		nextPerkLevel = payload.nextPerkLevel();
		for (TeamSyncPayload.Member member : payload.members()) {
			MEMBER_NAMES.put(member.id(), member.name());
			if (!member.id().equals(localPlayer)
					&& member.selectedSlot() >= 0 && member.selectedSlot() < 9) {
				ALLY_SLOTS.put(member.id(), member.selectedSlot());
			}
		}
	}

	public static void clear() {
		ALLY_SLOTS.clear();
		MEMBER_NAMES.clear();
		teamLevel = 0;
		nextPerkLevel = 0;
	}

	public static boolean isAllyUsingHotbarSlot(int slot) {
		return ALLY_SLOTS.containsValue(slot);
	}

	public static boolean inTeam() {
		return !MEMBER_NAMES.isEmpty();
	}

	/** 팀이 공유하는 경험치 레벨. */
	public static int teamLevel() {
		return teamLevel;
	}

	/** 다음 증강까지 남은 레벨. 더 이상 받을 증강이 없으면 -1. */
	public static int levelsToNextPerk() {
		return nextPerkLevel <= 0 ? -1 : Math.max(0, nextPerkLevel - teamLevel);
	}
}
