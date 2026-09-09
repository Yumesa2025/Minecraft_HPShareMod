package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SwapExemptEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「가호 4」의 「전원에게」가 {@code holder} 를 쓰지 않는 가호 증강에도 걸리는지 본다.
 *
 * <h2>왜 이 시험이 있는가</h2>
 * <p>넉백 교체·피해 차단·교환 제외는 상태이상도 속성도 아니라 {@code holder} 에 담을 수 없어
 * 저마다 독립 효과 타입이 되었고, 그것들은 {@code TeamState.perkOwners} 에서 주인을 직접
 * 찾았다. 가호 세트의 모드 스위치가 {@code holder} 쪽에만 연결돼 있어 <b>「가호 4」를 모아도
 * 그 넷은 여전히 고른 사람에게만 걸렸다.</b> 판정을 {@link PerkBlessingSet#appliesTo} 한 곳으로
 * 모아 고쳤고, 이 시험이 그것을 지킨다.
 *
 * <p>값(「가호 3」의 강화)은 {@code BlessingAmplifiedValuesTest} 가 본다.
 */
class BlessingEveryoneTest {

	/**
	 * 기본 풀의 가호 증강 넷. 넷을 모으면 3단계와 4단계가 함께 켜진다.
	 *
	 * <p>「열외」를 맨 앞에 둔 이유는 <b>셋만 모은 팀에도 열외가 들어 있어야</b> 3단계와
	 * 4단계의 차이를 교환 명단으로 비교할 수 있기 때문이다.
	 */
	private static final List<String> 가호넷 = List.of(
			"sharedfate:swap_exempt", "sharedfate:cudgel", "sharedfate:porter",
			"sharedfate:bodyguard");

	private final UUID 주인 = UUID.randomUUID();
	private final UUID 남 = UUID.randomUUID();

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
	}

	@Test
	void 셋까지는_고른_사람에게만_걸린다(@TempDir Path dir) throws IOException {
		TeamState state = 가호를가진팀(dir, 3);

		assertTrue(PerkBlessingSet.appliesTo(state, "sharedfate:cudgel", 주인));
		assertFalse(PerkBlessingSet.appliesTo(state, "sharedfate:cudgel", 남),
				"셋만 모았을 때 남에게 걸리면 「고른 사람 하나」라는 약속이 깨진다");
		assertTrue(PerkBlessingSet.isAmplified(state, "sharedfate:cudgel"), "셋이면 강화다");
		assertTrue(PerkBlessingSet.teamAmplified(state));
	}

	@Test
	void 넷을_모으면_전원에게_걸린다(@TempDir Path dir) throws IOException {
		TeamState state = 가호를가진팀(dir, 4);

		assertTrue(PerkBlessingSet.appliesTo(state, "sharedfate:cudgel", 주인));
		assertTrue(PerkBlessingSet.appliesTo(state, "sharedfate:cudgel", 남),
				"넷을 모으면 고르지 않은 사람에게도 걸린다");
		assertFalse(PerkBlessingSet.isAmplified(state, "sharedfate:cudgel"),
				"3과 4는 맞바꾸는 관계다. 강화와 「전원에게」가 함께 켜지지 않는다");
		assertFalse(PerkBlessingSet.teamAmplified(state));
	}

	@Test
	void 가호가_아닌_증강은_언제나_고른_사람만이다(@TempDir Path dir) throws IOException {
		TeamState state = 가호를가진팀(dir, 4);
		state.ownedPerks.add("sharedfate:rotating_buff");
		state.perkOwners.put("sharedfate:rotating_buff", 주인);

		// 「버프 돌리기」는 holder 를 쓰지만 무유형이다. 가호 단계에 휩쓸리면 그 증강의 재미인
		// 「누가 받을지 모른다」가 통째로 사라진다.
		assertFalse(PerkBlessingSet.appliesTo(state, "sharedfate:rotating_buff", 남));
	}

	@Test
	void 넷을_모으면_팀_전원이_교환에서_빠진다(@TempDir Path dir) throws IOException {
		TeamState state = 가호를가진팀(dir, 4);

		SwapExemptEffect everyone = SwapExemptEffect.everyoneIn(state);
		assertNotNull(everyone, "「가호 4」면 열외가 팀 전원에게 걸린다");
		assertEquals(List.of(), PerkSwapRules.swapParticipantIds(state, List.of(주인, 남)),
				"아무도 자리를 바꾸지 않는다 — 교환이 사실상 꺼진다. 의도한 대가다");
	}

	@Test
	void 셋까지는_고른_사람만_교환에서_빠진다(@TempDir Path dir) throws IOException {
		// 셋만 모은 팀에도 열외 자체는 들어 있다(위 목록의 맨 앞).
		TeamState state = 가호를가진팀(dir, 3);

		assertNull(SwapExemptEffect.everyoneIn(state), "셋이면 전원에게 걸리지 않는다");
		assertEquals(List.of(남), PerkSwapRules.swapParticipantIds(state, List.of(주인, 남)),
				"고른 사람만 빠지고 나머지는 그대로 자리를 바꾼다");
	}

	@Test
	void 증강을_꺼_둔_팀에는_아무것도_걸리지_않는다(@TempDir Path dir) throws IOException {
		TeamState state = 가호를가진팀(dir, 4);
		state.perksEnabled = false;

		assertNull(SwapExemptEffect.everyoneIn(state));
	}

	// ------------------------------------------------------------------ 도우미

	/**
	 * 가호 증강을 {@code count} 개 가진 팀. 주인은 전부 {@link #주인} 이다.
	 *
	 * <p>세트 판정은 {@code ownedPerks} 를 매번 다시 세므로 목록에 넣는 것만으로 단계가 켜진다.
	 */
	private TeamState 가호를가진팀(Path dir, int count) throws IOException {
		loadDefaultPool(dir);
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (int i = 0; i < count; i++) {
			String perkId = 가호넷.get(i);
			state.ownedPerks.add(perkId);
			state.perkOwners.put(perkId, 주인);
		}
		return state;
	}

	/**
	 * 번들 기본 정의를 임시 폴더에 풀어 <b>두 레지스트리에 모두</b> 올린다.
	 *
	 * <p>세트 정의를 함께 올리지 않으면 가호 단계가 하나도 안 켜져 언제나 {@code NORMAL} 이
	 * 된다 — 판정이 세트 정의에 적힌 단계를 읽기 때문이다.
	 */
	private static void loadDefaultPool(Path dir) throws IOException {
		if (!PerkRegistry.isLoaded()) {
			try (InputStream bundled = BlessingEveryoneTest.class
					.getResourceAsStream("/sharedfate-perks-default.json")) {
				Files.copy(bundled, dir.resolve(PerkRegistry.FILE_NAME));
			}
			PerkRegistry.load(dir);
		}
		if (!PerkSetRegistry.isLoaded()) {
			PerkSetRegistry.load(dir);
		}
	}
}
