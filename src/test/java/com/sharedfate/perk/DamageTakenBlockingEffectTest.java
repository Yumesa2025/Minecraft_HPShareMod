package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DamageTakenBlockingEffect;
import com.sharedfate.perk.effect.ShieldFallImmunityEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 방어 2단계 「방패 뒤가 편하다」({@code damage_taken_blocking})를 살아 있는 게임 없이 시험한다.
 *
 * <p>실제 자세({@code ServerPlayer.isBlocking})는 살아 있는 월드가 있어야 읽을 수 있다. 그래서
 * 그 값을 읽는 일은 {@link PerkDamage} 의 비공개 자리에 두고, 여기서는 읽어 온 값으로 답을 내는
 * 순수 판정({@link PerkDamage#blockingMultiplier}·{@link PerkDamage#blockingMultiplierOf})을 본다.
 * {@link ShieldFallImmunityEffectTest} 가 같은 방식이다.
 *
 * <p>정의 읽기는 {@code PerkEffectType} 을 거치지 않고 팩토리를 직접 부른다. 그 열거형에 새 줄을
 * 넣는 일은 다른 사람 몫이라, 여기서 열거형 상수를 참조하면 순서에 따라 빌드가 깨진다. 등록이
 * 끝나면 {@code DefaultPerkSetValuesTest} 가 「기본 정의가 조용히 사라지지 않았는가」를 잡는다.
 */
class DamageTakenBlockingEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	/** 다른 시험이 올려 둔 정의가 남아 있으면 답이 흔들린다. 앞뒤로 모두 비운다. */
	@BeforeEach
	void 준비() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 배율을_적으면_읽힌다() {
		DamageTakenBlockingEffect effect = assertInstanceOf(DamageTakenBlockingEffect.class,
				raw(0, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": 0.5 }"));

		assertEquals(0.5, effect.multiplier(), 1.0e-9);
	}

	@Test
	void 배율이_없거나_숫자가_아니면_버린다() {
		assertNull(raw(0, "{ \"type\": \"damage_taken_blocking\" }"));
		assertNull(raw(0, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": \"0.5\" }"));
	}

	@Test
	void 배율이_범위를_벗어나면_버린다() {
		assertNull(raw(0, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": -0.5 }"));
		assertNull(raw(0, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": 1000 }"));
	}

	/** 1 배는 아무 일도 하지 않는다. 설명만 있고 동작이 없는 정의를 미리 걸러 낸다. */
	@Test
	void 배율이_1이면_버린다() {
		assertNull(raw(0, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": 1.0 }"));
	}

	/**
	 * 하위 효과로 들어가면 버린다.
	 *
	 * <p>{@code PerkDamage} 는 최상위 효과만 훑으므로, {@code periodic}·{@code conditional} 안에
	 * 넣으면 조용히 아무 일도 하지 않는다. {@code damage_taken_from} 과 같은 기준이다.
	 */
	@Test
	void 하위_효과로_들어가면_버린다() {
		assertNull(raw(100, "{ \"type\": \"damage_taken_blocking\", \"multiplier\": 0.5 }"));
	}

	// ------------------------------------------------------------------ 자세 판정

	@Test
	void 막는_중일_때만_배율이_걸린다() {
		DamageTakenBlockingEffect effect = new DamageTakenBlockingEffect(0.5);

		assertEquals(0.5, effect.multiplierFor(true), 1.0e-9);
		assertEquals(1.0, effect.multiplierFor(false), 1.0e-9,
				"방패를 내리면 배율이 걸리면 안 된다");
	}

	/** 조건 없는 배율로는 절대 새어 나가지 않는다. 그 자리에는 자세가 넘어오지 않는다. */
	@Test
	void 조건_없는_받는_피해_배율은_1이다() {
		assertEquals(1.0, new DamageTakenBlockingEffect(0.5).damageTakenMultiplier(), 1.0e-9);
	}

	@Test
	void 효과_목록에서도_막을_때만_걸린다() {
		List<PerkEffect> effects = List.of(new DamageTakenBlockingEffect(0.5));

		assertEquals(0.5, PerkDamage.blockingMultiplierOf(effects, true), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplierOf(effects, false), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplierOf(null, true), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplierOf(List.of(), true), 1.0e-9);
	}

	/** 여럿을 가졌으면 전부 곱한다. 다른 배율을 모으는 규칙과 같다. */
	@Test
	void 여러_개면_배율이_곱해진다() {
		double total = PerkDamage.blockingMultiplierOf(
				List.of(new DamageTakenBlockingEffect(0.5), new DamageTakenBlockingEffect(0.5)),
				true);

		assertEquals(0.25, total, 1.0e-9);
	}

	/** 「받는 피해 50% 감소」가 실제 피해량에 그대로 반영된다. */
	@Test
	void 절반_배율은_피해를_절반으로_만든다() {
		double factor = PerkDamage.blockingMultiplierOf(
				List.of(new DamageTakenBlockingEffect(0.5)), true);

		assertEquals(5.0F, PerkDamage.combine(10.0F, factor), 1.0e-5F);
		assertEquals(10.0F, PerkDamage.combine(10.0F,
				PerkDamage.blockingMultiplierOf(List.of(new DamageTakenBlockingEffect(0.5)), false)),
				0.0F);
	}

	/** 배율 0 은 「막는 동안 완전 면역」이라는 뜻이라 1 로 되돌리지 않는다. */
	@Test
	void 배율_0은_그대로_0이다() {
		double factor = PerkDamage.blockingMultiplierOf(
				List.of(new DamageTakenBlockingEffect(0.0)), true);

		assertEquals(0.0, factor, 1.0e-9);
		assertEquals(0.0F, PerkDamage.combine(10.0F, factor), 0.0F);
	}

	// ------------------------------------------------------------------ 팀 조회

	@Test
	void 팀이_없거나_막지_않으면_1이다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertEquals(1.0, PerkDamage.blockingMultiplier(null, true), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplier(teamWith("sharedfate:guard"), false), 1.0e-9);
	}

	/**
	 * 증강도 세트도 이 효과를 갖고 있지 않으면 아무 일도 일어나지 않는다.
	 *
	 * <p>세트 정의를 비운 채로 부른다 — 세트를 쓰지 않는 서버에서 이 자리가 조용히 1 로
	 * 물러나는지 본다.
	 */
	@Test
	void 세트도_증강도_없으면_아무_일도_없다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));
		PerkSetRegistry.clear();

		assertTrue(PerkSetRegistry.isEmpty());
		assertEquals(1.0, PerkDamage.blockingMultiplier(teamWith("sharedfate:guard"), true), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplier(teamWith("sharedfate:plain"), true), 1.0e-9);
		assertEquals(1.0, PerkDamage.blockingMultiplier(TeamState.fresh(20.0F), true), 1.0e-9,
				"증강이 하나도 없는 팀은 훑을 것도 없다");
	}

	/** 풀에서 사라진 id 는 조용히 건너뛴다. 저장에만 남은 id 는 언제든 생긴다. */
	@Test
	void 정의가_없는_id는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertEquals(1.0, PerkDamage.blockingMultiplier(teamWith("sharedfate:missing"), true), 1.0e-9);
	}

	// ------------------------------------------------------------------ 낙하 면역과의 관계

	/**
	 * 「버티는 방패」(낙하 면역)는 그대로다.
	 *
	 * <p>둘 다 「막는 중인가」를 보지만 하는 일이 다르다. 새 배율을 넣으면서 낙하 면역의 판정이
	 * 흔들리지 않았는지 여기서 한 번 더 못박는다.
	 */
	@Test
	void 방패_낙하_면역은_그대로다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));
		TeamState state = teamWith("sharedfate:guard");

		assertTrue(ShieldFallImmunityEffect.blocks(state, true, true));
		assertFalse(ShieldFallImmunityEffect.blocks(state, true, false),
				"방패를 들고만 있어서는 안 되고 막는 중이어야 한다");
		assertFalse(ShieldFallImmunityEffect.blocks(state, false, true),
				"낙하가 아닌 피해까지 막으면 무적이 된다");
		assertFalse(PerkDamage.blocksFallDamage(null, null));
	}

	/** 새 배율만 가진 팀에게 낙하 면역이 딸려 오지 않는다. 둘은 서로 다른 효과다. */
	@Test
	void 막을_때_배율은_낙하_면역을_주지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(ShieldFallImmunityEffect.heldBy(teamWith("sharedfate:plain")));
		assertEquals(1.0, PerkDamage.blockingMultiplier(teamWith("sharedfate:plain"), true), 1.0e-9);
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(int index, String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return DamageTakenBlockingEffect.fromJson("sharedfate:테스트", index, parsed);
	}

	private static TeamState teamWith(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:guard", "rarity": "silver", "name": "버티는 방패",
				      "effects": [ { "type": "shield_fall_immunity" } ] },
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
