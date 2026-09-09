package com.sharedfate.sync;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 「지금 이 범위가 켜져 있다」를 파티클 고리로 보여 준다.
 *
 * <h2>왜 필요한가</h2>
 * <p>「성역」과 「방패벽」은 <b>조건이 맞아야</b> 도는 증강이다. 그런데 켜졌는지 꺼졌는지 알
 * 방법이 없었다 — 성역은 몹이 원래 느린 건지 내가 켠 건지 구분이 안 되고, 방패벽은 화살이
 * 안 날아오는 것이 막힌 건지 애초에 안 쏜 건지 알 수 없다. 효과가 보이지 않으면 없는 것과
 * 같다.
 *
 * <p>그래서 켜져 있는 동안 <b>범위의 경계</b>에 파티클을 둘러 준다. 어디까지가 안인지도 함께
 * 알 수 있어, 「조금 더 붙어야 하나」를 눈으로 판단할 수 있다.
 *
 * <h2>새 패킷이 없다</h2>
 * <p>파티클은 바닐라 패킷이라 <b>통신 규약을 올리지 않는다.</b> 모드를 안 깐 사람에게도 보인다.
 *
 * <h2>바닥에 그리지 않는다</h2>
 * <p>고리를 발밑 높이에 그리면 지형에 파묻혀 안 보인다. 눈높이보다 조금 아래에 띄워
 * <b>공중에 뜬 고리</b>로 만든다. 언덕이든 동굴이든 같은 모양으로 보인다.
 */
public final class AuraRing {

	/**
	 * 고리 하나에 찍는 파티클 수.
	 *
	 * <p>반경이 커져도 개수는 그대로다. 개수를 반경에 비례시키면 넓은 고리에서 파티클이 수백
	 * 개가 되어 화면이 지저분해지고, 성긴 고리도 어디가 경계인지 아는 데는 충분하다.
	 */
	private static final int POINTS = 40;

	/** 발밑에서 얼마나 띄울 것인가(블록). */
	private static final double HEIGHT = 1.2;

	private AuraRing() {
	}

	/**
	 * 한 사람을 중심으로 고리 하나를 그린다.
	 *
	 * @param center 중심이 될 사람
	 * @param radius 반경(블록)
	 * @param type   찍을 파티클
	 * @param phase  고리를 돌리는 값. 틱을 넘기면 고리가 천천히 회전해 살아 있는 것처럼 보인다
	 */
	public static void draw(@Nullable ServerPlayer center, double radius,
			@Nullable ParticleOptions type, long phase) {
		if (center == null || type == null || !(radius > 0.0)) {
			return;
		}
		draw(center.level(), center.position(), radius, type, phase);
	}

	/** 좌표를 직접 주는 형태. 팀의 무게중심처럼 사람이 아닌 자리에 그릴 때 쓴다. */
	public static void draw(@Nullable ServerLevel level, @Nullable Vec3 center, double radius,
			@Nullable ParticleOptions type, long phase) {
		if (level == null || center == null || type == null || !(radius > 0.0)) {
			return;
		}
		double spin = (phase % 360L) * (Math.PI / 180.0);
		for (int index = 0; index < POINTS; index++) {
			double angle = spin + (Math.PI * 2.0 * index) / POINTS;
			double x = center.x + Math.cos(angle) * radius;
			double z = center.z + Math.sin(angle) * radius;
			// 개수 1, 퍼짐 0 이면 정확히 그 자리에 하나만 찍힌다. 속도도 0 이라 제자리에 머문다.
			level.sendParticles(type, x, center.y + HEIGHT, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
