package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.perk.effect.DamageDealtEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.perk.effect.HolderEffect.Branch;
import com.sharedfate.perk.effect.HolderEffect.HolderMode;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code holder} 의 두 모드 — 강화({@code on_holder_amplified})와 「전원에게」 — 를 검증한다.
 *
 * <p>실제로 플레이어에게 붙였다 떼는 부분은 살아 있는 서버가 있어야 하므로 여기서 다루지
 * 않는다. 대신 그 코드가 내리는 판단을 전부 여기서 확인한다. <b>"어느 묶음을 붙일 것인가"와
 * "그 묶음의 하위 순번이 형제와 겹치지 않는가"</b> 두 가지가 정해지면 {@code applyAs} 는 그
 * 결정대로 갈아 끼우는 일만 한다.
 *
 * <p>{@code HolderEffectTest} 는 이 필드를 모르던 시절의 동작을 지킨다. 여기서는 그 위에
 * 얹히는 것만 본다.
 */
class HolderModeTest {

	@BeforeAll
	static void setUp() {
		// 속성 operation 과 상태이상 레지스트리를 건드리므로 최소 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		// 판정기는 전역 스위치다. 시험끼리 새어 나가면 다른 시험의 보유자 판정까지 바뀐다.
		PerkHolderManager.setModeResolver(null);
		PerkHolderManager.reset();
		ConditionalPerkManager.beginMultiplierLookupForTesting(null);
	}

	// ------------------------------------------------------------------ 도우미

	private static JsonObject json(String raw) {
		return JsonParser.parseString(raw).getAsJsonObject();
	}

	private static HolderEffect parse(String raw) {
		return assertInstanceOf(HolderEffect.class,
				HolderEffect.fromJson("sharedfate:test", 0, json(raw)));
	}

	/** 「가호」의 모양. 고른 사람만 강해지고 나머지는 손해를 본다. */
	private static final String BLESSING = """
			{
			  "type": "holder",
			  "fixed_to_owner": true,
			  "on_holder": [
			    { "type": "attribute", "attribute": "minecraft:block_break_speed",
			      "operation": "add_multiplied_total", "amount": 4.0 }
			  ],
			  "on_holder_amplified": [
			    { "type": "attribute", "attribute": "minecraft:block_break_speed",
			      "operation": "add_multiplied_total", "amount": 5.0 }
			  ],
			  "on_others": [
			    { "type": "attribute", "attribute": "minecraft:block_break_speed",
			      "operation": "add_multiplied_total", "amount": -0.5 }
			  ]
			}
			""";

	// ------------------------------------------------------------------ 모드 판정

	@Test
	void 강화와_전원은_동시에_켜지지_않는다() {
		// 전원 모드가 이기고 강화는 무시된다. 두 단계가 서로를 맞바꾸는 세트라서다.
		assertSame(HolderMode.NORMAL, HolderMode.resolve(false, false));
		assertSame(HolderMode.AMPLIFIED, HolderMode.resolve(true, false));
		assertSame(HolderMode.EVERYONE, HolderMode.resolve(false, true));
		assertSame(HolderMode.EVERYONE, HolderMode.resolve(true, true),
				"둘 다 켜지면 전원 모드가 이긴다. 여기가 깨지면 강화와 전원이 이중으로 걸린다");
	}

	@Test
	void 판정기를_꽂지_않으면_언제나_보통_모드다() {
		// 기존 holder 증강 넷이 이 확장을 모르고도 그대로 돌아야 한다.
		assertSame(HolderMode.NORMAL, PerkHolderManager.modeOf(null, "sharedfate:test"));
		assertSame(HolderMode.NORMAL,
				PerkHolderManager.modeOf(TeamState.fresh(20.0F), "sharedfate:test"));
		assertSame(HolderMode.NORMAL, PerkHolderManager.modeFor(UUID.randomUUID(), null));
	}

