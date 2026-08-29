package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code gather} 증강이 "지금 모을 때인가"를 어떻게 판단하는지 본다.
 *
 * <p>실제로 끌어오는 자리는 살아 있는 서버와 월드가 있어야 하므로 여기서는 다루지 않는다.
 * 대신 그 코드가 부르는 순수 계산({@link TeamGathering#anyPairTooFar})을 모두 확인한다.
 * 차원은 객체 동일성으로만 보므로 여기서는 {@link Object} 를 차원 대신 쓴다.
 */
class TeamGatheringTest {
	/** 오버월드와 네더를 대신하는 표. 실제 판정도 객체 동일성만 본다. */
	private static final Object 오버월드 = new Object();
	private static final Object 네더 = new Object();

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 기준_거리_안에_모여_있으면_모으지_않는다() {
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드, 오버월드),
				List.of(new Vec3(0, 64, 0), new Vec3(10, 64, 0), new Vec3(0, 64, 20)),
				64.0));
	}

	@Test
	void 둘_중_하나라도_기준_거리를_넘으면_모은다() {
		assertTrue(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드, 오버월드),
				List.of(new Vec3(0, 64, 0), new Vec3(10, 64, 0), new Vec3(0, 64, 200)),
				64.0));
	}

	@Test
	void 거리는_높이도_함께_센다() {
		assertTrue(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드),
				List.of(new Vec3(0, 0, 0), new Vec3(0, 100, 0)),
				64.0));
	}

	@Test
	void 딱_기준_거리면_아직_모으지_않는다() {
		// "넘으면" 이므로 경계값은 포함하지 않는다.
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드),
				List.of(new Vec3(0, 0, 0), new Vec3(64, 0, 0)),
				64.0));
	}

	@Test
	void 차원이_다르면_좌표가_같아도_모은다() {
		// 네더의 (0,0) 과 오버월드의 (0,0) 은 좌표만 보면 붙어 있지만 서로 닿을 수 없다.
		assertTrue(TeamGathering.anyPairTooFar(
				List.of(오버월드, 네더),
				List.of(new Vec3(0, 64, 0), new Vec3(0, 64, 0)),
				64.0));
	}

	@Test
	void 혼자면_흩어질_수_없다() {
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드), List.of(new Vec3(0, 0, 0)), 64.0));
		assertFalse(TeamGathering.anyPairTooFar(List.of(), List.of(), 64.0));
	}

	@Test
	void 목록_길이가_어긋나거나_기준이_이상하면_판정하지_않는다() {
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드),
				List.of(new Vec3(0, 0, 0)),
				64.0), "차원과 좌표의 수가 다르면 짝을 지을 수 없다");
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드),
				List.of(new Vec3(0, 0, 0), new Vec3(9999, 0, 0)),
				0.0));
		assertFalse(TeamGathering.anyPairTooFar(
				List.of(오버월드, 오버월드),
				List.of(new Vec3(0, 0, 0), new Vec3(9999, 0, 0)),
				-1.0));
	}
}
