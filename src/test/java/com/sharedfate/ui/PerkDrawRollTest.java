package com.sharedfate.ui;

import com.sharedfate.perk.PerkChoiceSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 굴림 연출이 <b>어떤 길이로 돌려도</b> 고장 나지 않는지 확인한다.
 *
 * <p>여기 있는 시험 둘은 실제로 났던 고장에서 나왔다. 하나는 확정 소리가 영영 나지 않던 것,
 * 하나는 뽑힌 이름이 한두 틱 만에 사라지던 것이다. 둘 다 <b>70틱이라는 특정 길이에서만</b>
 * 드러났고 시험이 없어 아무도 몰랐다. 그래서 길이를 여럿 넣어 돌린다.
 */
class PerkDrawRollTest {

	/** 실제로 쓰는 길이(70)를 가운데 두고 위아래로 벌린 값들. 1은 최소 방어선이다. */
	private static final int[] LENGTHS = {
			1, 2, 3, 5, 8, 12, 13, 20, 24, 25, 30, 40, 55, 60, 69, 70, 71, 80, 100, 137, 200
	};

	/** 한 연출을 끝까지 돌려 틱마다 무슨 일이 있었는지 모은다. 뒤에 여유분까지 더 돌린다. */
	private static List<PerkDrawRoll.Event> run(int totalTicks) {
		PerkDrawRoll roll = new PerkDrawRoll(totalTicks);
		List<PerkDrawRoll.Event> events = new ArrayList<>();
		for (int tick = 0; tick < totalTicks + 60; tick++) {
			events.add(roll.tick());
		}
		return events;
	}

	private static int count(List<PerkDrawRoll.Event> events, PerkDrawRoll.Event wanted) {
		int found = 0;
		for (PerkDrawRoll.Event event : events) {
			if (event == wanted) {
				found++;
			}
		}
		return found;
	}

	/** 0부터 센 {@code events} 에서 확정이 일어난 자리. 실제 틱 번호는 여기에 1을 더한 값이다. */
	private static int revealIndex(List<PerkDrawRoll.Event> events) {
		return events.indexOf(PerkDrawRoll.Event.REVEAL);
	}

	@Test
	void 끝나는_순간_확정이_정확히_한_번_난다() {
		// 예전에는 끝나는 틱이 이름 교체 틱과 겹칠 때만 소리가 났다. 70틱에서는 한 번도
		// 겹치지 않아 확정 소리가 영영 나지 않았다. 어떤 길이로도 다시는 그러면 안 된다.
		for (int total : LENGTHS) {
			assertEquals(1, count(run(total), PerkDrawRoll.Event.REVEAL),
					total + "틱 연출에서 확정이 한 번이 아니다");
		}
	}

	@Test
	void 실제로_쓰는_길이에서도_확정이_난다() {
		// 서버가 보내는 값 그대로. 이 한 줄이 실제로 났던 고장을 정면으로 막는다.
		assertEquals(1, count(run(PerkChoiceSession.DRAW_TICKS), PerkDrawRoll.Event.REVEAL));
	}

	@Test
	void 확정은_굴림이_끝나는_바로_그_틱에_난다() {
		for (int total : LENGTHS) {
			// indexOf 는 0부터 세므로 틱 번호로 바꾸려면 1을 더한다.
			assertEquals(PerkDrawRoll.rollTicks(total), revealIndex(run(total)) + 1,
					total + "틱 연출에서 확정 시점이 굴림 끝과 다르다");
		}
	}

	@Test
	void 확정_뒤에는_이름이_바뀌지_않는다() {
		// 확정과 딸깍이 한 틱에 겹치면 딸깍이 확정을 덮어 "멈췄다"가 들리지 않는다.
		for (int total : LENGTHS) {
			List<PerkDrawRoll.Event> events = run(total);
			for (int index = revealIndex(events) + 1; index < events.size(); index++) {
				assertEquals(PerkDrawRoll.Event.NOTHING, events.get(index),
						total + "틱 연출에서 확정 뒤 " + index + "번째에 " + events.get(index));
			}
		}
	}