	@Test
	void 판정기가_증강별로_모드를_고를_수_있다() {
		// 한 팀이 세트에 속한 holder 와 속하지 않은 holder 를 함께 가질 수 있다. 세트에 속한
		// 것만 강화되지 않으면 「버프 돌리기」까지 덩달아 강해진다.
		PerkHolderManager.setModeResolver((state, perkId) ->
				"sharedfate:blessing".equals(perkId) ? HolderMode.AMPLIFIED : HolderMode.NORMAL);

		assertSame(HolderMode.AMPLIFIED, PerkHolderManager.modeOf(null, "sharedfate:blessing"));
		assertSame(HolderMode.NORMAL, PerkHolderManager.modeOf(null, "sharedfate:rotating_buff"));
	}

	@Test
	void 판정기가_null_을_돌려주면_보통_모드로_본다() {
		PerkHolderManager.setModeResolver((state, perkId) -> null);

		assertSame(HolderMode.NORMAL, PerkHolderManager.modeOf(null, "sharedfate:test"));
	}

	@Test
	void 판정기가_터져도_보유자_처리는_멈추지_않는다() {
		// 서버 틱 한가운데서 불리는 자리다. 밖에서 꽂은 함수의 실수로 전체가 멎으면 안 된다.
		PerkHolderManager.setModeResolver((state, perkId) -> {
			throw new IllegalStateException("판정기가 터졌다");
		});

		assertSame(HolderMode.NORMAL, PerkHolderManager.modeOf(null, "sharedfate:test"));
	}

	// ------------------------------------------------------------------ 어느 묶음을 붙이는가

