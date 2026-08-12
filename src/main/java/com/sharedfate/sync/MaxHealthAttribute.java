package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class MaxHealthAttribute {
	private static final Identifier MODIFIER_ID = SharedFateMod.id("shared_max_health");

	private MaxHealthAttribute() {
	}

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