	@Test
	void 뽑힌_이름을_붙잡아_둘_틱이_남는다() {
		// 이것이 두 번째 고장이었다. 굴림이 총 길이를 다 써서 뽑힌 이름이 뜨는 그 틱에
		// 서버의 선택창이 도착했다. 이제는 굴림이 반드시 먼저 끝난다.
		for (int total : LENGTHS) {
			int hold = total - PerkDrawRoll.rollTicks(total);
			assertTrue(hold >= 0, total + "틱 연출의 굴림이 총 길이를 넘는다");
			if (total >= PerkDrawRoll.REVEAL_TICKS * 2) {
				assertEquals(PerkDrawRoll.REVEAL_TICKS, hold,
						total + "틱 연출에서 결과 표시가 " + hold + "틱뿐이다");
			}
		}
	}

	@Test
	void 실제_길이에서_결과를_0점6초_보여_준다() {
		int total = PerkChoiceSession.DRAW_TICKS;
		assertEquals(12, PerkDrawRoll.REVEAL_TICKS);
		assertEquals(58, PerkDrawRoll.rollTicks(total));
		// 0.25초로는 글자 크기·색·소리가 한꺼번에 바뀌는 것을 하나도 받기 전에 사라진다.
		assertTrue(PerkDrawRoll.REVEAL_TICKS >= 10, "결과 표시가 0.5초에 못 미친다");
		// 총 길이는 서버가 쥐고 있다. 결과 표시는 굴림에서 떼어 오는 것이라 한없이 늘릴 수 없다.
		assertTrue(PerkDrawRoll.REVEAL_TICKS <= 15, "굴림이 55틱 아래로 내려간다");
	}

	@Test
	void 굴림이_결과_표시보다_짧아지지_않는다() {
		// 짧은 길이로 불려도 굴린 것으로 보여야 한다. 굴림이 절반 아래로 내려가면 이름
		// 하나가 떴다 사라지는 화면이 된다.
		for (int total = 1; total <= 300; total++) {
			int rolling = PerkDrawRoll.rollTicks(total);
			assertTrue(rolling >= 1, total + "틱에서 굴림이 " + rolling + "틱이다");
			assertTrue(rolling <= total, total + "틱에서 굴림이 총 길이를 넘는다");
			assertTrue(rolling * 2 >= total, total + "틱에서 굴림이 절반도 안 된다");
		}
	}

	@Test
	void 어떤_길이로도_이름이_굴러간다() {
		// 굴림 없이 결과만 뜨면 추첨이 아니다. 아주 짧은 길이는 어쩔 수 없지만 실제로
		// 쓰는 길이 언저리에서는 눈에 굴림으로 읽힐 만큼 바뀌어야 한다.
		for (int total : LENGTHS) {
			if (total < 40) {
				continue;
			}
			assertTrue(count(run(total), PerkDrawRoll.Event.NEXT_NAME) >= 5,
					total + "틱 연출에서 이름이 다섯 번도 안 바뀐다");
		}
		assertTrue(count(run(PerkChoiceSession.DRAW_TICKS), PerkDrawRoll.Event.NEXT_NAME) >= 12,
				"실제 길이에서 굴림이 너무 성기다");
	}

	@Test
	void 마지막_이름이_충분히_머문다() {
		// 감속의 요점은 마지막 한 칸에 시선이 머무는 것이다. 다음 이름이 제 간격을 채우지
		// 못하고 결과에 잘릴 상황이면 아예 넘기지 않고 붙잡아 둔다. 이 장치가 없으면
		// 실제 길이에서 마지막 이름이 2틱 만에 지나가 애써 늦춰 온 감속이 헛수고가 된다.
		for (int total : LENGTHS) {
			if (total < 40) {
				continue;
			}
			assertTrue(lastNameHoldTicks(total) >= 4,
					total + "틱 연출에서 마지막 이름이 " + lastNameHoldTicks(total) + "틱만 머문다");
		}
		assertTrue(lastNameHoldTicks(PerkChoiceSession.DRAW_TICKS) >= 8,
				"실제 길이에서 마지막 이름이 "
						+ lastNameHoldTicks(PerkChoiceSession.DRAW_TICKS) + "틱만 머문다");
	}