	@Test
	void 보통_모드는_예전과_같다() {
		assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, HolderMode.NORMAL, true));
		assertSame(Branch.OTHERS, HolderEffect.selectBranch(false, HolderMode.NORMAL, true));
		assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, HolderMode.NORMAL, false));
		assertSame(Branch.OTHERS, HolderEffect.selectBranch(false, HolderMode.NORMAL, false));
	}

	@Test
	void 강화_모드는_보유자만_강화_묶음을_받는다() {
		assertSame(Branch.HOLDER_AMPLIFIED, HolderEffect.selectBranch(true, HolderMode.AMPLIFIED, true));
		assertSame(Branch.OTHERS, HolderEffect.selectBranch(false, HolderMode.AMPLIFIED, true),
				"나머지 팀원은 강화와 무관하게 그대로 손해를 본다");
	}

	@Test
	void 강화_묶음이_없으면_강화_모드여도_보통_묶음을_쓴다() {
		// 이 필드를 적지 않은 기존 증강이 세트에 딸려 들어와도 조용히 아무것도 안 붙는 일이
		// 없어야 한다.
		assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, HolderMode.AMPLIFIED, false));
		assertSame(Branch.OTHERS, HolderEffect.selectBranch(false, HolderMode.AMPLIFIED, false));
	}

	@Test
	void 전원_모드는_모두가_보유자_묶음을_받는다() {
		// 「고른 사람」이라는 구분이 사라진다. on_others 는 아무에게도 붙지 않는다.
		for (boolean hasAmplified : new boolean[] {true, false}) {
			assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, HolderMode.EVERYONE, hasAmplified));
			assertSame(Branch.HOLDER, HolderEffect.selectBranch(false, HolderMode.EVERYONE, hasAmplified),
					"보유자가 아닌 사람도 보유자와 같은 묶음을 받는다");
		}
	}

	@Test
	void 전원_모드는_강화를_쓰지_않는다() {
		// 강화가 사라지는 대신 전원이 받는다. 강화된 값을 전원이 받으면 두 단계의 맞바꿈이
		// 맞바꿈이 아니게 된다.
		assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, HolderMode.EVERYONE, true));
	}

	@Test
	void 모드를_모르면_보통_모드로_본다() {
		assertSame(Branch.HOLDER, HolderEffect.selectBranch(true, null, true));
		assertSame(Branch.OTHERS, HolderEffect.selectBranch(false, null, true));
	}

	@Test
	void 효과가_제_강화_묶음_유무를_보고_고른다() {
		HolderEffect blessing = parse(BLESSING);
		HolderEffect plain = parse("""
				{ "type": "holder", "fixed_to_owner": true,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				""");

		assertSame(Branch.HOLDER_AMPLIFIED, blessing.branchFor(true, HolderMode.AMPLIFIED));
		assertSame(Branch.HOLDER, plain.branchFor(true, HolderMode.AMPLIFIED),
				"강화 묶음이 없는 증강은 강화가 켜져도 예전 그대로다");
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 강화_묶음을_읽는다() {
		HolderEffect effect = parse(BLESSING);

		assertEquals(1, effect.onHolder().size());
		assertEquals(1, effect.onHolderAmplified().size());
		assertEquals(1, effect.onOthers().size());
		assertEquals(4.0,
				assertInstanceOf(AttributeEffect.class, effect.onHolder().getFirst()).amount(), 1.0e-9);
		assertEquals(5.0,
				assertInstanceOf(AttributeEffect.class, effect.onHolderAmplified().getFirst()).amount(),
				1.0e-9);
		assertEquals("sharedfate:test", effect.perkId(),
				"증강 id 를 들고 있어야 세트에 속한 증강만 골라 모드를 켤 수 있다");
	}

	@Test
	void 강화_묶음은_안_적으면_빈_묶음이다() {
		// 기존 holder 증강 넷(버프 돌리기·제왕과 신하·전속 광부·돌아가는 것)이 이 필드 없이
		// 지금과 똑같이 동작해야 한다.
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 1200, "min_hold_ticks": 200, "pass_on_hurt": true,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_others": [] }
				""");

		assertTrue(effect.onHolderAmplified().isEmpty());
		assertEquals("sharedfate:test", effect.perkId(), "정의에서 읽으면 증강 id 가 남는다");
	}

	@Test
	void 강화_묶음만_있어도_받아_준다() {
		// 「강화되기 전에는 아무 일도 없다」는 정의도 뜻이 통한다. 버리면 증강이 풀에서 빠진다.
		HolderEffect effect = parse("""
				{ "type": "holder", "fixed_to_owner": true,
				  "on_holder_amplified": [ { "type": "damage_dealt", "multiplier": 2.0 } ] }
				""");

		assertTrue(effect.onHolder().isEmpty());
		assertEquals(1, effect.onHolderAmplified().size());
	}

	@Test
	void 세_묶음이_모두_비면_버린다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "on_holder": [], "on_holder_amplified": [], "on_others": [] }
				""")));
	}

	@Test
	void 강화_묶음의_하위_효과가_잘못되면_전체를_버린다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder",
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_holder_amplified": [ { "type": "attribute", "operation": "add_value" } ] }
				""")), "attribute 필드가 빠진 하위 효과 하나 때문에 통째로 버려져야 한다");
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder",
				  "on_holder_amplified": [ { "type": "그런_건_없다" } ] }
				""")), "알 수 없는 하위 type 도 마찬가지다");
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder", "on_holder_amplified": "더_강함" }
				""")), "배열이 아니면 버린다");
	}

	@Test
	void 강화_묶음_안에_보유자를_넣을_수_없다() {
		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json("""
				{ "type": "holder",
				  "on_holder_amplified": [
				    { "type": "holder",
				      "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ] }
				  ] }
				""")));
	}

	@Test
	void 강화_묶음에_효과가_너무_많으면_버린다() {
		StringBuilder builder = new StringBuilder("{ \"type\": \"holder\", \"on_holder_amplified\": [");
		for (int i = 0; i < 51; i++) {
			builder.append(i == 0 ? "" : ",").append("{ \"type\": \"damage_dealt\", \"multiplier\": 1.1 }");
		}
		builder.append("] }");

		assertNull(HolderEffect.fromJson("sharedfate:test", 0, json(builder.toString())),
				"순번 파생 규칙이 깨지는 개수는 받지 않는다");
	}

	// ------------------------------------------------------------------ 하위 순번

	@Test
	void 네_묶음의_하위_순번이_서로_겹치지_않는다() {
		// 이 시험이 이 확장에서 가장 중요하다. 속성 수정자 식별자가 증강id + 효과순번 으로
		// 만들어지므로, on_holder 와 on_holder_amplified 의 순번이 겹치면 서로를 덮어쓴다.
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 600,
				  "on_holder": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": 0.5 } ],
				  "on_holder_amplified": [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": 1.0 } ],
				  "on_others": [ { "type": "attribute", "attribute": "minecraft:movement_speed",
				                   "operation": "add_multiplied_total", "amount": -0.2 } ],
				  "on_pass":   [ { "type": "attribute", "attribute": "minecraft:attack_damage",
				                   "operation": "add_multiplied_total", "amount": -0.5, "duration": 5 } ] }
				""");

		AttributeEffect holderSide =
				assertInstanceOf(AttributeEffect.class, effect.onHolder().getFirst());
		AttributeEffect amplifiedSide =
				assertInstanceOf(AttributeEffect.class, effect.onHolderAmplified().getFirst());
		AttributeEffect otherSide =
				assertInstanceOf(AttributeEffect.class, effect.onOthers().getFirst());
		AttributeEffect passSide =
				assertInstanceOf(AttributeEffect.class, effect.onPass().getFirst().effect());

		Set<String> ids = new HashSet<>();
		for (AttributeEffect attribute
				: List.of(holderSide, amplifiedSide, otherSide, passSide)) {
			assertTrue(ids.add(attribute.modifierId().toString()),
					"네 자리의 수정자 식별자가 하나라도 겹치면 서로를 덮어쓴다");
		}

		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 0)),
				holderSide.modifierId(), "on_holder 는 예전 그대로 childIndex 0 부터 센다");
		assertEquals(AttributeEffect.modifierId("sharedfate:test", ConditionalEffect.childIndex(0, 50)),
				otherSide.modifierId(), "on_others 도 예전 그대로 50 부터 센다");
		assertEquals(AttributeEffect.modifierId("sharedfate:test", OnKillEffect.nestedIndex(0, 0)),
				passSide.modifierId(), "on_pass 도 예전 그대로다");
		assertEquals(AttributeEffect.modifierId("sharedfate:test", HolderEffect.amplifiedIndex(0, 0)),
				amplifiedSide.modifierId(), "강화 묶음만 새 구간을 쓴다");
	}

	@Test
	void 강화_순번은_on_pass_구간_안에서_비켜나_있다() {
		// on_pass 는 nestedIndex 의 0 부터 여덟 칸만 쓴다. 강화 묶음은 그 뒤 100 부터 쉰 칸을
		// 쓰므로 한 칸(1000)을 넘지 않고 on_pass 와도 겹치지 않는다.
		for (int parent = 0; parent < 100; parent++) {
			assertEquals(OnKillEffect.nestedIndex(parent, 100), HolderEffect.amplifiedIndex(parent, 0));
			assertTrue(HolderEffect.amplifiedIndex(parent, 0) > OnKillEffect.nestedIndex(parent, 7),
					"on_pass 가 쓸 수 있는 마지막 칸보다 뒤여야 한다");
			assertTrue(HolderEffect.amplifiedIndex(parent, 49) < OnKillEffect.nestedIndex(parent + 1, 0),
					"다음 부모의 칸을 침범하면 안 된다");
			assertNotEquals(ConditionalEffect.childIndex(parent, 0),
					HolderEffect.amplifiedIndex(parent, 0));
		}
	}

	@Test
	void 강화_순번은_묶음_안에서도_하나씩_다르다() {
		assertNotEquals(HolderEffect.amplifiedIndex(0, 0), HolderEffect.amplifiedIndex(0, 1));
		assertEquals(HolderEffect.amplifiedIndex(0, 0) + 1, HolderEffect.amplifiedIndex(0, 1));
	}

	// ------------------------------------------------------------------ 하위 효과 훑기

	@Test
	void 하위_효과에_강화_묶음도_모인다() {
		// 여기가 빠지면 강화 묶음의 상태이상이 증강분으로 인식되지 않아 포션 효과처럼 팀에
		// 공유된다.
		HolderEffect effect = parse(BLESSING);

		assertEquals(3, effect.children().size(), "보유자 1개 + 강화 1개 + 나머지 1개");
		assertTrue(effect.children().containsAll(effect.onHolder()));
		assertTrue(effect.children().containsAll(effect.onHolderAmplified()));
		assertTrue(effect.children().containsAll(effect.onOthers()));
	}

	@Test
	void 강화_묶음이_없으면_하위_효과도_예전_그대로다() {
		HolderEffect effect = parse("""
				{ "type": "holder", "rotate_ticks": 1200,
				  "on_holder": [ { "type": "damage_dealt", "multiplier": 1.5 } ],
				  "on_others": [] }
				""");

		assertEquals(1, effect.children().size());
		assertSame(effect.onHolder(), effect.children(), "묶음이 하나뿐이면 그대로 돌려준다");
	}

	// ------------------------------------------------------------------ 피해 배율

	@Test
	void 배율도_모드를_따른다() {
		HolderEffect effect = new HolderEffect("sharedfate:test", 0, 0, false, true,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(2.0)),
				List.of(new DamageTakenEffect(1.3)),
				List.of());

		assertEquals(1.5, effect.damageDealtMultiplier(true, HolderMode.NORMAL), 1.0e-9);
		assertEquals(2.0, effect.damageDealtMultiplier(true, HolderMode.AMPLIFIED), 1.0e-9,
				"강화가 켜지면 보유자는 강화 배율만 받는다. 두 배율을 곱하면 3.0 이 되어 버린다");
		assertEquals(1.5, effect.damageDealtMultiplier(true, HolderMode.EVERYONE), 1.0e-9,
				"전원 모드에서는 강화가 사라진다");
		assertEquals(1.5, effect.damageDealtMultiplier(false, HolderMode.EVERYONE), 1.0e-9,
				"보유자가 아닌 사람도 보유자 배율을 받는다");

		assertEquals(1.3, effect.damageTakenMultiplier(false, HolderMode.NORMAL), 1.0e-9);
		assertEquals(1.3, effect.damageTakenMultiplier(false, HolderMode.AMPLIFIED), 1.0e-9,
				"나머지 팀원 쪽은 강화와 무관하다");
		assertEquals(1.0, effect.damageTakenMultiplier(false, HolderMode.EVERYONE), 1.0e-9,
				"전원 모드에서는 on_others 가 아무에게도 붙지 않는다");
	}

	@Test
	void 모드를_안_주는_배율은_보통_모드다() {
		// 기존 시험과 기존 호출부가 그대로 돌아야 한다.
		HolderEffect effect = new HolderEffect("sharedfate:test", 0, 0, false, true,
				List.of(new DamageDealtEffect(1.5)),
				List.of(new DamageDealtEffect(2.0)),
				List.of(new DamageDealtEffect(0.7)),
				List.of());

		assertEquals(1.5, effect.damageDealtMultiplier(true), 1.0e-9);
		assertEquals(0.7, effect.damageDealtMultiplier(false), 1.0e-9);
	}

	@Test
	void 강화_묶음이_비면_배율도_보통_묶음을_쓴다() {
		HolderEffect effect = new HolderEffect(0, 0, false,
				List.of(new DamageDealtEffect(1.5)), List.of(), List.of());

		assertEquals(1.5, effect.damageDealtMultiplier(true, HolderMode.AMPLIFIED), 1.0e-9);
	}

	// ------------------------------------------------------------------ 붙여 둔 묶음 기억

	@Test
	void 아직_붙인_적이_없으면_기억도_없다() {
		HolderEffect effect = parse(BLESSING);

		assertNull(effect.appliedBranch(UUID.randomUUID()));
		assertNull(effect.appliedBranch(null));
	}

	@Test
	void 기억을_버리면_다시_모르는_상태가_된다() {
		// 서버가 멈출 때 반드시 버려야 한다. 남겨 두면 다음 회차에서 「이미 붙어 있다」고 잘못
		// 믿어 이전 모드의 효과를 걷어내지 않는다.
		HolderEffect effect = parse(BLESSING);

		effect.forgetAll();

		assertNull(effect.appliedBranch(UUID.randomUUID()));
	}

	@Test
	void 고정_보유자_증강은_넘김_설정을_그대로_무시한다() {
		// 「가호」는 전부 fixed_to_owner 다. 이 확장이 그 규칙을 건드리지 않았는지 못박아 둔다.
		HolderEffect effect = parse(BLESSING);

		assertTrue(effect.fixedToOwner());
		assertEquals(0, effect.rotateTicks());
		assertFalse(effect.passOnHurt());
		assertTrue(effect.onPass().isEmpty());
	}
}
