package com.sharedfate.sync;

import com.sharedfate.ui.GameOverCountdown;
import com.sharedfate.ui.RunResetMessages;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 서버 초기화 되묻기의 <b>「누가·언제까지」</b>.
 *
 * <p>{@code /shareteam reset} 은 무엇이 지워지는지 나열하고 수락을 기다린다. 이 클래스는 그
 * 기다림 하나만 들고 있다 — 누가 물었고, 몇 틱 남았는가.
 *
 * <h2>왜 떼어 두었는가</h2>
 * <p>명령을 실제로 받는 {@code RunResetCommand} 는 살아 있는 서버와
 * {@code CommandSourceStack} 이 있어야 해서 단위 시험으로 닿지 않는다. 그런데 여기서 틀리면
 * <b>남이 대신 수락</b>하거나 <b>대기가 영영 안 끝나서</b> 몇 시간 뒤의 {@code /yes} 한 번에
 * 서버가 날아간다. 둘 다 조용히 일어나므로, 판단만 순수 로직으로 떼어 시험이 닿게 했다.
 *
 * <h2>왜 밀리초가 아니라 틱인가</h2>
 * <p>시간을 흘리는 것이 서버 틱이기 때문이다. 벽시계로 재면 서버가 멈춰 있는 동안에도 시간이
 * 가서, 막 되돌아온 사람에게 「이미 지났습니다」가 뜬다. {@link GameOverCountdown} 이 같은
 * 이유로 틱을 센다.
 */
public final class RunResetConfirmation {
	/** 수락을 기다리는 시간(초). 문구에 적는 값과 반드시 같아야 한다. */
	public static final int TIMEOUT_SECONDS = RunResetMessages.TIMEOUT_SECONDS;

	/** 그 시간을 틱으로. 30초 = 600틱. */
	public static final int TIMEOUT_TICKS = TIMEOUT_SECONDS * GameOverCountdown.TICKS_PER_SECOND;

	/** 이름을 못 얻었을 때 대신 적는 말. 빈칸이 화면에 나가면 안 된다. */
	private static final String UNKNOWN_NAME = "알 수 없음";

	/** 수락을 받아 본 결과. */
	public enum Answer {
		/** 요청한 사람이 제때 수락했다. 대기는 이 순간 비워진다. */
		ACCEPTED,
		/** 다른 사람이 수락하려 했다. <b>대기는 그대로 남는다.</b> */
		NOT_REQUESTER,
		/** 기다리는 요청이 없다. 처음부터 없었거나 이미 지났다. */
		NOTHING_PENDING
	}

	private @Nullable UUID requester;
	private String requesterName = "";
	private int remainingTicks;

	/** 되묻기를 띄운다. 이미 기다리는 것이 있으면 시간이 처음부터 다시 흐른다. */
	public void request(UUID who, @Nullable String name) {
		requester = who;
		requesterName = name == null || name.isBlank() ? UNKNOWN_NAME : name;
		remainingTicks = TIMEOUT_TICKS;
	}

	public boolean pending() {
		return requester != null && remainingTicks > 0;
	}

	/** 지금 수락을 기다리는 사람. 기다리는 것이 없으면 {@code null}. */
	public @Nullable UUID requester() {
		return pending() ? requester : null;
	}

	/** 화면에 적을 요청자 이름. 기다리는 것이 없으면 빈 문자열. */
	public String requesterName() {
		return pending() ? requesterName : "";
	}

	public int remainingTicks() {
		return pending() ? remainingTicks : 0;
	}

	/** 남은 시간(초). 1틱이라도 남아 있으면 1초로 보여 준다. */
	public int remainingSeconds() {
		return GameOverCountdown.secondsRemaining(remainingTicks());
	}

	public boolean isRequester(@Nullable UUID who) {
		return pending() && requester.equals(who);
	}

	/**
	 * 수락을 받아 본다.
	 *
	 * <p>받아들인 경우에만 대기를 비운다. <b>남의 수락으로는 비우지 않는다</b> — 비우면 옆
	 * 사람이 {@code /yes} 한 번으로 남의 확인 절차를 조용히 취소시킬 수 있다.
	 */
	public Answer answer(@Nullable UUID who) {
		if (!pending()) {
			return Answer.NOTHING_PENDING;
		}
		if (who == null || !requester.equals(who)) {
			return Answer.NOT_REQUESTER;
		}
		clear();
		return Answer.ACCEPTED;
	}

	/**
	 * 한 틱 흘린다.
	 *
	 * @return 이 틱에 <b>막 만료되었으면</b> 참. 만료는 한 번만 알린다 — 매 틱 참을 돌려주면
	 *         「시간이 지났습니다」가 채팅에 쏟아진다
	 */
	public boolean tick() {
		if (!pending()) {
			return false;
		}
		if (--remainingTicks > 0) {
			return false;
		}
		clear();
		return true;
	}

	public void clear() {
		requester = null;
		requesterName = "";
		remainingTicks = 0;
	}
}
