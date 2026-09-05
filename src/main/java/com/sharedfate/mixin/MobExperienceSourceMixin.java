package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.sync.ExperienceBonus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 몹에게서 나오는 경험치에 세트 배율({@code experience_bonus}, {@code source: "mob"})을 먹인다.
 *
 * <p>규칙과 계산은 {@link ExperienceBonus} 에 있다. 여기는 「어디서 곱하는가」만 정한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다. 서술자를 못박는 시험은
 * {@code ExperienceSourceTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/entity/LivingEntity.class
 *
 * // die → dropAllDeathLoot 의 마지막 줄
 *     40: aload_0
 *     41: aload_1                            // ServerLevel
 *     42: aload_2                            // DamageSource
 *     43: invokevirtual  // DamageSource.getEntity:()Lnet/minecraft/world/entity/Entity;
 *     46: invokevirtual  // dropExperience:(Lnet/minecraft/server/level/ServerLevel;
 *                        //                 Lnet/minecraft/world/entity/Entity;)V
 *
 * protected void dropExperience(ServerLevel, Entity);
 *      0: … wasExperienceConsumed / isAlwaysExperienceDropper /
 *         lastHurtByPlayerMemoryTime / shouldDropExperience / GameRules.MOB_DROPS
 *     47: aload_1
 *     48: aload_0
 *     49: invokevirtual  // position:()Lnet/minecraft/world/phys/Vec3;
 *     52: aload_0
 *     53: aload_1
 *     54: aload_2
 *     55: invokevirtual  // getExperienceReward:(Lnet/minecraft/server/level/ServerLevel;
 *                        //                      Lnet/minecraft/world/entity/Entity;)I
 *     58: invokestatic   // ExperienceOrb.award:(ServerLevel;Vec3;I)V
 *     61: return
 *
 * public final int getExperienceReward(ServerLevel, Entity);
 *      2: … getBaseExperienceReward
 *      8: invokestatic   // EnchantmentHelper.processMobExperience(...)I   ← 바닐라 인챈트 몫
 * }</pre>
 *
 * <h2>왜 {@code getExperienceReward} 를 <b>직접</b> 잡지 않는가</h2>
 * <p>그 메서드에는 처치자가 인자로 들어와 있어 그쪽에 파고들어도 사람은 알 수 있다. 그런데
 * 26.2 에서 그것을 부르는 곳이 하나 더 있다 — {@code SculkCatalystBlockEntity$CatalystListener}
 * 가 스컬크가 얼마나 번질지를 그 값으로 정한다. 거기까지 부풀리면 사냥 세트를 낀 팀 근처에서만
 * 스컬크가 더 번지는, 아무도 설명해 주지 않은 부작용이 생긴다. 그래서 <b>경험치 오브를 만드는
 * 그 한 번의 호출만</b> 골라 잡는다.
 *
 * <p>{@code @ModifyExpressionValue} 는 그 호출이 <b>돌려준 값</b>만 바꾼다. 바닐라 본문은
 * 그대로 돌고, 뒤이어 오는 {@code ExperienceOrb.award} 가 바뀐 값을 그대로 쓴다. 처치자
 * ({@code dropExperience} 의 두 번째 인자)를 그대로 이어 받으므로 <b>스레드 전역 문맥이 전혀
 * 필요 없다.</b>
 *
 * <h2>처치자가 누구인가</h2>
 * <p>{@code DamageSource.getEntity()} 다. 화살·삼지창처럼 던진 것에 맞아 죽으면 <b>쏜 사람</b>이
 * 들어온다(직접 맞힌 화살은 {@code getDirectEntity()} 쪽이다). 몹끼리 싸우거나 낙하·용암으로
 * 죽어 사람이 없으면 {@code null} 이고, 그때는 배율이 걸리지 않는다.
 */
@Mixin(LivingEntity.class)
public abstract class MobExperienceSourceMixin {
	@ModifyExpressionValue(
			method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/entity/Entity;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getExperienceReward("
							+ "Lnet/minecraft/server/level/ServerLevel;"
							+ "Lnet/minecraft/world/entity/Entity;)I"))
	private int sharedfate$scaleMobExperience(int reward, ServerLevel level, Entity killer) {
		return ExperienceBonus.scaleMobExperience(reward, killer);
	}
}
