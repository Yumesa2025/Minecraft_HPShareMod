package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamManager;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 공유된 상태이상이 팀 전원에게 똑같이 주는 피해를 한 사람 몫으로 줄인다.
 *
 * <p>{@link EffectSync} 는 한 명이 걸린 상태이상을 팀 전원에게 복사한다. 그래서 독에 걸리면
 * 4인 팀은 네 명이 각자 독 피해를 받고, {@link StatMirror} 는 그 네 번의 체력 감소를 모두
 * 합산해 공유 체력에서 깎는다. 결과적으로 독 한 방이 4배가 된다. 위더처럼 주기적으로 피해를
 * 주는 상태이상은 전부 같은 문제를 갖는다.
 *
 * <p>합산 자체가 틀린 건 아니다. 팀원 A 가 좀비에게, B 가 스켈레톤에게 같은 틱에 맞았다면
 * 그건 진짜로 두 번 맞은 것이라 합산이 맞다. 틀린 건 "한 원인이 공유 때문에 여러 번으로
 * 보이는" 경우뿐이다. 그래서 여기서는 원인을 정확히 짚어 구분한다.
 *
 * <ol>
 *   <li>{@code MobEffectInstanceSharedTickMixin} 이 상태이상 틱이 도는 구간을 표시한다.
 *       그 구간 안에서 발생한 피해만 "상태이상이 원인인 피해"다. 몹·낙하·용암 피해는
 *       구간 밖이라 애초에 후보가 되지 않는다.
 *   <li>그 구간의 피해는 팀의 <em>대표 한 명</em>만 실제로 받는다. 나머지 팀원의 피해는
 *       {@code LivingEntityPerkDamageMixin} 이 {@code hurtServer} 진입 시점에서 막는다.
 *       체력이 애초에 줄지 않으므로 {@link StatMirror} 도 {@link DamageLedger} 도 그 몫을
 *       세지 않는다.
 * </ol>
 *
 * <p>상태이상 공유 자체는 그대로다. 팀원 넷 모두 여전히 독 아이콘과 입자를 갖고, 대표가
 * 받은 피해는 {@link SharedHurtFeedback} 이 팀 전원에게 피격 연출로 뿌리며, 줄어든 공유
 * 체력은 {@code StatMirror.writeBack} 이 전원에게 똑같이 써 준다. 보이는 결과는 "다 같이
 * 독에 걸려 다 같이 체력이 준다"로 이전과 같고, 깎이는 양만 1인분으로 바로잡힌다.
 *
 * <p>대표가 같은 상태이상을 갖고 있을 때만 막는다는 조건이 안전장치다. 어떤 이유로 대표에게
 * 그 상태이상이 없으면 아무도 막히지 않으므로, 팀이 공짜로 피해 면역을 얻는 일은 없다.
 * {@code shareStatusEffects} 가 꺼져 있으면 이 판정은 항상 거짓이라 동작이 완전히 이전과 같다.
 */
public final class SharedEffectDamage {
	/** 지금 상태이상 틱이 돌고 있는 대상. 구간 밖에서는 null 이다. */
	private static @Nullable LivingEntity tickingEntity;
	/** 지금 틱이 도는 상태이상의 종류. */
	private static @Nullable Holder<MobEffect> tickingEffect;
	/** 중첩 호출 대비. 0 → 1 로 올라갈 때만 구간을 기록한다. */
	private static int depth;

	private SharedEffectDamage() {
	}

	/** 상태이상 틱 구간을 연다. 반드시 {@link #endEffectTick()} 과 짝을 이뤄야 한다. */
	public static void beginEffectTick(LivingEntity entity, @Nullable Holder<MobEffect> effect) {
		if (depth++ == 0) {
			tickingEntity = entity;
			tickingEffect = effect;
		}
	}

	/** 상태이상 틱 구간을 닫는다. */
	public static void endEffectTick() {
		if (depth > 0 && --depth == 0) {
			tickingEntity = null;
			tickingEffect = null;
		}
	}

	/**
	 * 지금 들어온 피해가 공유 상태이상 때문에 중복으로 발생한 것인가.
	 *
	 * <p>{@code hurtServer} 진입 시점마다 불린다. 상태이상 틱 구간 밖이면 첫 줄에서 바로
	 * 빠져나가므로 평소 피해 경로에는 필드 비교 두 번만 얹힌다.
	 */
	public static boolean isDuplicateEffectDamage(@Nullable LivingEntity victim) {
		Holder<MobEffect> effect = tickingEffect;
		if (effect == null || victim == null || tickingEntity != victim) {
			return false;
		}
		if (SharedFateMod.config == null || !SharedFateMod.config.shareStatusEffects) {
			return false;
		}
		if (!(victim instanceof ServerPlayer player)) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		ShareTeam team = TeamManager.get(server).teamOf(player.getUUID());
		if (team == null) {
			return false;
		}
		ServerPlayer representative = StatMirror.damageRepresentative(server, team);
		// 대표를 못 고르면(전원 오프라인·사망) 아무도 막지 않는다.
		boolean victimIsRepresentative =
				representative == null || representative.getUUID().equals(player.getUUID());
		boolean representativeHasSameEffect =
				!victimIsRepresentative && representative.getEffect(effect) != null;
		return isDuplicateEffectDamage(true, true, true, victimIsRepresentative, representativeHasSameEffect);
	}

	/**
	 * 판정의 알맹이. 월드 없이 시험할 수 있도록 조건만 떼어 놨다.
	 *
	 * @param shareStatusEffects        상태이상 공유 설정이 켜져 있는가
	 * @param insideSharedEffectTick    지금 피해가 상태이상 틱 구간 안에서 났는가
	 * @param victimInTeam              피해자가 공유 팀에 속해 있는가
	 * @param victimIsRepresentative    피해자가 이 팀의 피해 대표인가
	 * @param representativeHasSameEffect 대표가 같은 상태이상을 갖고 있는가
	 * @return 이 피해를 버려야 하면 {@code true}
	 */
	static boolean isDuplicateEffectDamage(boolean shareStatusEffects, boolean insideSharedEffectTick,
			boolean victimInTeam, boolean victimIsRepresentative, boolean representativeHasSameEffect) {
		return shareStatusEffects
				&& insideSharedEffectTick
				&& victimInTeam
				&& !victimIsRepresentative
				&& representativeHasSameEffect;
	}

	/** 테스트나 서버 종료 때 남은 구간 표시를 지운다. */
	static void clearState() {
		tickingEntity = null;
		tickingEffect = null;
		depth = 0;
	}
}
