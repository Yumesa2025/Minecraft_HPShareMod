package com.sharedfate;

import com.sharedfate.command.ShareTeamCommand;
import com.sharedfate.config.SharedFateConfig;
import com.sharedfate.net.SharedFateNetworking;
import com.sharedfate.net.TeamBroadcaster;
import com.sharedfate.perk.ConditionalPerkManager;
import com.sharedfate.perk.MobPerkModifiers;
import com.sharedfate.perk.PerkBlockBreaks;
import com.sharedfate.perk.PerkClientRules;
import com.sharedfate.perk.PerkCompassTargets;
import com.sharedfate.perk.PerkHealthRules;
import com.sharedfate.perk.PerkHolderManager;
import com.sharedfate.perk.PerkKillRewards;
import com.sharedfate.perk.PerkLifesteal;
import com.sharedfate.perk.PerkManager;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkTriggers;
import com.sharedfate.perk.PerkWorldRules;
import com.sharedfate.perk.PeriodicPerkManager;
import com.sharedfate.perk.TimedPerkEffects;
import com.sharedfate.inventory.ExpandedInventoryManager;
import com.sharedfate.sync.MaxHealthAttribute;
import com.sharedfate.sync.EffectSync;
import com.sharedfate.sync.DeathHandler;
import com.sharedfate.sync.StatMirror;
import com.sharedfate.sync.SharedHurtFeedback;
import com.sharedfate.sync.WorldResetCoordinator;
import com.sharedfate.sync.RunProgressManager;
import com.sharedfate.sync.PositionSwapManager;
import com.sharedfate.sync.TeamGathering;
import com.sharedfate.sync.TeamRosterStore;
import com.sharedfate.team.TeamLookup;
import com.sharedfate.team.TeamManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
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
		PerkRegistry.load(FabricLoader.getInstance().getConfigDir());
		SharedFateNetworking.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TeamLookup.setServer(server);
			TeamRosterStore.onServerStarted(server);
			RunProgressManager.onServerStarted(server);
			WorldResetCoordinator.onServerStarted(server);
			// 얼어 있는 채로 서버가 뜨는 일을 막는다. 강제 증강 선택이 남긴 시간 정지든
			// 다른 이유든, 시작 시점에 멈춰 있으면 무조건 풀고 로그를 남긴다.
			PerkManager.onServerStarted(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(TeamRosterStore::onServerStopping);
		// 종료 직전에 시간 정지를 되돌린다. reset 은 서버가 완전히 멈춘 뒤라 너무 늦다.
		ServerLifecycleEvents.SERVER_STOPPING.register(PerkManager::onServerStopping);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TeamLookup.setServer(null);
			ExpandedInventoryManager.clearRuntimeState();
			WorldResetCoordinator.reset();
			RunProgressManager.reset();
			PerkManager.reset();
			PerkHealthRules.reset();
			ConditionalPerkManager.reset();
			PeriodicPerkManager.reset();
			PerkHolderManager.reset();
			TeamGathering.reset();
			PerkWorldRules.reset();
			PerkCompassTargets.reset();
			com.sharedfate.perk.PerkGearManager.reset();
			TimedPerkEffects.reset();
			MobPerkModifiers.reset();
			PerkClientRules.reset();
			EffectSync.reset();
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
			PerkManager.onPlayerJoin(player);
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
			PerkManager.refreshPlayer(newPlayer);
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
			PerkManager.onPlayerLeave(player);
			PerkHolderManager.onPlayerLeave(player);
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(RunProgressManager::onDeath);
		// 처치 보상 증강(on_kill)의 등록 지점. 죽은 쪽이 몹이 아니면 곧바로 빠져나간다.
		ServerLivingEntityEvents.AFTER_DEATH.register(PerkKillRewards::onDeath);
		// 보유자형 증강(holder)의 보유자가 죽으면 즉시 다른 팀원에게 넘기는 지점.
		ServerLivingEntityEvents.AFTER_DEATH.register(PerkHolderManager::onDeath);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SharedHurtFeedback::onDamage);
		// 팀원이 맞았을 때 잠깐 걸리는 증강(on_team_hurt)의 등록 지점.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PerkTriggers::onDamage);
		// 준 피해의 일부를 팀 공유 체력으로 되돌리는 증강(lifesteal)의 등록 지점.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PerkLifesteal::onDamage);
		// 보유자가 맞으면 버프를 넘기는 증강(holder + pass_on_hurt)의 등록 지점.
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PerkHolderManager::onDamage);
		// 블록 파괴 증강(bonus_drop / on_break)의 등록 지점. 팀 증강이 없으면 곧바로 빠져나간다.
		PlayerBlockBreakEvents.AFTER.register(PerkBlockBreaks::onBlockBroken);
		// 수면 차단 증강(no_sleep)의 집행 지점. null 을 돌려주면 평소대로 잔다.
		EntitySleepEvents.ALLOW_SLEEPING.register(PerkWorldRules::onAllowSleep);
		EffectSync.register();
		ServerTickEvents.END_SERVER_TICK.register(EffectSync::tick);
		ServerTickEvents.END_SERVER_TICK.register(StatMirror::tick);
		ServerTickEvents.END_SERVER_TICK.register(WorldResetCoordinator::tick);
		ServerTickEvents.END_SERVER_TICK.register(RunProgressManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PositionSwapManager::tick);
		// 흩어진 팀을 한곳으로 모으는 증강(gather)의 판정 지점. 1초에 한 번만 실제로 잰다.
		ServerTickEvents.END_SERVER_TICK.register(TeamGathering::tick);
		ServerTickEvents.END_SERVER_TICK.register(PerkManager::tick);
		// 최대 체력 고정 증강(max_health_lock)이 명령이나 다른 증강에 밀리지 않게 지키는 지점.
		// StatMirror 보다 뒤에 등록해야 공유 체력 계산이 끝난 뒤에 상한을 되돌린다.
		ServerTickEvents.END_SERVER_TICK.register(PerkHealthRules::tick);
		// 팀 상태에 따라 갈리는 증강(conditional)의 주기 평가 지점.
		ServerTickEvents.END_SERVER_TICK.register(ConditionalPerkManager::tick);
		// 주기로 켜졌다 꺼지는 증강(periodic)의 주기 평가 지점.
		ServerTickEvents.END_SERVER_TICK.register(PeriodicPerkManager::tick);
		// 한 명만 효과를 받는 증강(holder)의 보유자 순환 지점.
		ServerTickEvents.END_SERVER_TICK.register(PerkHolderManager::tick);
		// 시간 고정 증강(time_lock)의 되돌리기 지점. 20틱마다 오버월드 시계만 제자리로 돌린다.
		ServerTickEvents.END_SERVER_TICK.register(PerkWorldRules::tick);
		// 나침반 지시 증강(compass_target)의 집행 지점. 증강을 잃은 팀의 나침반을
		// 되돌리는 길도 여기뿐이다.
		ServerTickEvents.END_SERVER_TICK.register(PerkCompassTargets::tick);
		// 방아쇠형 증강이 잠깐 걸어 둔 효과를 시간이 되면 걷어내는 지점.
		ServerTickEvents.END_SERVER_TICK.register(TimedPerkEffects::tick);
		// 장비 제한 증강(equip_ban / item_ban / offhand_lock / weapon_damage)의 집행 지점.
		// 증강을 잃은 사람에게 남아 있던 공격력 수정자를 걷어내는 길도 여기뿐이다.
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.perk.PerkGearManager::tick);
		// 몹에게 걸리는 증강(mob_health / mob_damage)의 등록 지점.
		ServerTickEvents.END_SERVER_TICK.register(MobPerkModifiers::tick);
		ServerEntityEvents.ENTITY_LOAD.register(MobPerkModifiers::onEntityLoad);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				ShareTeamCommand.register(dispatcher, config));
		LOGGER.info("SharedFate 로드됨");
	}
}
