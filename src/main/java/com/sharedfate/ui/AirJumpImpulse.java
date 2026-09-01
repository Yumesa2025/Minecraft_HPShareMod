package com.sharedfate.ui;

/**
 * 공중 점프가 몸에 싣는 속도를 계산하는 순수 함수.
 *
 * <p>{@link AirJumpInput} 이 "언제 뛰는가"를 정한다면 이쪽은 "얼마나, 어느 쪽으로 뛰는가"를
 * 정한다. 같은 이유로 공용 소스셋에 있다 — 클라이언트와 서버가 <b>글자 하나 다르지 않게</b>
 * 같은 값을 내야 하기 때문이다. 클라이언트는 키를 누른 그 틱에 이 값으로 스스로 몸을 띄우고,
 * 서버는 요청을 받아들일 때 같은 값을 제 쪽 사본에 적는다. 둘이 어긋나면 서버가 뒤늦게
 * 보내는 보정이 화면을 튀게 만든다. 이 파일에는 마인크래프트 클래스가 하나도 들어오지 않는다.
 *
 * <h2>위로 — 덮어쓰되 깎지는 않는다</h2>
 * <p>위쪽 속도를 {@code power} 로 덮어쓴다. 떨어지던 속도를 지워야 "한 번 더 뛰었다"가 되기
 * 때문이다. 다만 이미 그보다 빠르게 올라가고 있었다면 그대로 둔다. 그러지 않으면 (폭발에
 * 떠밀렸다든가 다른 증강이 겹쳤다든가로) 빠르게 오르던 중에 누른 공중 점프가 오히려
 * <b>속도를 깎아</b> 낮게 뛰는 결과가 된다. 바닐라의 {@code jumpFromGround} 도 같은 자리에서
 * {@code Math.max} 를 쓴다.
 *
 * <h2>앞으로 — 지금 가던 쪽으로 민다</h2>
 * <p>수평으로는 <b>지금 움직이고 있는 방향</b>으로 {@link #FORWARD_BOOST} 만큼 더한다. 보고
 * 있는 방향이 아니다. 제자리에서 뛰었을 때 앞으로 나가지 않아야 하는데, 시선을 기준으로 하면
 * 가만히 서서 뛰어도 어딘가로 밀려나기 때문이다. 가던 방향을 쓰면 "앞으로 뛰면 앞으로,
 * 제자리에서 뛰면 제자리"가 조건문 하나 없이 그대로 나온다.
 *
 * <p>기어가는 속도에서 갑자기 최대치가 붙지 않도록 {@link #FULL_BOOST_SPEED} 까지는 비례해
 * 커진다. 걷는 속도가 틱당 0.216 남짓이므로 걷기만 해도 최대치가 붙고, 살짝 미끄러지는
 * 정도(틱당 0.01)에서는 미는 힘도 그만큼 작다.
 *
 * <p>더하는 것이지 덮어쓰는 것이 아니다. 덮어쓰면 달리다가 뛴 순간 오히려 느려진다.
 */
public final class AirJumpImpulse {
	/**
	 * 수평으로 더하는 힘.
	 *
	 * <p>바닐라가 <b>달리기 점프</b>에 얹는 값과 같다({@code LivingEntity.jumpFromGround} 이
	 * 달리는 중이면 보는 방향으로 0.2 를 더한다). 새 숫자를 지어내는 대신 이미 게임 안에
	 * 있는 "한 번 확 나아간다"의 크기를 그대로 빌렸다. 공중 점프 뒤 남은 체공(대략 23틱)
	 * 동안 공기 마찰(틱당 0.91)을 견디며 약 두 칸을 더 나아간다.
	 */
	public static final double FORWARD_BOOST = 0.2;

	/**
	 * 이 속도 이상이면 {@link #FORWARD_BOOST} 를 다 받는다. 틱당 칸.
	 *
	 * <p>걷기(약 0.216)·달리기(약 0.281)는 모두 여기를 넘고, 웅크리기(약 0.066)는 못 넘어
	 * 절반 남짓만 받는다.
	 */
	public static final double FULL_BOOST_SPEED = 0.1;

	/** 이보다 느리면 방향이랄 것이 없다고 본다. 0 으로 나누는 것도 여기서 막힌다. */
	private static final double STANDSTILL_SPEED = 1.0E-6;

	/** 공중 점프가 끝난 뒤의 속도. */
	public record Velocity(double x, double y, double z) {
	}

	private AirJumpImpulse() {
	}

	/**
	 * 공중 점프를 실은 속도를 낸다.
	 *
	 * @param x     지금 속도의 x
	 * @param y     지금 속도의 y
	 * @param z     지금 속도의 z
	 * @param power 공중 점프가 실을 위쪽 속도
	 */
	public static Velocity of(double x, double y, double z, double power) {
		double speed = Math.sqrt(x * x + z * z);
		double boost = forwardBoost(speed);
		double pushedX = x;
		double pushedZ = z;
		if (boost > 0.0) {
			pushedX += x / speed * boost;
			pushedZ += z / speed * boost;
		}
		return new Velocity(pushedX, Math.max(power, y), pushedZ);
	}

	/**
	 * 이 수평 속도에서 앞으로 미는 힘이 얼마인가.
	 *
	 * @param horizontalSpeed 지금 수평 속도의 크기. 틱당 칸
	 */
	public static double forwardBoost(double horizontalSpeed) {
		if (horizontalSpeed < STANDSTILL_SPEED) {
			return 0.0;
		}
		if (horizontalSpeed >= FULL_BOOST_SPEED) {
			return FORWARD_BOOST;
		}
		return FORWARD_BOOST * horizontalSpeed / FULL_BOOST_SPEED;
	}
}
