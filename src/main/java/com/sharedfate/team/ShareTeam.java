package com.sharedfate.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ShareTeam(UUID teamId, String name, List<UUID> members) {

	public static final Codec<ShareTeam> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.STRING_CODEC.fieldOf("teamId").forGetter(ShareTeam::teamId),
			Codec.STRING.fieldOf("name").forGetter(ShareTeam::name),
			UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(ShareTeam::members)
	).apply(instance, ShareTeam::new));

	public ShareTeam {
		Objects.requireNonNull(teamId, "teamId");
		Objects.requireNonNull(name, "name");
		members = List.copyOf(members);
	}

	public static ShareTeam create(String name, UUID leader) {
		return new ShareTeam(UUID.randomUUID(), name, List.of(leader));
	}

	public UUID leader() {
		return members.isEmpty() ? null : members.getFirst();
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	public boolean contains(UUID player) {
		return members.contains(player);
	}

	public int size() {
		return members.size();
	}

	public ShareTeam withMemberAdded(UUID player) {
		Objects.requireNonNull(player, "player");
		if (members.contains(player)) {
			return this;
		}
		List<UUID> next = new ArrayList<>(members);
		next.add(player);
		return new ShareTeam(teamId, name, next);
	}

	public ShareTeam withMemberRemoved(UUID player) {
		if (!members.contains(player)) {
			return this;
		}
		List<UUID> next = new ArrayList<>(members);
		next.remove(player);
		return new ShareTeam(teamId, name, next);
	}
}
