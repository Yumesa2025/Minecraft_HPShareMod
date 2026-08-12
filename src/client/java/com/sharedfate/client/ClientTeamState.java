package com.sharedfate.client;

import com.sharedfate.net.TeamSyncPayload;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientTeamState {
	private static final Map<UUID, Integer> ALLY_SLOTS = new HashMap<>();
	private static final Map<UUID, String> MEMBER_NAMES = new HashMap<>();

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

	public static void setTeam(List<TeamSyncPayload.Member> members, UUID localPlayer) {
		MEMBER_NAMES.clear();
		ALLY_SLOTS.clear();
		for (TeamSyncPayload.Member member : members) {
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
	}

	public static boolean isAllyUsingHotbarSlot(int slot) {
		return ALLY_SLOTS.containsValue(slot);
	}

	public static boolean inTeam() {
		return !MEMBER_NAMES.isEmpty();
	}
}
