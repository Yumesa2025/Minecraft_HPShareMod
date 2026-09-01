package com.sharedfate.ui;

import java.util.Locale;

/**
 * 능력치 한 줄을 「기본값 → 지금 값 (증감)」으로 적는다.
 *
 * <p>{@link GameStartButton}·{@link InventoryTeamButton} 과 같은 이유로 여기 있다 — 팀 화면은
 * {@code src/client} 에 있어 시험 소스셋이 볼 수 없으므로 <b>순수 계산만</b> 공용 소스셋으로
 * 내려 둔다.
 *
 * <h2>왜 이동 속도만 백분율인가</h2>
 * <p>바닐라 이동 속도의 기본값은 {@code 0.1} 이다. 그대로 적으면 <b>0.1 이 빠른 것인지 느린
 * 것인지 아무도 모른다.</b> 「115%」는 기본값을 100 으로 놓은 값이라 증강이 얼마나 밀어 올렸는지
 * 곧바로 읽힌다. 방어력·최대 체력은 반대다 — 방어구 칸 수와 하트 수로 이미 익은 단위라
 * 백분율로 바꾸면 오히려 낯설어진다.
 */
public final class StatSummary {
	/** 소수점 아래가 이만큼도 다르지 않으면 같은 값으로 본다. 부동소수 찌꺼기를 증감으로 읽지 않는다. */
	private static final double EPSILON = 1.0E-4;

	/** 값을 어떤 모습으로 적을지. */
	public enum Unit {
		/** 숫자를 그대로. 방어력·최대 체력처럼 이미 익은 단위에 쓴다. */
		RAW,
		/** 기본값을 100 으로 놓은 백분율. 이동 속도처럼 단위가 낯선 값에 쓴다. */
		PERCENT
	}

	private StatSummary() {
	}

	/**
	 * 올랐으면 1, 내렸으면 −1, 그대로면 0.
	 *
	 * <p>화면이 이 값으로 글자색을 고른다. 「오르면 좋다」가 아니라 「기본값과 다르다」만 말하므로,
	 * 대가로 무언가를 깎는 증강도 빨간색으로 정확히 보인다.
	 */
	public static int direction(double base, double current) {
		if (current > base + EPSILON) {
			return 1;
		}
		return current < base - EPSILON ? -1 : 0;
	}

	/**
	 * 화면이 다르게 그릴 만큼 달라졌는가.
	 *
	 * <p>서버가 공격력을 클라이언트에 보낼 때 「다시 보낼 값인가」를 이것으로 정한다. 화면이
	 * 어차피 같은 글자를 그릴 값이라면 패킷을 쓸 이유가 없고, 무엇보다 <b>「달라졌다」의 뜻이
	 * 화면과 네트워크에서 갈라지지 않아야</b> 한다. 갈라지면 한쪽은 보냈다고 여기는데 다른
	 * 쪽은 옛 글자를 그대로 두는 일이 생긴다.
	 */
	public static boolean changed(double previous, double now) {
		return direction(previous, now) != 0;
	}

	/**
	 * 「이름  기본값 → 지금 값  (증감)」 한 줄.
	 *
	 * <p>값이 그대로일 때는 괄호를 붙이지 않는다. 「(+0)」은 읽는 사람에게 아무것도 알려 주지
	 * 않으면서 줄만 길게 만든다. 기본값과 지금 값은 <b>같아도 둘 다 적는다</b> — 그래야 무엇이
	 * 기준인지 알 수 있다.
	 */
	public static String line(String label, double base, double current, Unit unit) {
		String head = label + "  " + value(base, base, unit) + " → " + value(current, base, unit);
		int direction = direction(base, current);
		if (direction == 0) {
			return head;
		}
		return head + "  (" + delta(base, current, unit) + ")";
	}

	/** 한 값을 적는다. 백분율이면 {@code base} 를 100 으로 놓고 잰다. */
	public static String value(double amount, double base, Unit unit) {
		if (unit == Unit.PERCENT && Math.abs(base) > EPSILON) {
			return Math.round(amount / base * 100.0) + "%";
		}
		return number(amount);
	}

	/** 증감. 부호를 반드시 붙인다 — 「6」과 「+6」은 뜻이 다르다. */
	public static String delta(double base, double current, Unit unit) {
		if (unit == Unit.PERCENT && Math.abs(base) > EPSILON) {
			long percent = Math.round(current / base * 100.0) - 100L;
			return (percent > 0 ? "+" : "") + percent + "%";
		}
		double difference = current - base;
		return (difference > 0 ? "+" : "") + number(difference);
	}

	/** 20.0 처럼 소수점이 의미 없는 값을 "20" 으로 적는다. 팀 화면의 표기와 같아야 한다. */
	public static String number(double value) {
		double rounded = Math.rint(value * 10.0) / 10.0;
		if (Math.abs(rounded - Math.rint(rounded)) < EPSILON) {
			return String.valueOf((long) Math.rint(rounded));
		}
		return String.format(Locale.ROOT, "%.1f", rounded);
	}
}
