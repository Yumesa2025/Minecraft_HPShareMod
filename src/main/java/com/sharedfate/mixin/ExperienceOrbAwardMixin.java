package com.sharedfate.mixin;

import com.sharedfate.sync.ExperienceBonus;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 게임 전체의 경험치 획득량에 배율을 먹이는 자리.
 *
 * <p>규칙과 기본값은 {@link ExperienceBonus} 에, 설정 항목은
 * {@code SharedFateConfig.experienceMultiplier} 에 있다. 여기는 「어디서 곱하는가」만 정한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/entity/ExperienceOrb.class
 *
 * public static void award(ServerLevel, Vec3, int);
 *      0: aload_0
 *      1: aload_1
 *      2: getstatic     // Field net/minecraft/world/phys/Vec3.ZERO
 *      5: iload_2
 *      6: invokestatic  // Method awardWithDirection:
 *                       //   (Lnet/minecraft/server/level/ServerLevel;
 *                       //    Lnet/minecraft/world/phys/Vec3;
 *                       //    Lnet/minecraft/world/phys/Vec3;I)V
 *      9: return
 *
 * public static void awardWithDirection(ServerLevel, Vec3, Vec3, int);
 *      0: iload_3                       // 남은 양
 *      1: ifle          45
 *      4: iload_3
 *      5: invokestatic  // getExperienceValue(I)I  ← 이번 오브에 담을 몫
 *     10: iload_3 / iload 4 / isub / istore_3   ← 남은 양에서 뺀다
 *     15: … tryMergeToExisting / new ExperienceOrb / addFreshEntity
 *     42: goto          0                ← 남은 양이 0 이 될 때까지 반복
 *     45: return
 * }</pre>
 *
 * <p>여기서 두 가지가 확인된다.
 * <ul>
 *   <li>{@code award} 는 {@code awardWithDirection} 을 그대로 부르는 한 줄짜리다. 즉 <b>둘 중
 *       아래쪽 하나만 잡으면 두 경로가 모두 걸린다.</b></li>
 *   <li>인자 3번({@code iload_3})이 「앞으로 나눠 담을 총량」이고, 반복문이 그 값을 깎아 가며
 *       오브를 만든다. 그래서 <b>진입 직후에 그 값을 바꾸면</b> 오브 개수와 각 오브의 몫이
 *       바닐라 규칙 그대로 다시 계산된다. 오브를 직접 만들 필요가 없다.</li>
 * </ul>
 *
 * <p>참조하는 클래스도 확인했다. 26.2 의 공통 jar 에서 {@code ExperienceOrb} 를 참조하는 것은
 * {@code LivingEntity}(몹·플레이어 사망) · {@code Block}({@code popExperience}, 광석) ·
 * {@code AbstractFurnaceBlockEntity} · {@code FishingHook} · {@code Animal} ·
 * {@code Villager} · {@code WanderingTrader} · {@code ThrownExperienceBottle} ·
 * {@code GrindstoneMenu} · {@code ServerGamePacketListenerImpl} 이고, 전부 위 두 정적
 * 메서드로 들어온다.
 *
 * <h2>왜 {@code @ModifyVariable} 인가</h2>
 * <p>{@code @Inject} 로 가로채 스스로 다시 부르면 이 mixin 이 자기 자신을 부르는 고리가 된다.
 * 인자 하나만 바꾸고 바닐라 본문을 그대로 태우는 편이 안전하고, 오브를 몇 개로 쪼갤지도
 * 바닐라가 정한 규칙대로 남는다. {@code argsOnly = true} 에 {@code ordinal = 0} 이면 인자 중
 * 첫 번째 {@code int} — 이 메서드에는 하나뿐인 그 값이다.
 *
 * <h2>여기에 걸리지 않는 것</h2>
 * <p>{@code Player.giveExperiencePoints}(운영자의 {@code /xp} 등)는 오브를 만들지 않으므로
 * 배율이 걸리지 않는다. 손으로 넣어 준 값까지 부풀릴 이유가 없으니 이대로가 맞다.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbAwardMixin {
	@ModifyVariable(
			method = "awardWithDirection(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;I)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static int sharedfate$scaleAwardedExperience(int amount) {
		return ExperienceBonus.scale(amount);
	}
}
