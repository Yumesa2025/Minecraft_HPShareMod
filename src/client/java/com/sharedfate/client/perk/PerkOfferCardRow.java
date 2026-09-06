package com.sharedfate.client.perk;

/**
 * 증강 카드 한 줄이 <b>가로로 어디에 서는가</b>.
 *
 * <h2>왼쪽 세트 판의 자리를 먼저 뗀다</h2>
 * <p>카드를 화면 한가운데에 놓고 나면 카드 왼쪽에 남는 폭은 <b>화면 폭에서 카드 줄 폭을 뺀
 * 절반</b>뿐이다. 카드 세 장은 116×3 + 틈 8×2 = 364 를 쓰므로, 화면이 480 픽셀인 자리에서는
 * 왼쪽에 58 밖에 안 남는다. 세트 판은 머리글까지 넣으면 70 남짓이 있어야 하므로 그 자리에서는
 * 판이 통째로 사라진다({@code PerkSetPanelLayout.fit} 이 {@code hidden} 을 돌려준다).
 * <b>증강을 고르는 순간이 세트를 가장 많이 보는 때인데 하필 그때 안 보이는 것이다.</b>
 *
 * <p>그래서 판이 쓸 폭을 <b>먼저 떼고</b> 남은 자리에 카드를 놓는다. 카드는 줄어들지 않고
 * 오른쪽으로 밀릴 뿐이다 — 넓은 화면에서는 가운데 자리가 이미 그 선보다 오른쪽이라 자리가
 * 하나도 안 바뀐다.
 *
 * <h2>그래도 카드가 먼저다</h2>
 * <p>자리를 뗀 탓에 카드가 {@link Limits#panelFloor()} 보다 좁아져야 한다면 <b>판을
 * 포기한다.</b> 카드가 좁아지면 설명이 여러 줄로 접히고, 접힌 만큼 카드가 세로로 길어지다가
 * 결국 잘려 나간다. 무엇을 고르는지가 안 읽히는 것이 판이 안 보이는 것보다 나쁘다.
 *
 * <p>계산에 마인크래프트 클래스를 하나도 쓰지 않는다. 글자 폭을 재는 일은 부르는 쪽이 하고
 * 여기는 <b>수만 받는다</b> — 그래야 화면을 띄우지 않고 시험할 수 있다.
 *
 * @param cardWidth     카드 한 장의 가로
 * @param firstCardLeft 첫 카드의 왼쪽 변
 */
public record PerkOfferCardRow(int cardWidth, int firstCardLeft) {

	/**
	 * 카드 줄이 지켜야 하는 치수.
	 *
	 * @param margin         화면 가장자리에서 띄우는 여백
	 * @param gap            카드 사이의 틈
	 * @param minWidth       카드가 아무리 좁아도 이만큼은 쓴다
	 * @param preferredWidth 자리가 넉넉할 때 쓰는 카드 폭
	 * @param panelFloor     세트 판 자리를 떼고도 카드가 유지해야 하는 최소 폭.
	 *                       이보다 좁아지면 판을 포기한다
	 */
	public record Limits(int margin, int gap, int minWidth, int preferredWidth, int panelFloor) {
	}

	/**
	 * 세트 판 자리를 뗀 카드 줄. 뗄 수 없으면 안 뗀 것과 같은 값이 나온다.
	 *
	 * @param count   카드 장수
	 * @param reserve 카드 왼쪽에 비워 둘 폭(판 폭 + 판과 카드 사이의 틈). 판이 없으면 0
	 */
	public static PerkOfferCardRow fit(Limits limits, int screenWidth, int count, int reserve) {
		PerkOfferCardRow plain = place(limits, screenWidth, count, 0);
		if (reserve <= 0) {
			return plain;
		}
		PerkOfferCardRow shifted = place(limits, screenWidth, count, reserve);
		if (shifted.cardWidth() < limits.panelFloor()) {
			return plain;
		}
		return shifted;
	}

	/**
	 * 왼쪽에 {@code reserve} 만큼 비워 둔 채로 카드를 놓는다.
	 *
	 * <p>가운데 자리가 이미 비워 둘 선보다 오른쪽이면 <b>가운데를 그대로 쓴다.</b> 넓은 화면에서
	 * 카드가 이유 없이 오른쪽으로 치우쳐 보이면 안 된다.
	 */
	private static PerkOfferCardRow place(Limits limits, int screenWidth, int count, int reserve) {
		int cards = Math.max(1, count);
		int gaps = Math.max(0, limits.gap()) * (cards - 1);
		int kept = Math.max(0, reserve);
		int available = Math.max(limits.minWidth() * cards + gaps,
				screenWidth - limits.margin() * 2 - kept);
		int fitted = (available - gaps) / cards;
		int cardWidth = Math.max(limits.minWidth(), Math.min(limits.preferredWidth(), fitted));

		int total = cardWidth * cards + gaps;
		int left = Math.max(limits.margin() + kept, (screenWidth - total) / 2);
		// 오른쪽으로 밀다가 화면 밖으로 나가면 안 된다. 여기에 걸리는 것은 자리를 떼고 나니
		// 카드가 최소 폭까지 줄어든 화면뿐이고, 그런 화면은 fit 이 이미 판을 포기한 뒤다.
		left = Math.min(left, screenWidth - limits.margin() - total);
		return new PerkOfferCardRow(cardWidth, Math.max(2, left));
	}

	/** 카드 줄 전체가 차지하는 가로. 카드가 화면 안에 다 들어갔는지 되짚을 때 쓴다. */
	public int totalWidth(int count, int gap) {
		int cards = Math.max(1, count);
		return cardWidth * cards + Math.max(0, gap) * (cards - 1);
	}
}
