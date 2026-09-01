package com.sharedfate.ui;

/**
 * 마인크래프트 26.2 기본 폰트의 글자 폭을 시험 안에서 되짚는다.
 *
 * <p>시험 소스셋에서는 바닐라 {@code Font} 를 띄울 수 없다(자원팩과 GL 문맥이 필요하다).
 * 그래서 자리 계산은 「글자 폭을 재는 것」을 밖에서 받도록 만들어 두었고
 * ({@link InventoryStatPanel#layout}), 여기서 그 자리에 넣을 자를 만든다.
 *
 * <h2>숫자의 출처</h2>
 * <p>실제 폰트 자원({@code assets/minecraft/font/*})에서 바닐라와 같은 식으로 뽑은 값이다.
 *
 * <ul>
 *   <li><b>한글 음절</b> 8.5 — {@code include/unifont.json} 이 {@code AC00–D7AF} 를
 *       {@code left=1, right=15} 로 덮어쓰고, {@code UnihexProvider} 의 폭은
 *       {@code (right − left + 1) / 2 + 1} 이다.</li>
 *   <li><b>숫자·대부분의 로마자</b> 6, <b>마침표</b> 2, <b>괄호</b> 4 —
 *       {@code ascii.png} 의 실제 칸 폭 + 1({@code BitmapProvider}).</li>
 *   <li><b>공백</b> 4 — {@code include/space.json}.</li>
 *   <li><b>화살표(→)</b> 8 — {@code nonlatin_european.png} 에 있고, 그 제공자가
 *       unifont 보다 앞서 있어 이쪽이 쓰인다.</li>
 * </ul>
 *
 * <p>합은 실수로 더한 뒤 올림한다. 바닐라 {@code Font.width} 가 그렇게 한다 — 한글이 홀수
 * 개일 때 0.5px 이 남는데, 그것을 버리면 시험이 실제보다 좁게 잰다.
 */
final class FakeFont {
	private FakeFont() {
	}

	static int width(String text) {
		double total = 0.0;
		for (int index = 0; index < text.length(); index++) {
			total += advance(text.charAt(index));
		}
		return (int) Math.ceil(total);
	}

	private static double advance(char letter) {
		if (letter >= 0xAC00 && letter <= 0xD7A3) {
			return 8.5;
		}
		return switch (letter) {
			case ' ' -> 4.0;
			case '→' -> 8.0;
			case '(', ')' -> 4.0;
			case '.' -> 2.0;
			default -> 6.0;
		};
	}
}
