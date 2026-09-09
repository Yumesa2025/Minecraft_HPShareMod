package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DamageTakenBlockingEffect;
import com.sharedfate.perk.effect.WeaponKnockbackEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 실버 「몽둥이찜질」({@code weapon_knockback})을 살아 있는 월드 없이 시험한다.
 *
 * <p>지금 무엇을 들었고 무엇을 때리는지는 살아 있는 월드가 있어야 읽을 수 있다. 그래서 그 값을
 * 읽는 일은 {@link PerkDamage#weaponKnockback} 에 두고, 여기서는 읽어 온 값으로 답을 내는 순수
 * 계산만 본다.
 *
 * <p>정의 읽기는 {@code PerkEffectType} 을 거치지 않고 팩토리를 직접 부른다. 등록 여부는
 * {@code DefaultPerkPoolValuesTest} 가 「기본 정의가 조용히 사라지지 않았는가」로 잡는다.
 */
class WeaponKnockbackEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@BeforeEach
	void 준비() {
		PerkRegistry.clear();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 정의를_그대로_읽는다() {
		WeaponKnockbackEffect effect = knockback("""
				{ "type": "weapon_knockback", "item": "minecraft:stick", "knockback": 10.0,
				  "exclude_players": true }
				""");

		assertEquals(Identifier.withDefaultNamespace("stick"), effect.itemId());
		assertEquals(10.0F, effect.knockback(), 1.0e-6F);
		assertTrue(effect.excludePlayers());
	}

	/**
	 * 바닐라가 같은 자리에서 2 로 나누므로 「넉백 10」의 실제 값은 5 다.
	 *
	 * <p>이 눈금이 흔들리면 정의에 적은 숫자와 실제 세기가 조용히 어긋난다.
	 */
	@Test
	void 넉백_10은_실제로_5를_돌려준다() {
		assertEquals(5.0F, knockback(stick(10.0)).attackKnockback(), 1.0e-6F);
		assertEquals(0.0F, knockback(stick(0.0)).attackKnockback(), 1.0e-6F);
		assertEquals(20.0F, knockback(stick(40.0)).attackKnockback(), 1.0e-6F);
	}

	@Test
	void 아이템이_없으면_버린다() {
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"knockback\": 10.0 }"));
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"item\": \"\" }"));
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"item\": 5 }"));
	}

	/** 이름 모양이 아이디로 읽히지 않으면 버린다. 대문자는 아이디에 쓸 수 없다. */
	@Test
	void 아이템_이름_모양이_틀리면_버린다() {
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"item\": \"minecraft:Stick\" }"));
	}

	/** 모양은 맞지만 이 판에 없는 이름도 버린다. 조용히 아무 일도 안 하는 정의를 남기지 않는다. */
	@Test
	void 이_판에_없는_아이템이면_버린다() {
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"item\": \"minecraft:not_a_real_item\" }"));
		assertNull(raw("{ \"type\": \"weapon_knockback\", \"item\": \"sharedfate:club\" }"));
	}

	@Test
	void 세기가_범위를_벗어나면_자른다() {
		assertEquals(WeaponKnockbackEffect.MIN_KNOCKBACK, knockback(stick(-5.0)).knockback(),
				1.0e-6);
		assertEquals(WeaponKnockbackEffect.MAX_KNOCKBACK, knockback(stick(1000.0)).knockback(),
				1.0e-6);
	}

	/** 세기를 적지 않았거나 숫자가 아니면 기본값을 쓴다. 정의를 버리지는 않는다. */
	@Test
	void 세기가_없으면_기본값이다() {
		assertEquals(WeaponKnockbackEffect.DEFAULT_KNOCKBACK,
				knockback("{ \"type\": \"weapon_knockback\", \"item\": \"minecraft:stick\" }")
						.knockback(), 1.0e-6);
		assertEquals(WeaponKnockbackEffect.DEFAULT_KNOCKBACK, knockback("""
				{ "type": "weapon_knockback", "item": "minecraft:stick", "knockback": "열" }
				""").knockback(), 1.0e-6);
	}

	/** 적지 않으면 플레이어를 뺀다. PvP 에서 사람이 날아가는 쪽이 훨씬 큰 사고다. */
	@Test
	void 플레이어_제외는_기본이_참이다() {
		assertTrue(knockback("{ \"type\": \"weapon_knockback\", \"item\": \"minecraft:stick\" }")
				.excludePlayers());
		assertFalse(knockback("""
				{ "type": "weapon_knockback", "item": "minecraft:stick", "exclude_players": false }
				""").excludePlayers());
		assertFalse(knockback("""
				{ "type": "weapon_knockback", "item": "minecraft:stick", "excludePlayers": false }
				""").excludePlayers());
	}

	// ------------------------------------------------------------------ 아이템 판정

	@Test
	void 적힌_아이템일_때만_걸린다() {
		WeaponKnockbackEffect effect = knockback(stick(10.0));

		assertTrue(effect.matches(new ItemStack(Items.STICK)));
		assertFalse(effect.matches(new ItemStack(Items.DIAMOND_SWORD)));
		assertFalse(effect.matches(ItemStack.EMPTY));
		assertFalse(effect.matches(null));
	}

	// ------------------------------------------------------------------ 대상 판정

	@Test
	void 몹에게는_걸리고_플레이어에게는_안_걸린다() {
		List<PerkEffect> effects = List.of(knockback(stick(10.0)));

		assertEquals(5.0F, WeaponKnockbackEffect.strengthOf(
				effects, new ItemStack(Items.STICK), false), 1.0e-6F);
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthOf(
				effects, new ItemStack(Items.STICK), true), 1.0e-6F,
				"PvP 에서 사람이 날아가면 안 된다");
	}

	/** 플레이어를 빼지 않기로 적은 정의는 사람에게도 걸린다. */
	@Test
	void 플레이어_제외를_끄면_사람에게도_걸린다() {
		List<PerkEffect> effects = List.of(knockback("""
				{ "type": "weapon_knockback", "item": "minecraft:stick", "knockback": 10.0,
				  "exclude_players": false }
				"""));

		assertEquals(5.0F, WeaponKnockbackEffect.strengthOf(
				effects, new ItemStack(Items.STICK), true), 1.0e-6F);
	}

	@Test
	void 다른_것을_들면_걸리지_않는다() {
		List<PerkEffect> effects = List.of(knockback(stick(10.0)));

		assertEquals(-1.0F, WeaponKnockbackEffect.strengthOf(
				effects, new ItemStack(Items.DIAMOND_SWORD), false), 1.0e-6F);
		assertEquals(-1.0F,
				WeaponKnockbackEffect.strengthOf(effects, ItemStack.EMPTY, false), 1.0e-6F);
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthOf(effects, null, false), 1.0e-6F);
	}

	@Test
	void 이_효과가_없는_목록은_음수다() {
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthOf(
				List.of(new DamageTakenBlockingEffect(0.5)), new ItemStack(Items.STICK), false),
				1.0e-6F);
		assertEquals(-1.0F,
				WeaponKnockbackEffect.strengthOf(List.of(), new ItemStack(Items.STICK), false),
				1.0e-6F);
		assertEquals(-1.0F,
				WeaponKnockbackEffect.strengthOf(null, new ItemStack(Items.STICK), false), 1.0e-6F);
	}

	/** 여럿이 걸리면 가장 센 것이 이긴다. */
	@Test
	void 여럿이면_가장_센_것이_이긴다() {
		List<PerkEffect> effects = List.of(knockback(stick(4.0)), knockback(stick(20.0)));

		assertEquals(10.0F, WeaponKnockbackEffect.strengthOf(
				effects, new ItemStack(Items.STICK), false), 1.0e-6F);
	}

	// ------------------------------------------------------------------ 「고른 사람」 가려내기

	@Test
	void 고른_사람만_주인이다() {
		UUID chooser = UUID.randomUUID();
		TeamState state = teamWith("sharedfate:club", chooser);

		assertTrue(WeaponKnockbackEffect.chosenBy(state, "sharedfate:club", chooser));
		assertFalse(WeaponKnockbackEffect.chosenBy(state, "sharedfate:club", UUID.randomUUID()),
				"같은 팀이라도 고른 사람이 아니면 주인이 아니다");
		assertFalse(WeaponKnockbackEffect.chosenBy(null, "sharedfate:club", chooser));
		assertFalse(WeaponKnockbackEffect.chosenBy(state, "sharedfate:club", null));
	}

	/**
	 * 훑을 값어치가 없는 상황은 곧바로 빠져나간다.
	 *
	 * <p>실제로 이 증강을 가진 팀까지 훑는 길은 증강 풀에 {@code weapon_knockback} 정의가 들어와야
	 * 볼 수 있다. 여기서는 훑기 전에 물러나는 조건만 못박는다.
	 */
	@Test
	void 훑을_것이_없으면_곧바로_음수다() {
		UUID chooser = UUID.randomUUID();
		ItemStack stick = new ItemStack(Items.STICK);
		TeamState disabled = teamWith("sharedfate:club", chooser);
		disabled.perksEnabled = false;

		assertEquals(-1.0F, WeaponKnockbackEffect.strengthFor(null, chooser, stick, false), 1.0e-6F);
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthFor(
				teamWith("sharedfate:club", chooser), null, stick, false), 1.0e-6F);
		assertEquals(-1.0F,
				WeaponKnockbackEffect.strengthFor(disabled, chooser, stick, false), 1.0e-6F);
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthFor(
				TeamState.fresh(20.0F), chooser, stick, false), 1.0e-6F);
		assertEquals(-1.0F, WeaponKnockbackEffect.strengthFor(
				teamWith("sharedfate:club", chooser), chooser, ItemStack.EMPTY, false), 1.0e-6F);
	}

	/** 풀에서 사라진 id 는 조용히 건너뛴다. */
	@Test
	void 정의가_없는_id는_건너뛴다() {
		UUID chooser = UUID.randomUUID();

		assertEquals(-1.0F, WeaponKnockbackEffect.strengthFor(
				teamWith("sharedfate:missing", chooser), chooser, new ItemStack(Items.STICK), false),
				1.0e-6F);
	}

	// ------------------------------------------------------------------ 공격 진입점

	/** 때리는 쪽이 팀원이 아니면 아무 일도 없다. 몹끼리의 싸움도 이 자리를 지난다. */
	@Test
	void 읽을_것이_없으면_음수다() {
		assertEquals(-1.0F, PerkDamage.weaponKnockback(null, null), 1.0e-6F);
	}

	// ------------------------------------------------------------------ 도우미

	private static String stick(double strength) {
		return "{ \"type\": \"weapon_knockback\", \"item\": \"minecraft:stick\", \"knockback\": "
				+ strength + " }";
	}

	private static WeaponKnockbackEffect knockback(String json) {
		PerkEffect effect = raw(json);
		assertNotNull(effect, "쓸 수 있는 정의를 버리면 안 된다");
		return assertInstanceOf(WeaponKnockbackEffect.class, effect);
	}

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return WeaponKnockbackEffect.fromJson("sharedfate:테스트", 0, parsed);
	}

	private static TeamState teamWith(String perkId, UUID chooser) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		state.perkOwners.put(perkId, chooser);
		return state;
	}
}
