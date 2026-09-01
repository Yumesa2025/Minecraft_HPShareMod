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
import com.sharedfate.perk.PerkLegacyGear;
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
import com.sharedfate.sync.WorldGameRules;
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
			// 회차 번호를 먼저 읽어야 한다. 바로 아래에서 명단을 맞출 때 「2회차 이상이면 언제나
			// 진행 중」이라는 규칙을 적용하는데, 그 판단의 근거가 회차 번호 하나뿐이다.
			RunProgressManager.onServerStarted(server);
			TeamRosterStore.onServerStarted(server);
			WorldResetCoordinator.onServerStarted(server);
			// 발전과제 달성 알림 끄기. 회차마다 월드가 새로 만들어지므로 월드에 한 번 적어
			// 두는 방식으로는 유지되지 않는다. 까닭은 WorldGameRules 에 적어 뒀다.
			WorldGameRules.onServerStarted(server);
			// 얼어 있는 채로 서버가 뜨는 일을 막는다. 강제 증강 선택이 남긴 시간 정지든
			// 다른 이유든, 시작 시점에 멈춰 있으면 무조건 풀고 로그를 남긴다.
			PerkManager.onServerStarted(server);
			// 증강 시험 명령이 켜져 있으면 시끄럽게 알린다. 조용히 켜져 있는 것이 가장 위험하다.
			com.sharedfate.command.PerkTestCommand.warnOnServerStarted(server);
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
			com.sharedfate.sync.StaggeredSwapManager.reset();
			com.sharedfate.sync.RallyPointManager.reset();
			com.sharedfate.sync.SwapExplosionScheduler.reset();
			com.sharedfate.perk.PerkResonantMining.reset();
			PerkWorldRules.reset();
			PerkCompassTargets.reset();
			com.sharedfate.perk.PerkGearManager.reset();
			PerkLegacyGear.reset();
			TimedPerkEffects.reset();
			MobPerkModifiers.reset();
			com.sharedfate.sync.DifficultyEscalation.reset();
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
			com.sharedfate.command.PerkTestCommand.warnOnJoin(player);
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
		// 「유산」의 전멸 시점 승계 스냅샷. DeathHandler 가 공유 인벤토리를 비우기 전에 떠야
		// 하므로 DeathHandler::onDeath 보다 반드시 먼저 등록한다(PerkLegacyGear 문서 참고).
		ServerLivingEntityEvents.AFTER_DEATH.register(PerkLegacyGear::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathHandler::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(RunProgressManager::onDeath);
		// 처치 보상 증강(on_kill)의 등록 지점. 죽은 쪽이 몹이 아니면 곧바로 빠져나간다.
		ServerLivingEntityEvents.AFTER_DEATH.register(PerkKillRewards::onDeath);
		// 보유자형 증강(holder)의 보유자가 죽으면 즉시 다른 팀원에게 넘기는 지점.
		ServerLivingEntityEvents.AFTER_DEATH.register(PerkHolderManager::onDeath);
		// 시차·정거장이 진행·대기 중이던 상태를 팀 전멸 때 지우는 지점.
		ServerLivingEntityEvents.AFTER_DEATH.register(com.sharedfate.sync.StaggeredSwapManager::onDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(com.sharedfate.sync.RallyPointManager::onDeath);
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
		// 나무를 광물로 바꾸는 증강(ore_exchange)의 등록 지점. 허공 우클릭에서만 발화한다.
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register(
				com.sharedfate.perk.PerkOreExchange::onUseItem);
		EffectSync.register();
		ServerTickEvents.END_SERVER_TICK.register(EffectSync::tick);
		ServerTickEvents.END_SERVER_TICK.register(StatMirror::tick);
		ServerTickEvents.END_SERVER_TICK.register(WorldResetCoordinator::tick);
		ServerTickEvents.END_SERVER_TICK.register(RunProgressManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PositionSwapManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.sync.StaggeredSwapManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.sync.RallyPointManager::tick);
		// 폭발 교환이 0.5초 미뤄 둔 폭발을 실제로 터뜨리는 지점.
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.sync.SwapExplosionScheduler::tick);
		// 공명(paired_mining)의 "혼자면 채굴 속도 페널티" 판정. 1초마다 실제로 확인한다.
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.perk.PerkResonantMining::tick);
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
		// 시간이 흐를수록 적대적 몹이 세지는 「난이도 상승」의 등록 지점. 증강의 몹 배율과는
		// 다른 속성 수정자를 쓰므로 둘이 서로 덮어쓰지 않고 곱해진다.
		ServerTickEvents.END_SERVER_TICK.register(com.sharedfate.sync.DifficultyEscalation::tick);
		ServerEntityEvents.ENTITY_LOAD.register(com.sharedfate.sync.DifficultyEscalation::onEntityLoad);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				ShareTeamCommand.register(dispatcher, config));
		LOGGER.info("SharedFate 로드됨");
	}
}
