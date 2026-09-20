package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「고른 사람만 쓸 수 있는」 증강을 <b>무작위로 받았을 때</b> 주인이 정해지는가.
 *
 * <p>주인은 {@code PerkManager.commit} 이 고른 사람이 있을 때만 적는다. 그래서 「숨은 재능」·
 * 「하늘의 은총」·「요행」·「도박꾼」으로 덤으로 받거나 「환골탈태」로 갈아엎으면 주인이 비고,
 * {@code perkOwners.get(id)} 가 {@code null} 이라 <b>팀의 누구와도 같지 않아 효과가 아무에게도
 * 안 걸렸다.</b> 「비행 부적」과 「열외」가 실제로 그랬다.
 *
 * <p>{@code PerkHolderManager.assignMissingOwners} 가 접속한 팀원 중 한 명을 무작위로 정해 주고
 * 팀 전원에게 알린다. 여기서 지키는 것은 <b>어느 증강이 그 구제를 받아야 하는가</b>다 — 그
 * 판정이 틀리면 구제가 통째로 빗나가거나, 팀 전체에 걸려야 할 증강이 한 명에게 묶인다.
 */
class PerkOwnerAssignmentTest {

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void unloadPool() {
		PerkRegistry.clear();
	}

	/** 기본 풀에서 실제로 겪은 둘. {@code OwnerBoundEffect} 표지가 붙어 있어야 한다. */
	@Test
	void 비행_부적과_열외는_주인이_필요하다() {
		loadDefaults();

		assertTrue(needsOwner("sharedfate:flight_charm"), "비행 부적");
		assertTrue(needsOwner("sharedfate:swap_exempt"), "열외");
	}

	/** 「호위」는 {@code damage_ward} 로, 「몽둥이찜질」은 넉백 교체로 주인을 본다. */
	@Test
	void 호위와_몽둥이찜질도_주인이_필요하다() {
		loadDefaults();

		assertTrue(needsOwner("sharedfate:bodyguard"), "호위");
	}

	/**
	 * 팀 전체에 걸리는 보통 증강은 주인을 잡지 않는다.
	 *
	 * <p>여기가 넓게 잡히면 <b>팀 전원이 받아야 할 증강이 한 명에게만 걸린다.</b> 고치려던 것보다
	 * 나쁜 결과라, 반대 방향도 반드시 못박는다.
	 */
	@Test
	void 팀_전체_증강은_주인을_잡지_않는다() {
		loadDefaults();

		assertFalse(needsOwner("sharedfate:porter"), "짐꾼은 팀 전체의 칸을 연다");
		assertFalse(needsOwner("sharedfate:excavator"), "굴착기는 팀 전체가 빨라진다");
	}

	@Test
	void 모르는_증강은_주인을_잡지_않는다() {
		loadDefaults();

		assertFalse(PerkHolderManager.needsOwner(null));
		assertFalse(needsOwner("sharedfate:그런것은없다"));
	}

	/** 모드 안의 기본 풀을 읽힌다. 설정 폴더는 보지 않는다. */
	private static void loadDefaults() {
		PerkRegistry.loadBundled();
	}

	private static boolean needsOwner(String perkId) {
		return PerkHolderManager.needsOwner(PerkRegistry.byId(perkId).orElse(null));
	}
}
