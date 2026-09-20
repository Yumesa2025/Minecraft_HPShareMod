package com.sharedfate.client.perk;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강 카드를 고르는 마우스 버튼 판정.
 *
 * <h2>왜 이 시험이 있는가</h2>
 * <p>26.3 이 GLFW 를 SDL 로 바꾸면서 <b>버튼 번호가 통째로 달라졌다.</b>
 *
 * <pre>
 *            26.2   26.3
 *   왼쪽       0   →   1
 *   가운데     2   →   2
 *   오른쪽     1   →   3
 * </pre>
 *
 * <p>{@code PerkOfferScreen} 이 {@code event.button() == 0} 으로 적어 두고 있었다. 26.3 에서
 * {@code 0} 은 <b>아무 버튼도 아니다.</b> 그래서 증강 선택 화면이 멀쩡히 뜨는데 <b>카드가 한
 * 번도 눌리지 않았다</b> — 빌드도 통과하고 시험도 통과하고 로그도 조용했다. 회차마다 여덟 번
 * 지나는 화면이라 이게 죽으면 게임이 굴러가지 않는다.
 *
 * <p>같은 판에서 키 코드도 바뀌었다({@code KEY_ESCAPE} 256 → 41). ESC 쪽은 처음부터
 * {@link InputConstants} 를 썼기 때문에 다시 컴파일하는 것만으로 따라갔다. <b>차이는 상수를
 * 썼느냐 숫자를 박았느냐 하나뿐이었다.</b>
 *
 * <p>이 시험은 판 번호에 기대지 않는다. 어느 판에서 돌리든 「왼쪽이면 참, 나머지면 거짓」만
 * 본다. 누가 다시 숫자를 박으면 그 값이 그 판의 왼쪽과 어긋나는 순간 여기서 걸린다.
 */
class PerkOfferScreenClickTest {

	@Test
	void 왼쪽_버튼이면_카드를_고른다() {
		assertTrue(PerkOfferScreen.isSelectClick(InputConstants.MOUSE_BUTTON_LEFT),
				"왼쪽 클릭으로 카드를 고를 수 없다. 버튼 번호를 숫자로 박아 두지 않았는지 보라");
	}

	@Test
	void 오른쪽과_가운데로는_고르지_않는다() {
		assertFalse(PerkOfferScreen.isSelectClick(InputConstants.MOUSE_BUTTON_RIGHT),
				"오른쪽 클릭으로 증강이 골라지면 되돌릴 방법이 없다");
		assertFalse(PerkOfferScreen.isSelectClick(InputConstants.MOUSE_BUTTON_MIDDLE));
	}

	/**
	 * 버튼 번호가 <b>판마다 달라지는 값</b>이라는 사실 자체를 못박는다.
	 *
	 * <p>셋이 서로 다른 값이어야 위 두 시험이 뜻을 갖는다. 그리고 왼쪽이 0 이 아니게 된 것이
	 * 이 버그의 전부였으므로, 그 사실을 여기 남겨 둔다.
	 */
	@Test
	void 버튼_번호는_판마다_달라지는_값이다() {
		assertNotEquals(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT);
		assertNotEquals(InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_MIDDLE);
		assertNotEquals(InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE);
	}
}
