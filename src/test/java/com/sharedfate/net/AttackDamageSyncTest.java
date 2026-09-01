package com.sharedfate.net;

import com.sharedfate.TestBootstrap;
import com.sharedfate.ui.StatSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공격력을 클라이언트로 보내는 규칙.
 *
 * <p>「언제 다시 보내는가」와 「이상한 값을 어떻게 눕히는가」 둘만 본다. 값을 <b>뽑는</b> 쪽
 * ({@code getBaseValue}/{@code getValue})은 살아 있는 플레이어가 있어야 하므로 여기서 보지
 * 않고, {@code ScreenStatSourceTest} 가 그 두 값이 무엇인지를 붙들어 둔다.
 */
class AttackDamageSyncTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	/** 한 번도 보내지 않았으면 무조건 보낸다. 접속 직후와 재접속 직후가 이 경우다. */
	@Test
	void 아직_보낸_적이_없으면_보낸다() {
		assertTrue(new AttackDamagePayload(1.0F, 1.0F).differsFrom(null));
	}

	@Test
	void 값이_그대로면_다시_보내지_않는다() {
		AttackDamagePayload sent = new AttackDamagePayload(1.0F, 7.0F);

		assertFalse(new AttackDamagePayload(1.0F, 7.0F).differsFrom(sent));
	}

	/** 무기를 바꾸면 지금 값이 달라진다. 그것이 곧 다시 보내는 신호다. */
	@Test
	void 지금_값이_달라지면_다시_보낸다() {
		AttackDamagePayload sent = new AttackDamagePayload(1.0F, 7.0F);

		assertTrue(new AttackDamagePayload(1.0F, 4.0F).differsFrom(sent));
		assertTrue(new AttackDamagePayload(1.0F, 9.0F).differsFrom(sent));
	}

	/** 기본값이 움직이는 일은 드물지만, 움직였는데 안 보내면 「→」 왼쪽이 거짓말이 된다. */
	@Test
	void 기본값이_달라져도_다시_보낸다() {
		AttackDamagePayload sent = new AttackDamagePayload(1.0F, 7.0F);

		assertTrue(new AttackDamagePayload(2.0F, 7.0F).differsFrom(sent));
	}

	/**
	 * 「달라졌다」의 기준을 화면과 나눠 쓴다.
	 *
	 * <p>{@link StatSummary#changed} 가 무시할 만큼 작은 차이면 패킷도 나가지 않아야 한다.
	 * 두 기준이 갈라지면 초당 네 번씩 아무 뜻 없는 패킷이 나가거나, 반대로 화면이 옛 글자를
	 * 그대로 들고 있게 된다.
	 */
	@Test
	void 화면이_구분하지_못하는_차이로는_보내지_않는다() {
		AttackDamagePayload sent = new AttackDamagePayload(1.0F, 7.0F);
		float dust = Math.nextUp(7.0F);

		assertFalse(StatSummary.changed(7.0F, dust));
		assertFalse(new AttackDamagePayload(1.0F, dust).differsFrom(sent));
	}

	@Test
	void 무한대와_NaN_은_0으로_눕힌다() {
		assertEquals(0.0F, new AttackDamagePayload(Float.NaN, Float.NaN).current());
		assertEquals(0.0F,
				new AttackDamagePayload(1.0F, Float.POSITIVE_INFINITY).current());
		assertEquals(0.0F,
				new AttackDamagePayload(Float.NEGATIVE_INFINITY, 1.0F).base());
	}

	/** 바닐라는 이 속성을 0 아래로 두지 않는다. 다른 모드가 범위를 바꿔도 화면은 지킨다. */
	@Test
	void 음수는_0으로_눕힌다() {
		AttackDamagePayload payload = new AttackDamagePayload(-3.0F, -1.0F);

		assertEquals(0.0F, payload.base());
		assertEquals(0.0F, payload.current());
	}
}
