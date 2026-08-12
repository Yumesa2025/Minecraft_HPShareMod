package com.sharedfate.sync;

import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

public final class SharedHurtFeedback {
	private SharedHurtFeedback() {
	}

	public static void onDamage(LivingEntity entity, DamageSource source,
			float baseDamageTaken, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer victim) || blocked || damageTaken <= 0.0F) {
			return;
		}
		TeamManager manager = TeamManager.get(victim.level().getServer());
		ShareTeam team = manager.teamOf(victim.getUUID());
		if (team == null) {
			return;
		}

		DamageSource visualSource = new DamageSource(source.typeHolder());
		for (UUID member : team.members()) {
			if (member.equals(victim.getUUID())) {
				continue;
			}
			ServerPlayer teammate = victim.level().getServer().getPlayerList().getPlayer(member);
			if (teammate != null && !teammate.isRemoved() && !teammate.isDeadOrDying()) {
				teammate.connection.send(new ClientboundDamageEventPacket(teammate, visualSource));
			}
		}
	}
}
