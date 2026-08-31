package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.BonusDropEffect;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.DamageTakenFromEffect;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.perk.effect.ItemGrantEffect;
import com.sharedfate.perk.effect.MaxHealthBonusEffect;
import com.sharedfate.perk.effect.NoDamageBoostEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.OreExchangeEffect;
import com.sharedfate.perk.effect.RarityGrantEffect;
import com.sharedfate.perk.effect.WeaponDamageEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 번들 기본 증강 풀({@code sharedfate-perks-default.json})에 2026-09-01에 고친 값들이
 * 실제로 그 값 그대로 들어갔는지 본다.
 *
 * <p>{@code MaxHealthBonusEffectTest}의 "기본 풀" 절과 같은 방식이다 — JSON 을 다시 읽어
 * 숫자 하나를 눈으로 확인하는 대신, 여기 넣어 두면 다음에 또 조정할 때 회귀를 잡는다.
 */
class DefaultPerkPoolValuesTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 얼음_발은_실제로_미끄러워지는_부호다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:icy_feet");
		AttributeEffect friction = attributeEffect(perk, "minecraft:friction_modifier");

		// clamp(1 - (1-블록마찰) * modifier, 0, 1) 에서 modifier = 1+amount 다.
		// amount 가 양수면 modifier > 1 이 되어 오히려 안 미끄러워진다 — 반드시 음수여야 한다.
		assertTrue(friction.amount() < 0.0, "부호가 양수면 오히려 덜 미끄러워진다");
		assertEquals(-0.5, friction.amount(), 1.0e-9);
	}

	@Test
	void 불굴은_50퍼센트_경계다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:unbroken");
		ConditionalEffect conditional = assertInstanceOf(ConditionalEffect.class, perk.effects().get(0));

		assertEquals(ConditionalEffect.Condition.HEALTH_BELOW, conditional.condition());
		assertEquals(0.5, conditional.threshold(), 1.0e-9);
	}

	@Test
	void 유리_세계는_받는_피해_2점5배다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:glass_world");
		DamageTakenEffect damageTaken = assertInstanceOf(DamageTakenEffect.class, perk.effects().get(1));

		assertEquals(2.5, damageTaken.multiplier(), 1.0e-9);
	}

	@Test
	void 제왕과_신하는_1분마다_바뀌고_대가는_15퍼센트다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:king_and_subjects");
		HolderEffect holder = assertInstanceOf(HolderEffect.class, perk.effects().get(0));

		assertEquals(1200, holder.rotateTicks(), "1분 = 1200틱");
		assertEquals(200, holder.minHoldTicks());
		AttributeEffect penalty = assertInstanceOf(AttributeEffect.class, holder.onOthers().get(0));
		assertEquals(-0.15, penalty.amount(), 1.0e-9);
	}

	@Test
	void 버프_돌리기의_대가에는_실명이_없고_2초다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:rotating_buff");
		HolderEffect holder = assertInstanceOf(HolderEffect.class, perk.effects().get(0));

		assertEquals(2, holder.onPass().size(), "나약함·채굴 피로 둘뿐, 실명은 빠졌다");
		for (OnKillEffect.Grant grant : holder.onPass()) {
			assertEquals(40, grant.durationTicks(), "2초 = 40틱");
		}
	}

	@Test
	void 비상식량은_20개다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:emergency_ration");
		ItemGrantEffect grant = assertInstanceOf(ItemGrantEffect.class, perk.effects().get(0));

		assertEquals(20, grant.entries().get(0).count());
	}

	@Test
	void 손에_쥔_목숨은_토템_3개다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:life_in_hand");
		ItemGrantEffect grant = assertInstanceOf(ItemGrantEffect.class, perk.effects().get(0));

		assertEquals(3, grant.entries().get(0).count());
	}

	@Test
	void 광전사는_최대_체력_10도_준다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:berserker");
		boolean hasBonus = perk.effects().stream().anyMatch(e ->
				e instanceof MaxHealthBonusEffect bonus && bonus.amount() == 10.0F);

		assertTrue(hasBonus, "최대 체력 +10 이 있어야 한다");
	}

	@Test
	void 허공답보는_점프력_30퍼센트도_준다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:void_step");
		AttributeEffect jump = attributeEffect(perk, "minecraft:jump_strength");

		assertEquals(0.3, jump.amount(), 1.0e-9);
	}

	@Test
	void 포식은_허기10_체력8이다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:devour");
		OnKillEffect onKill = assertInstanceOf(OnKillEffect.class, perk.effects().get(0));

		assertEquals(10, onKill.food());
		assertEquals(8.0F, onKill.health());
	}

	@Test
	void 타지_않는_살갗은_화염만_막고_해독제_id가_아니다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:fireproof_skin");
		DamageTakenFromEffect fireImmune =
				assertInstanceOf(DamageTakenFromEffect.class, perk.effects().get(0));

		assertEquals(0.0, fireImmune.multiplier(), 1.0e-9);
		assertFalse(PerkRegistry.byId("sharedfate:antidote").isPresent(), "예전 id는 더 없다");
	}

	@Test
	void 비옥한_땅은_확정_3배다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:fertile_ground");
		BonusDropEffect bonus = assertInstanceOf(BonusDropEffect.class, perk.effects().get(0));

		assertEquals(1.0, bonus.chanceFor(), 1.0e-9);
		assertEquals(2, bonus.extra());
	}

	@Test
	void 삽질의_대가는_다른_무기_공격력을_고정하지_않고_증가_차단을_쓴다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:shovel_master");
		WeaponDamageEffect weapon = assertInstanceOf(WeaponDamageEffect.class, perk.effects().get(0));
		boolean hasBan = perk.effects().stream().anyMatch(e -> e instanceof NoDamageBoostEffect);

		assertNull(weapon.othersDamage(), "이제 다른 무기를 1로 고정하지 않는다");
		assertTrue(hasBan);
	}

	@Test
	void 나무꾼의_욕심은_ore_exchange_하나뿐이다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:woodcutters_greed");

		assertEquals(1, perk.effects().size());
		assertInstanceOf(OreExchangeEffect.class, perk.effects().get(0));
	}

	@Test
	void 숨은_재능과_하늘의_은총이_있다(@TempDir Path dir) throws IOException {
		Perk hidden = perk(dir, "sharedfate:hidden_talent");
		RarityGrantEffect hiddenGrant = assertInstanceOf(RarityGrantEffect.class, hidden.effects().get(0));
		assertEquals(PerkRarity.GOLD, hiddenGrant.rarity());
		assertEquals(PerkRarity.SILVER, hidden.rarity());

		Perk blessing = perk(dir, "sharedfate:blessing_of_heaven");
		RarityGrantEffect blessingGrant =
				assertInstanceOf(RarityGrantEffect.class, blessing.effects().get(0));
		assertEquals(PerkRarity.PRISM, blessingGrant.rarity());
		assertEquals(PerkRarity.GOLD, blessing.rarity());
	}

	// ------------------------------------------------------------------ 도우미

	private static AttributeEffect attributeEffect(Perk perk, String attributeId) {
		return perk.effects().stream()
				.filter(e -> e instanceof AttributeEffect attribute
						&& attribute.attributeId().toString().equals(attributeId))
				.map(AttributeEffect.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError(attributeId + " 효과가 없다: " + perk.id()));
	}

	private static Perk perk(Path dir, String id) throws IOException {
		Path target = dir.resolve(PerkRegistry.FILE_NAME);
		if (!Files.exists(target)) {
			try (InputStream bundled = DefaultPerkPoolValuesTest.class
					.getResourceAsStream("/sharedfate-perks-default.json")) {
				Files.copy(bundled, target);
			}
			PerkRegistry.load(dir);
		}
		return PerkRegistry.byId(id).orElseThrow(() -> new AssertionError(id + " 를 찾을 수 없다"));
	}
}
