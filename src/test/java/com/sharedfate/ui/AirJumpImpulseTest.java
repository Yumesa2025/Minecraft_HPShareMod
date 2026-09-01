package com.sharedfate.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirJumpImpulseTest {
	/** 「허공답보」의 두 번째 점프 세기. 바닐라 기본 점프 0.42 의 1.7배. */
	private static final double POWER = 0.714;
	/** 값 비교에 쓰는 오차. */
	private static final double EPSILON = 1.0e-9;

	// ------------------------------------------------------------------ 위로

	@Test
	void 떨어지던_속도를_지우고_점프_세기를_싣는다() {
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.0, -0.55, 0.0, POWER);

		assertEquals(POWER, pushed.y(), EPSILON);
	}

	@Test
	void 이미_더_빠르게_오르던_중이면_깎지_않는다() {
		// 폭발에 떠밀렸다든가 다른 증강이 겹쳤을 때. 여기서 덮어쓰면 공중 점프가 오히려
		// 발목을 잡아 낮게 뛴 것처럼 보인다.
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.0, 1.5, 0.0, POWER);

		assertEquals(1.5, pushed.y(), EPSILON);
	}

	// ------------------------------------------------------------------ 앞으로

	@Test
	void 제자리에서_뛰면_앞으로_나가지_않는다() {
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.0, -0.2, 0.0, POWER);

		assertEquals(0.0, pushed.x(), EPSILON);
		assertEquals(0.0, pushed.z(), EPSILON);
	}

	@Test
	void 가던_방향_그대로_밀어_준다() {
		// 걷는 속도(약 0.216)로 +z 로 가는 중. 방향이 꺾이지 않고 그 축으로만 커져야 한다.
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.0, -0.1, 0.216, POWER);

		assertEquals(0.0, pushed.x(), EPSILON);
		assertEquals(0.216 + AirJumpImpulse.FORWARD_BOOST, pushed.z(), EPSILON);
	}

	@Test
	void 뒤로_가던_중이면_뒤로_밀린다() {
		// 「앞」은 보는 방향이 아니라 가던 방향이다. 뒷걸음질 중이면 뒷걸음질이 세진다.
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(-0.216, -0.1, 0.0, POWER);

		assertEquals(-(0.216 + AirJumpImpulse.FORWARD_BOOST), pushed.x(), EPSILON);
		assertEquals(0.0, pushed.z(), EPSILON);
	}

	@Test
	void 비스듬히_가도_방향은_그대로고_크기만_커진다() {
		double speed = Math.sqrt(0.15 * 0.15 + 0.2 * 0.2);
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.15, -0.1, 0.2, POWER);

		double pushedSpeed = Math.sqrt(pushed.x() * pushed.x() + pushed.z() * pushed.z());
		assertEquals(speed + AirJumpImpulse.FORWARD_BOOST, pushedSpeed, 1.0e-6);
		// 방향(x:z 비율)이 그대로여야 한다.
		assertEquals(0.15 / 0.2, pushed.x() / pushed.z(), 1.0e-9);
	}

	@Test
	void 덮어쓰지_않고_더한다() {
		// 달리다가 뛰면 달리던 속도가 남아 있어야 한다. 덮어쓰면 뛴 순간 오히려 느려진다.
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(0.0, -0.1, 0.48, POWER);

		assertTrue(pushed.z() > 0.48, "달리던 속도보다 빨라져야 한다");
		assertEquals(0.48 + AirJumpImpulse.FORWARD_BOOST, pushed.z(), EPSILON);
	}

	// ------------------------------------------------------------------ 미는 힘의 크기

	@Test
	void 걷기_이상이면_힘을_다_받는다() {
		// 걷기 0.216, 달리기 0.281 은 모두 상한을 넘는다.
		assertEquals(AirJumpImpulse.FORWARD_BOOST, AirJumpImpulse.forwardBoost(0.216), EPSILON);
		assertEquals(AirJumpImpulse.FORWARD_BOOST, AirJumpImpulse.forwardBoost(0.281), EPSILON);
		assertEquals(AirJumpImpulse.FORWARD_BOOST,
				AirJumpImpulse.forwardBoost(AirJumpImpulse.FULL_BOOST_SPEED), EPSILON);
	}

	@Test
	void 기어가는_속도에서는_힘도_그만큼_작다() {
		// 문턱을 딱 잘라 두면 틱당 0.001 로 미끄러지던 사람이 갑자기 확 튀어 나간다.
		assertEquals(AirJumpImpulse.FORWARD_BOOST / 2.0,
				AirJumpImpulse.forwardBoost(AirJumpImpulse.FULL_BOOST_SPEED / 2.0), EPSILON);
		assertEquals(0.0, AirJumpImpulse.forwardBoost(0.0), EPSILON);
	}

	@Test
	void 아주_느린_속도에서도_값이_망가지지_않는다() {
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(1.0e-12, -0.2, 0.0, POWER);

		assertTrue(Double.isFinite(pushed.x()), "0 으로 나눠 NaN 이 되면 안 된다");
		assertTrue(Double.isFinite(pushed.z()));
		assertEquals(1.0e-12, pushed.x(), 1.0e-15);
	}

	// ------------------------------------------------------------------ 물리 계산

	/**
	 * 두 번째 점프가 실제로 몇 칸 오르는지.
	 *
	 * <p>마인크래프트의 수직 물리는 매 틱 <b>{@code y += v; v = (v - 0.08) * 0.98}</b> 이다.
	 * 이 계산이 맞는지는 바닐라 기본 점프(0.42)가 널리 알려진 1.2522칸을 내는지로 확인한다.
	 */
	@Test
	void 수직_물리_계산이_바닐라_점프_높이를_재현한다() {
		assertEquals(1.2522, riseHeight(0.42), 1.0e-4, "바닐라 기본 점프는 1.2522칸이다");
		assertEquals(2.5914, riseHeight(0.42 * 1.5), 1.0e-4, "점프력 +50% 는 2.5914칸");
		assertEquals(3.2406, riseHeight(POWER), 1.0e-4, "두 번째 점프는 3.2406칸");
	}

	/**
	 * 정점에서 이어 뛰었을 때의 낙하 피해.
	 *
	 * <p>{@code LivingEntity.calculateFallDamage} 는
	 * {@code floor((낙하거리 + 1e-6 - 안전거리) × 배율)} 이고, 안전거리는
	 * {@code Entity.BASE_SAFE_FALL_DISTANCE} 3 이다.
	 */
	@Test
	void 최고점에서_떨어져도_피해는_4다() {
		double peak = riseHeight(0.42 * 1.5) + riseHeight(POWER);

		assertEquals(5.8321, peak, 1.0e-4, "땅 점프 2.5914 + 공중 점프 3.2406");
		assertEquals(4, fallDamage(peak, 1.5), "낙하 피해 1.5배에서 4");
		// 세진 두 번째 점프와 낮아진 배율이 서로 상쇄되어, 예전(0.62·2배)과 같은 4 다.
		assertEquals(4, fallDamage(riseHeight(0.42 * 1.5) + riseHeight(0.62), 2.0));
	}

	/** {@code v0} 으로 밀었을 때 정점까지 오르는 높이. */
	private static double riseHeight(double v0) {
		double v = v0;
		double height = 0.0;
		while (v > 0.0) {
			height += v;
			v = (v - 0.08) * 0.98;
		}
		return height;
	}

	/** 그만큼 떨어졌을 때 들어오는 피해. */
	private static int fallDamage(double distance, double multiplier) {
		return (int) Math.max(0, Math.floor((distance + 1.0e-6 - 3.0) * multiplier));
	}
}
