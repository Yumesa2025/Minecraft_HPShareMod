package com.sharedfate.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.rule.AdvancementExperienceRule;
import net.minecraft.advancements.AdvancementRewards;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 발전과제 달성 보상에서 <b>경험치만</b> 걷어낸다. 전리품·조합법 해금·함수 보상은 그대로
 * 둔다.
 *
 * <p>규칙과 「왜」는 {@link AdvancementExperienceRule} 에 있다. 여기는 「어디서 막는가」만
 * 정한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다. 서술자를 못박는 시험은
 * {@code AdvancementRewardsTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/advancements/AdvancementRewards.class
 *
 * public void grant(ServerPlayer);
 *      0: aload_1
 *      1: aload_0
 *      2: getfield      // Field experience:I     ← 이 값 하나만 가로챈다
 *      5: invokevirtual // ServerPlayer.giveExperiencePoints:(I)V
 *      8: aload_1 … (전리품 추첨·지급)
 *     …: aload_1 … recipes.isEmpty() 이면 건너뛰고 아니면 awardRecipesByKey
 *     …: function … 있으면 실행
 * }</pre>
 *
 * <p>{@code experience} 필드를 읽는 자리는 이 메서드에 <b>단 한 번</b>이라
 * {@code require = 1} 로 못박아도 안전하다. 전리품·조합법·함수는 각자 다른 필드를 읽으므로
 * 이 자리에서는 전혀 건드리지 않는다.
 *
 * <p>{@code AdvancementRewards} 는 {@code record} 이자 {@code final} 클래스라 <b>재정의될 수
 * 없다.</b> 「하위 클래스가 재정의하는 메서드에 믹스인을 거는 것」 함정이 애초에 성립하지
 * 않는 자리다.
 *
 * <h2>왜 {@code PlayerAdvancements.award} 를 통째로 막지 않는가</h2>
 * <p>26.2 의 기본 데이터팩은 <b>기초 조합법 해금조차 발전과제 보상</b>이다
 * ({@code recipes/decorations/crafting_table.json} 등). 달성 자체를 막으면 조합법 창이
 * 게임 시작부터 텅 빈다. 자세한 근거는 {@link AdvancementExperienceRule} 의 클래스 문서에
 * 있다.
 */
@Mixin(AdvancementRewards.class)
public abstract class AdvancementRewardsExperienceMixin {
	@ModifyExpressionValue(
			method = "grant(Lnet/minecraft/server/level/ServerPlayer;)V",
			at = @At(
					value = "FIELD",
					target = "Lnet/minecraft/advancements/AdvancementRewards;experience:I",
					opcode = Opcodes.GETFIELD),
			require = 1)
	private int sharedfate$stripAdvancementExperience(int vanillaExperience) {
		return AdvancementExperienceRule.strip(vanillaExperience);
	}
}
