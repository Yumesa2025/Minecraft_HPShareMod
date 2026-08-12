package com.sharedfate;

import com.sharedfate.command.ShareTeamCommand;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.net.SharedFateNetworking;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.sync.MaxHealthAttribute;
import com.sharedfate.sync.EffectSync;
import com.sharedfate.sync.DeathHandler;
import com.sharedfate.sync.StatMirror;
import com.sharedfate.sync.SharedHurtFeedback;
import com.sharedfate.sync.WorldResetCoordinator;
import com.sharedfate.sync.RunProgressManager;
import com.sharedfate.sync.PositionSwapManager;
import com.sharedfate.sync.TeamRosterStore;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedFateMod implements ModInitializer {
	public static final String MOD_ID = "sharedfate";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static SharedFateConfig config;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		config = SharedFateConfig.loadOrCreate(
				FabricLoader.getInstance().getConfigDir().resolve("sharedfate.json"));
		SharedFateNetworking.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TeamLookup.setServer(server);
			TeamRosterStore.onServerStarted(server);
			RunProgressManager.onServerStarted(server);
			WorldResetCoordinator.onServerStarted(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(TeamRosterStore::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TeamLookup.setServer(null);
			ExpandedInventoryManager.clearRuntimeState();
			WorldResetCoordinator.reset();
			RunProgressManager.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> TeamManager.get(server).markDirtyIfActive());
		ServerPlayerEvents.JOIN.register(player -> {
			TeamManager manager = TeamManager.get(player.level().getServer());
			if (manager.consumeExperienceClear(player.getUUID())) {
				StatMirror.setTotalExperience(player, 0);
			}
			if (manager.consumeEffectClear(player.getUUID())) {
				EffectSync.clearPersistedDetachedPlayer(player);
			}
			MaxHealthAttribute.refresh(player, config.sharedMaxHealth);
			EffectSync.refreshPlayer(player);
			var team = manager.teamOf(player.getUUID());
			var state = manager.stateOf(player.getUUID());
			if (team != null && state != null) {
				StatMirror.syncPlayerNow(team.teamId(), state, player);
			}
			TeamBroadcaster.sendTo(player);
			RunProgressManager.onPlayerJoin(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			StatMirror.forget(oldPlayer.getUUID());
			MaxHealthAttribute.refresh(newPlayer, config.sharedMaxHealth);
			EffectSync.refreshPlayer(newPlayer);
			TeamManager manager = TeamManager.get(newPlayer.level().getServer());
			var team = manager.teamOf(newPlayer.getUUID());
			var state = manager.stateOf(newPlayer.getUUID());
			if (team != null && state != null) {
				StatMirror.syncPlayerNow(team.teamId(), state, newPlayer);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			var state = TeamLookup.stateOf(player.getUUID());
			if (state != null) {
				com.sharedfate.sync.InventorySwapper.stashCarried(player, state);
			}
			StatMirror.forget(player.getUUID());
			TeamBroadcaster.onDisconnect(player);
			ExpandedInventoryManager.removePlayer(player);
			RunProgressManager.onPlayerLeave(player);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(RunProgressManager::onDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SharedHurtFeedback::onDamage);
		EffectSync.register();
		ServerTickEvents.END_SERVER_TICK.register(EffectSync::tick);
		ServerTickEvents.END_SERVER_TICK.register(StatMirror::tick);
		ServerTickEvents.END_SERVER_TICK.register(WorldResetCoordinator::tick);
		ServerTickEvents.END_SERVER_TICK.register(RunProgressManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PositionSwapManager::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				ShareTeamCommand.register(dispatcher, config));
		LOGGER.info("SharedFate 로드됨");
	}
}
