package com.sharedfate.ui;

/**
 * 공중 점프(더블 점프)를 언제 허용할지 정하는 순수 상태 기계.
 *
 * <p>조작 코드는 {@code src/client} 에 있는데 시험 소스셋은 그쪽을 보지 못한다. 그래서
 * {@link PanelScroll} 과 같은 이유로 <b>판단만</b> 공용 소스셋인 여기로 내려 두었다. 이
 * 파일에는 마인크래프트 클래스가 하나도 들어오지 않는다.
 *
 * <h2>한 번 뜨면 한 번</h2>
 * <p>땅(물·사다리 포함)에 닿는 순간 모든 표시가 지워지고, 그 뒤 <b>착지하기 전까지</b>
 * 공중 점프를 정확히 한 번 쓸 수 있다. 높이도 떨어지는 중인지도 따지지 않는다.
 *
 * <h2>왜 "키를 뗀 적이 있어야" 하는가</h2>
 * <p>바닐라는 <b>땅에서 점프한 그 틱 안에서</b> 이미 땅을 떠난다. {@code LivingEntity.aiStep}
 * 이 {@code jumpFromGround()} 로 위쪽 속도를 넣고, 같은 메서드 뒷부분의 {@code travel()} 이
 * 몸을 옮겨 {@code onGround} 를 false 로 만든다. 클라이언트 틱이 끝나는 시점에는 "점프 키가
 * 방금 눌렸다"와 "지금 공중이다"가 <b>동시에</b> 참이다.
 *
 * <p>그래서 "공중 + 방금 눌림"만 보면 <b>땅 점프 한 번이 공중 점프까지 같이 써 버린다.</b>
 * 스페이스를 한 번 눌렀을 뿐인데 두 점프가 같은 틱에 겹치므로 두 번째 점프는 눈에 보이지
 * 않는다. 이 상태 기계는 그래서 "공중에서 점프 키를 뗀 적이 있는가"({@link #armed()})를
 * 따로 세고, 그 뒤의 누름만 공중 점프로 친다. 결과적으로 <b>공중에서 한 번 더 눌러야</b>
 * 뛰어오르는, 사람이 기대하는 그 동작이 된다.
 *
 * <p>스페이스를 계속 누르고 있으면 공중 점프는 일어나지 않는다. 누르고 있는 동안 저절로
 * 떠오르는 것은 점프가 아니라 비행이라, 「허공답보」가 주려는 것과 다르다.
 */
public final class AirJumpInput {
	/** 지난 틱에 점프 키가 눌려 있었는가. 누르는 <b>순간</b>만 잡아내려고 들고 있다. */
	private boolean jumpHeld;
	/** 공중에서 점프 키를 뗀 적이 있는가. 이게 참이어야 다음 누름이 공중 점프가 된다. */
	private boolean armed;
	/** 지금 뜬 상태에서 이미 공중 점프를 썼는가. 땅에 닿으면 풀린다. */
	private boolean used;

	/**
	 * 한 틱을 넘긴다.
	 *
	 * @param grounded 발판 위인가. 땅뿐 아니라 물·사다리·탈것도 발판으로 본다.
	 * @param jumpDown 이번 틱에 점프 키가 눌려 있는가
	 * @param allowed  지금 공중 점프가 성립하는가(증강 보유·관전 아님·비행 아님 등).
	 *                 거짓이어도 키와 발판은 계속 따라가므로, 조건이 풀리는 순간
	 *                 엉뚱한 누름이 터지지 않는다.
	 * @return 이번 틱에 공중 점프를 해야 하면 참. 참을 돌려준 그 순간 한 번을 소비한다.
	 */
	public boolean tick(boolean grounded, boolean jumpDown, boolean allowed) {
		boolean pressedNow = jumpDown && !jumpHeld;
		jumpHeld = jumpDown;

		if (grounded) {
			// 발판에 닿았다. 다음 도약은 처음부터 다시 센다.
			armed = false;
			used = false;
			return false;
		}
		if (!jumpDown) {
			// 공중에서 키를 뗐다. 이제부터의 누름은 땅 점프의 여운이 아니라 새 입력이다.
			armed = true;
		}
		if (!allowed || !pressedNow || !armed || used) {
			return false;
		}
		used = true;
		// 한 번 쓴 뒤에는 다시 뗐다 눌러도 소용없지만, 표시를 내려 두어야 상태가 헷갈리지 않는다.
		armed = false;
		return true;
	}

	/** 월드에서 나가거나 공중 점프가 꺼졌을 때 부른다. */
	public void reset() {
		jumpHeld = false;
		armed = false;
		used = false;
	}

	/** 공중에서 키를 뗀 적이 있어 다음 누름이 공중 점프가 되는가. 시험·진단용. */
	public boolean armed() {
		return armed;
	}

	/** 지금 뜬 상태에서 이미 공중 점프를 썼는가. 시험·진단용. */
	public boolean used() {
		return used;
	}
}
