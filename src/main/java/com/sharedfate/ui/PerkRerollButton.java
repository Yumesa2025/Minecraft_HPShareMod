package com.sharedfate.ui;

/**
 * 증강 선택창의 「다시 뽑기」 단추가 무엇을 보여 주고 언제 눌리는지.
 *
 * <p>화면({@code PerkOfferScreen})은 {@code src/client} 에 있어 시험 소스셋이 볼 수 없다.
 * 그래서 <b>순수 계산만</b> 여기로 내려 둔다. 화면은 이 결과를 위젯에 옮겨 담기만 한다.
 *
 * <p>여기서 정하는 것은 <b>보여 주기</b>뿐이다. 실제로 다시 뽑아도 되는지는 서버가
 * {@code PerkManager.applyReroll} 에서 처음부터 다시 따진다. 이 클래스가 참을 돌려준다고
 * 해서 서버가 요청을 받아 준다는 뜻이 아니다.
 */
public final class PerkRerollButton {

	private PerkRerollButton() {
	}

	/**
	 * 단추에 적을 글자. 남은 횟수가 늘 보여야 한다.
	 *
	 * <p>0 일 때도 「0회 남음」이 아니라 못 쓴다는 뜻이 드러나게 적는다. 단추가 회색으로
	 * 잠긴 이유가 글자에 있어야 한다.
	 */
	public static String label(int remaining) {
		return remaining > 0
				? "다시 뽑기 (" + remaining + "회 남음)"
				: "다시 뽑기 (남은 횟수 없음)";
	}

	/**
	 * 단추를 그릴지.
	 *
	 * <p>서버가 강제로 띄운 창에서, 고를 권한이 있는 사람에게만 보인다. 관전자에게 보이면
	 * 눌러도 서버가 버리는 단추가 되고, {@code /shareteam perk} 로 직접 연 창에서는 시간이
	 * 멈춰 있지 않아 다시 뽑기 자체가 성립하지 않는다.
	 *
	 * <p>남은 횟수가 0 이어도 <b>그리기는 한다.</b> 사라지면 「원래 없는 기능」처럼 보인다.
	 */
	public static boolean visible(boolean forced, boolean canChoose) {
		return forced && canChoose;
	}

	/**
	 * 지금 누를 수 있는지.
	 *
	 * @param choiceSent    이미 증강을 골라 보낸 뒤인지
	 * @param rerollSent    다시 뽑기를 눌러 두고 서버의 새 후보를 기다리는 중인지
	 * @param showingResult 무엇이 정해졌는지 보여 주는 중인지
	 * @param remaining     이번 회차에 남은 횟수
	 */
	public static boolean enabled(boolean forced, boolean canChoose, boolean choiceSent,
			boolean rerollSent, boolean showingResult, int remaining) {
		return visible(forced, canChoose) && !choiceSent && !rerollSent && !showingResult
				&& remaining > 0;
	}
}