	/** 마지막 이름 교체부터 확정까지 몇 틱이 남는지. */
	private static int lastNameHoldTicks(int totalTicks) {
		List<PerkDrawRoll.Event> events = run(totalTicks);
		int reveal = revealIndex(events);
		return reveal - events.subList(0, reveal).lastIndexOf(PerkDrawRoll.Event.NEXT_NAME);
	}

	@Test
	void 간격이_뒤로_갈수록_길어진다() {
		int rolling = PerkDrawRoll.rollTicks(PerkChoiceSession.DRAW_TICKS);
		int previous = 0;
		for (int elapsed = 0; elapsed <= rolling; elapsed++) {
			int step = PerkDrawRoll.stepTicks(elapsed, rolling);
			assertTrue(step >= previous, elapsed + "틱에서 간격이 줄었다: " + step);
			previous = step;
		}
		// 굴림이 끝나는 지점에서 마지막 간격에 정확히 닿아야 감속의 모양이 길이와 무관해진다.
		assertEquals(PerkDrawRoll.FIRST_STEP_TICKS, PerkDrawRoll.stepTicks(0, rolling));
		assertEquals(PerkDrawRoll.LAST_STEP_TICKS, PerkDrawRoll.stepTicks(rolling, rolling));
	}

	@Test
	void 간격은_길이가_달라져도_같은_모양이다() {
		// 분모가 굴림 길이라 길이를 바꿔도 처음과 끝의 간격은 그대로다.
		for (int total : LENGTHS) {
			int rolling = PerkDrawRoll.rollTicks(total);
			assertEquals(PerkDrawRoll.FIRST_STEP_TICKS, PerkDrawRoll.stepTicks(0, rolling));
			assertEquals(PerkDrawRoll.LAST_STEP_TICKS, PerkDrawRoll.stepTicks(rolling, rolling));
		}
	}

	@Test
	void 이상한_길이가_들어와도_깨지지_않는다() {
		for (int total : new int[] {0, -1, -70, Integer.MIN_VALUE}) {
			PerkDrawRoll roll = new PerkDrawRoll(total);
			int reveals = 0;
			for (int tick = 0; tick < 50; tick++) {
				if (roll.tick() == PerkDrawRoll.Event.REVEAL) {
					reveals++;
				}
			}
			assertEquals(1, reveals, total + " 을 넣었더니 확정이 " + reveals + "번이다");
			assertTrue(roll.revealed());
		}
	}

	@Test
	void 굴리는_동안에는_아직_결과가_아니다() {
		PerkDrawRoll roll = new PerkDrawRoll(PerkChoiceSession.DRAW_TICKS);
		int rolling = PerkDrawRoll.rollTicks(PerkChoiceSession.DRAW_TICKS);
		assertFalse(roll.revealed());
		for (int tick = 1; tick < rolling; tick++) {
			roll.tick();
			assertFalse(roll.revealed(), tick + "틱에 벌써 결과다");
		}
		assertEquals(PerkDrawRoll.Event.REVEAL, roll.tick());
		assertTrue(roll.revealed());
	}

	@Test
	void 진행도는_굴림을_기준으로_0에서_1까지_간다() {
		PerkDrawRoll roll = new PerkDrawRoll(PerkChoiceSession.DRAW_TICKS);
		assertEquals(0.0F, roll.progress(), 0.0001F);
		float previous = 0.0F;
		for (int tick = 0; tick < PerkChoiceSession.DRAW_TICKS; tick++) {
			roll.tick();
			float now = roll.progress();
			assertTrue(now >= previous, tick + "틱에서 진행도가 되돌아갔다");
			assertTrue(now <= 1.0F, tick + "틱에서 진행도가 1을 넘었다");
			previous = now;
		}
		assertEquals(1.0F, roll.progress(), 0.0001F);
	}
}
