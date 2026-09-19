package com.sharedfate.ui;

import com.sharedfate.TestBootstrap;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AbstractRecipeBookScreenMixin} 이 조합법 책을 죽이려고 무는 자리.
 *
 * <p>시험 소스셋은 {@code src/client} 의 그 믹스인을 보지 못하지만 바닐라 클라이언트 클래스는
 * 볼 수 있다. refmap 이 없어 {@code @Inject}·{@code @Shadow} 의 대상이 틀려도 빌드는 그냥
 * 통과하고 제작대나 인벤토리를 여는 순간에야 터지므로, 대상 서술자만이라도 여기서 붙들어 둔다.
 */
class RecipeBookRemovalTargetTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/**
	 * 단추를 만드는 자리. {@code @Inject(method = "initButton", at = @At("HEAD"),
	 * cancellable = true)} 가 여기서 곧바로 되돌아 나가 단추 자체가 안 생기게 막는다.
	 */
	@Test
	void 단추를_만드는_initButton이_그대로_있다() {
		assertDoesNotThrow(
				() -> AbstractRecipeBookScreen.class.getDeclaredMethod("initButton"),
				"이름이 바뀌거나 사라지면 단추를 막는 손질이 아무 데도 안 붙는다");
	}

	/**
	 * 화면을 세우는 자리. {@code @Inject(method = "init", at = @At("TAIL"))} 이 여기서
	 * 책을 강제로 닫는다 — {@code RecipeBookComponent.init} 이 저장값으로 이미 열어 둔 뒤라야
	 * 뜻이 있으므로 반드시 TAIL 이어야 한다.
	 */
	@Test
	void 화면을_세우는_init이_그대로_있다() {
		assertDoesNotThrow(
				() -> AbstractRecipeBookScreen.class.getDeclaredMethod("init"),
				"서명이 바뀌면 책을 강제로 닫는 손질이 엉뚱한 자리에 붙거나 아예 안 붙는다");
	}

	/**
	 * {@code @Shadow} 로 끌어다 쓰는 밭. private final 이라 이름과 타입이 정확히 맞아야
	 * 믹스인 결합이 된다.
	 */
	@Test
	void recipeBookComponent_밭이_그대로다() throws NoSuchFieldException {
		var field = AbstractRecipeBookScreen.class.getDeclaredField("recipeBookComponent");
		assertEquals(RecipeBookComponent.class, field.getType(),
				"타입이 달라지면 @Shadow 선언이 어긋나 결합 자체가 실패한다");
		assertTrue(Modifier.isFinal(field.getModifiers()),
				"final 이 아니게 되면 @Shadow 에도 @Final 을 반드시 맞춰 지워야 한다");
	}

	/**
	 * 책을 강제로 닫는 데 쓰는 두 메서드.
	 *
	 * <p>{@code isVisible()} 로 열려 있는지 보고, 열려 있으면 {@code toggleVisibility()} 로
	 * 끈다. 이 쪽이 {@code setVisible(false)} 를 직접 부르는 것보다 안전하다 —
	 * {@code toggleVisibility} 는 지금 상태를 스스로 뒤집으므로 이미 닫혀 있을 때 잘못 열어
	 * 버릴 일이 없다(우리는 열려 있을 때만 부르지만, 그렇더라도 이쪽이 바닐라가 원래 단추에
	 * 붙이는 것과 같은 길이라 더 믿을 수 있다).
	 */
	@Test
	void 책을_닫는_메서드_둘이_그대로_있다() {
		assertDoesNotThrow(
				() -> RecipeBookComponent.class.getDeclaredMethod("isVisible"),
				"없으면 지금 열려 있는지 알 길이 없다");
		assertDoesNotThrow(
				() -> RecipeBookComponent.class.getDeclaredMethod("toggleVisibility"),
				"없으면 강제로 닫을 길이 없다");
	}
}
