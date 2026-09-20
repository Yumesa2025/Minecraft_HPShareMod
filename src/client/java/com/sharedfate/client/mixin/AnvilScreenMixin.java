package com.sharedfate.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sharedfate.enchant.AnvilDiamondLabel;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 모루 화면의 비용 라벨을 <b>「다이아몬드 10개」</b>로 바꾼다.
 *
 * <p>{@code AnvilMenuDiamondMixin} 이 {@code AnvilMenu.getCost()} 를 다이아몬드 개수로
 * 바꿔 둔 덕에 숫자는 이미 맞다. 그런데 바닐라가 그 숫자를 「마법부여 비용: 10」이라는
 * <b>경험치 레벨처럼 보이는 초록 글씨</b>로 적어, 다이아몬드 값인 줄 알 수 없었다.
 * 글월만 갈아 끼운다.
 *
 * <p>{@link AnvilDiamondLabel} 이 실제 글월을 만든다. 순수 함수로 떼어 둔 까닭은 시험
 * 소스셋이 {@code src/client} 를 보지 못하기 때문이다 — 확인은
 * {@code com.sharedfate.enchant.AnvilDiamondLabelTest} 가 한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.client.mixins.json} 에도 refmap 이 없어 <b>대상이 틀려도 빌드는
 * 통과하고 모루를 여는 순간 터진다.</b> 그래서 26.2 바이트코드를 직접 읽었다. 서술자를
 * 못박는 시험은 {@code AnvilMenuTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/client/gui/screens/inventory/AnvilScreen.class
 *
 * protected void extractLabels(GuiGraphicsExtractor, int, int);
 *      4: invokespecial  // ItemCombinerScreen.extractLabels  ← 부모 호출, 여기 끼어들지 않는다
 *     14: invokevirtual  // AnvilMenu.getCost:()I             → 지역변수 4
 *     21: ifle    194                                         ← 0 이면 아무것도 안 그린다
 *     28~45: cost >= 40 && !hasInfiniteMaterials              ← TOO_EXPENSIVE_TEXT 갈래.
 *                                                                getCost 가 10 이라 절대 안 온다
 *     83: ldc "container.repair.cost"
 *     97: invokestatic   // Component.translatable:(Ljava/lang/String;[Ljava/lang/Object;)
 *                        //   Lnet/minecraft/network/chat/MutableComponent;   ← 여기를 바꾼다
 *    132~151: x = imageWidth - 8 - font.width(글월) - 2       ← 글월을 먼저 바꿔야 자리가 맞는다
 *    191: invokevirtual  // GuiGraphicsExtractor.text(Font, Component, III)V
 * }</pre>
 *
 * <p>{@code extractLabels} 안에서 {@code Component.translatable} 은 <b>이 한 번</b>만 불린다
 * (「너무 비쌉니다」는 {@code TOO_EXPENSIVE_TEXT} 정적 밭이라 호출이 아니다). 그래서
 * {@code allow = 1} 로 못박았다.
 *
 * <p>글월을 <b>만들 때</b> 바꾸는 까닭은 바로 아래에서 {@code font.width(글월)} 로 오른쪽
 * 맞춤 자리를 계산하기 때문이다. 그리는 순간에 바꾸면 자리와 배경 상자가 옛 글월 폭에 맞아
 * 어긋난다.
 *
 * <p><b>색은 건드리지 않는다.</b> 바닐라는 색을 글월이 아니라 {@code text(...)} 의 인자로
 * 넘기므로, 다이아몬드가 모자라 집을 수 없을 때 빨갛게 되는 판정이 그대로 산다.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
	@ModifyExpressionValue(
			method = "extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/chat/Component;translatable"
							+ "(Ljava/lang/String;[Ljava/lang/Object;)"
							+ "Lnet/minecraft/network/chat/MutableComponent;"
			),
			require = 1,
			allow = 1
	)
	private MutableComponent sharedfate$diamondCostLabel(MutableComponent original) {
		return AnvilDiamondLabel.rewrite(original);
	}
}
