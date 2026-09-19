package com.sharedfate.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 조합법 책을 통째로 죽인다. 제작대·화로·훈연기·용광로·플레이어 인벤토리가 모두 이 클래스를
 * 물려받으므로 한 곳만 고치면 된다.
 *
 * <h2>왜 없애는가</h2>
 * <p>바닐라 {@code AbstractRecipeBookScreen.extractRenderState} 는 책이 펴져 있고 화면이
 * 좁으면({@code widthTooNarrow}, 화면 폭 379 미만) <b>칸과 아이템을 아예 그리지 않는다</b> —
 * {@code extractContents} 대신 배경 하나만 그리는 {@code extractBackground} 로 갈아탄다.
 * 이 모드가 추가하는 27칸도 {@code extractContents} 안(={@code extractSlots})에서만 그려지므로
 * 그 순간 함께 사라진다. 책을 펴는 순간 창 전체가 빈 배경판만 남는 것으로 보이는 것이 이
 * 증상이다. 책을 아예 못 켜지게 하면 이 갈림길 자체가 없어진다.
 *
 * <p>단추 자리를 창에 맞춰 따라오게 손보는 미봉책도 가능했지만, 그래도 위 분기 자체는 남는다
 * — 화면이 379px 아래로 좁아지는 상황(GUI 배율을 올린 작은 창)은 이 모드와 무관하게 언제든
 * 올 수 있다. 책을 없애는 것이 이 모드가 요구받은 것이기도 하고, 근본적으로도 더 안전하다.
 *
 * <h2>조합은 그대로 된다</h2>
 * <p>여기서 건드리는 것은 {@code RecipeBookComponent} 하나뿐이다. 제작대·인벤토리의 조합칸
 * 자체는 {@code CraftingMenu}/{@code InventoryMenu} 가 다루고 이 믹스인은 그 근처를 지나지도
 * 않는다. 2×2·3×3 조합은 평소처럼 마우스로 재료를 놓아 만든다.
 *
 * <h2>단추만 지우면 안 되는 이유 — {@code tick()} 이 매 틱 되살린다</h2>
 * <p>{@code RecipeBookComponent.tick()} 은 매 틱 {@code isVisibleAccordingToBookData()}(=
 * 지난번에 열어 뒀는지가 적힌 저장값)와 지금 상태가 다르면 <b>그 저장값으로 되돌린다</b>.
 * 그래서 단추만 없애 클릭을 막아도, 예전에 책을 펴 둔 채로 저장된 적이 있다면 화면을 열자마자
 * 한 틱 만에 다시 펼쳐진다. {@link #sharedfate$forceClosed} 가 화면을 세우는 자리에서 한 번
 * 확실히 닫아 두는 이유가 이것이다 — {@code toggleVisibility()}(={@code setVisible(false)})는
 * 저장값도 함께 닫힌 것으로 고쳐 쓰므로, 다음 틀에서 재본 저장값도 이미 닫힌 채라 되살아나지
 * 않는다.
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {
	@Shadow
	@Final
	private RecipeBookComponent<?> recipeBookComponent;

	/**
	 * 조합법 책을 펴고 닫는 그 단추를 아예 만들지 않는다.
	 *
	 * <p>단추가 없으면 화면에서 누를 것이 없다. {@code recipeBookComponent} 를 위젯으로
	 * 등록하는 줄도 이 메서드 안에 있어 함께 사라지는데, 아래에서 책을 늘 닫아 두므로
	 * 위젯으로 등록해 봐야 어차피 아무 입력도 받지 않는다({@code RecipeBookComponent} 의
	 * 클릭·타이핑 처리는 하나같이 맨 앞에서 {@code isVisible()} 부터 본다).
	 */
	@Inject(method = "initButton", at = @At("HEAD"), cancellable = true)
	private void sharedfate$noButton(CallbackInfo ci) {
		ci.cancel();
	}

	/**
	 * 화면을 세울 때마다 책이 닫혀 있는지 확인하고, 열려 있으면 닫는다.
	 *
	 * <p>단추가 없으니 사람이 다시 열 길은 없지만, 저장된 「예전에 열어 뒀음」 값은 이
	 * 손질과 무관하게 남아 있다가 {@code tick()} 이 그대로 되살릴 수 있다. 여기서 한 번
	 * 확실히 닫아 저장값까지 고쳐 두면 그 뒤로는 무엇도 다시 열 것이 없다.
	 */
	@Inject(method = "init", at = @At("TAIL"))
	private void sharedfate$forceClosed(CallbackInfo ci) {
		if (recipeBookComponent.isVisible()) {
			recipeBookComponent.toggleVisibility();
		}
	}
}
