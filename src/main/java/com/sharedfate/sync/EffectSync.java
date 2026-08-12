package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import com.sharedfate.team.TeamState;
import net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EffectSync {
	private static boolean propagating;
	private static final Map<UUID, Set<Holder<MobEffect>>> PENDING_UPDATES = new HashMap<>();

	private EffectSync() {
	}

	public static void register() {
		ServerMobEffectEvents.BEFORE_ADD.register((instance, entity, context) -> {
			if (!propagating && SharedFateMod.config.shareStatusEffects
					&& entity instanceof ServerPlayer player) {
				PENDING_UPDATES.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>())
						.add(instance.getEffect());
			}
		});
		ServerMobEffectEvents.AFTER_ADD.register((instance, entity, context) -> {
			if (entity instanceof ServerPlayer player) {
				onAdded(player, instance);
			}
		});
		ServerMobEffectEvents.AFTER_REMOVE.register((instance, entity, context) -> {
			if (entity instanceof ServerPlayer player) {
				onRemoved(player, instance);
			}
		});
	}

	private static void onAdded(ServerPlayer source, MobEffectInstance instance) {
		if (propagating || !SharedFateMod.config.shareStatusEffects) {
			return;
		}
		MinecraftServer server = source.level().getServer();
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(source.getUUID());
		TeamState state = manager.stateOf(source.getUUID());
		if (team == null || state == null) {
			return;
		}
		clearPending(source.getUUID(), instance.getEffect());

		state.effects.removeIf(effect -> effect.getEffect().equals(instance.getEffect()));
		state.effects.add(new MobEffectInstance(instance));
		propagating = true;
		try {
			forEachOtherOnline(server, team, source.getUUID(), player ->
					player.addEffect(new MobEffectInstance(instance)));
		} finally {
			propagating = false;
		}
	}

	private static void onRemoved(ServerPlayer source, MobEffectInstance instance) {
		if (propagating || !SharedFateMod.config.shareStatusEffects) {
			return;
		}
		MinecraftServer server = source.level().getServer();
		TeamManager manager = TeamManager.get(server);
		ShareTeam team = manager.teamOf(source.getUUID());
		TeamState state = manager.stateOf(source.getUUID());
		if (team == null || state == null) {
			return;
		}

		state.effects.removeIf(effect -> effect.getEffect().equals(instance.getEffect()));
		propagating = true;
		try {
			forEachOtherOnline(server, team, source.getUUID(), player ->
					player.removeEffect(instance.getEffect()));
		} finally {
			propagating = false;
		}
	}

	public static void refreshPlayer(ServerPlayer player) {
		if (!SharedFateMod.config.shareStatusEffects) {
			return;
		}
		TeamState state = TeamLookup.stateOf(player.getUUID());
		if (state == null) {
			return;
		}
		propagating = true;
		try {
			player.removeAllEffects();
			for (MobEffectInstance effect : state.effects) {
				player.addEffect(new MobEffectInstance(effect));
			}
		} finally {
			propagating = false;
		}
	}

	public static void clearDetachedPlayer(ServerPlayer player) {
		if (!SharedFateMod.config.shareStatusEffects) {
			return;
		}
		propagating = true;
		try {
			player.removeAllEffects();
		} finally {
			propagating = false;
		}
	}

	public static void clearPersistedDetachedPlayer(ServerPlayer player) {
		propagating = true;
		try {
			player.removeAllEffects();
		} finally {
			propagating = false;
		}
	}

	public static void tick(MinecraftServer server) {
		if (!SharedFateMod.config.shareStatusEffects) {
			return;
		}
		TeamManager manager = TeamManager.get(server);
		try {
			for (ShareTeam team : manager.allTeams()) {
				for (UUID member : team.members()) {
					Set<Holder<MobEffect>> updated = PENDING_UPDATES.get(member);
					ServerPlayer source = server.getPlayerList().getPlayer(member);
					if (updated == null || source == null) {
						continue;
					}
					for (Holder<MobEffect> effect : Set.copyOf(updated)) {
						MobEffectInstance current = source.getEffect(effect);
						if (current != null) {
							onAdded(source, current);
						}
					}
				}

				ServerPlayer representative = firstAliveOnline(server, team);
				TeamState state = manager.stateByTeamId(team.teamId());
				if (representative == null || state == null) {
					continue;
				}
				state.effects.clear();
				for (MobEffectInstance effect : representative.getActiveEffects()) {
					state.effects.add(new MobEffectInstance(effect));
				}
			}
		} finally {
			PENDING_UPDATES.clear();
		}
	}

	public static void clearTeamEffects(MinecraftServer server, ShareTeam team, TeamState state) {
		state.effects.clear();
		if (!SharedFateMod.config.shareStatusEffects) {
			return;
		}
		propagating = true;
		try {
			for (UUID member : team.members()) {
				ServerPlayer player = server.getPlayerList().getPlayer(member);
				if (player != null) {
					player.removeAllEffects();
				}
			}
		} finally {
			propagating = false;
		}
	}

	private static ServerPlayer firstAliveOnline(MinecraftServer server, ShareTeam team) {
		for (UUID member : team.members()) {
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null && !player.isRemoved() && !player.isDeadOrDying()) {
				return player;
			}
		}
		return null;
	}

	private static void forEachOtherOnline(MinecraftServer server, ShareTeam team, UUID source,
			java.util.function.Consumer<ServerPlayer> action) {
		for (UUID member : team.members()) {
			if (member.equals(source)) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(member);
			if (player != null) {
				action.accept(player);
			}
		}
	}

	private static void clearPending(UUID player, Holder<MobEffect> effect) {
		Set<Holder<MobEffect>> pending = PENDING_UPDATES.get(player);
		if (pending != null) {
			pending.remove(effect);
			if (pending.isEmpty()) {
				PENDING_UPDATES.remove(player);
			}
		}
	}
}
