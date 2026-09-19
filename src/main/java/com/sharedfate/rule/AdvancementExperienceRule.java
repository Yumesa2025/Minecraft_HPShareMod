package com.sharedfate.rule;

/**
 * 발전과제 달성 보상에서 <b>경험치만</b> 걷어내는 규칙.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>이 모드는 경험치를 팀이 공유하고, 공유 레벨이 5·10·15…40 에 닿을 때마다 증강을
 * 고른다({@code PerkMilestones}). 그래서 의도하지 않은 경험치는 곧 <b>증강을 공짜로 얻는
 * 길</b>이 된다.
 *
 * <p>{@code WorldGameRules} 는 {@code show_advancement_messages} 게임 규칙을 꺼서 발전과제
 * 달성 <b>채팅 알림</b>만 없앤다. 26.2 바이트코드를 직접 읽어 확인한 결과
 * ({@code PlayerAdvancements.award}), 그 규칙은 알림 여부만 가릴 뿐 <b>달성 자체와
 * 보상은 그대로 이루어진다</b> — {@code AdvancementRewards.grant} 가 무조건 불려
 * {@code ServerPlayer.giveExperiencePoints} 로 경험치를 준다. 알림만 끈 것은 새는 길을
 * 막지 못한다.
 *
 * <h2>왜 달성 자체를 막지 않는가</h2>
 *
 * <p>26.2 의 기본 데이터팩을 보면 <b>가장 기초적인 조합법(제작대·화로·횃불 등)조차
 * 발전과제로 잠금 해제된다</b> — 예를 들어 {@code recipes/decorations/crafting_table.json} 은
 * {@code minecraft:tick}(항상 참) 조건과 {@code recipe_unlocked} 조건을 함께 걸어 두고
 * {@code rewards.recipes} 로 조합법을 내려준다. 발전과제 완료 자체를 막으면(예:
 * {@code PlayerAdvancements.award} 를 통째로 취소) <b>조합법 창이 게임 시작부터 끝까지
 * 텅 비게 된다</b> — 손으로 배치해 만드는 것은 여전히 되지만(조합법 잠금은 조합 자체를
 * 막지 않는다), 자동 완성·검색 같은 조합법 창의 편의 기능을 서버 전체가 영영 잃는다.
 *
 * <p>이 모드 자체의 어떤 기능도({@code perk}·엔딩 등) 발전과제 완료 여부를 보지 않으므로
 * 그 위험은 없지만, 조합법 창 부작용은 실재한다. 그래서 <b>달성은 그대로 두고 보상
 * 경험치만</b> 걷어낸다 — 전리품·조합법·함수 보상은 손대지 않는다.
 *
 * @see com.sharedfate.sync.WorldGameRules
 */
public final class AdvancementExperienceRule {
	private AdvancementExperienceRule() {
	}

	/**
	 * 발전과제가 주려던 경험치. 언제나 0이다.
	 *
	 * <p>전리품·조합법·함수 보상은 이 규칙이 손대지 않는다 — {@code AdvancementRewards.grant}
	 * 안에서 경험치를 건네주는 그 한 줄만 가로챈다.
	 */
	public static int strip(int vanillaExperience) {
		return 0;
	}
}
