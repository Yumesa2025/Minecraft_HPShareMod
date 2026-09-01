package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.AttributeEffect;
import com.sharedfate.perk.effect.BonusDropEffect;
import com.sharedfate.perk.effect.ConditionalEffect;
import com.sharedfate.perk.effect.DamageTakenEffect;
import com.sharedfate.perk.effect.DamageTakenFromEffect;
import com.sharedfate.perk.effect.DoubleJumpEffect;
import com.sharedfate.perk.effect.HolderEffect;
import com.sharedfate.perk.effect.ItemGrantEffect;
import com.sharedfate.perk.effect.MaxHealthBonusEffect;
import com.sharedfate.perk.effect.NoDamageBoostEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.perk.effect.OreExchangeEffect;
import com.sharedfate.perk.effect.RarityGrantEffect;
import com.sharedfate.perk.effect.WeaponDamageEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
		//
		// amount=-1.0 은 modifier=0 을 만들어 어느 블록에서든 결과가 clamp 상한인 1 로
		// 못박힌다 — "2배 더 미끄럽게" 요청에 맞춰 -0.5(보통 땅 0.6→0.8, 기준보다 +0.2)에서
		// 미끄러움 증가분(1-modifier)을 두 배로 올린 값이자, 이 공식이 낼 수 있는 최댓값이다.
		assertTrue(friction.amount() < 0.0, "부호가 양수면 오히려 덜 미끄러워진다");
		assertEquals(-1.0, friction.amount(), 1.0e-9);
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
	void 허공답보는_점프력_50퍼센트도_준다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:void_step");
		AttributeEffect jump = attributeEffect(perk, "minecraft:jump_strength");

		assertEquals(0.5, jump.amount(), 1.0e-9);
	}

	/**
	 * 두 번째 점프는 바닐라 기본 점프의 1.7배다.
	 *
	 * <p>{@code LivingEntity.BASE_JUMP_POWER} 가 0.42 이므로 0.42 × 1.7 = 0.714 다. 첫 점프에
	 * 붙는 점프력 +50%(0.63)와 값을 맞추던 예전과 달리, 이제 <b>두 번째가 첫 번째보다 세다.</b>
	 */
	@Test
	void 허공답보의_두_번째_점프는_기본의_1_7배다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:void_step");
		DoubleJumpEffect jump = perk.effects().stream()
				.filter(DoubleJumpEffect.class::isInstance)
				.map(DoubleJumpEffect.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError("double_jump 효과가 없다"));

		assertEquals(0.42 * 1.7, jump.power(), 1.0e-9);
		assertEquals(DoubleJumpEffect.DEFAULT_POWER, jump.power(), 1.0e-9,
				"power 를 적지 않았을 때의 기본값도 같아야 한다");
	}

	/**
	 * 낙하 피해는 1.5배다.
	 *
	 * <p>{@code add_multiplied_total} 이라 최종 배율은 {@code 1 + amount} 다. 1.5배를 만들려면
	 * {@code amount} 가 0.5 여야 한다.
	 */
	@Test
	void 허공답보의_낙하_피해는_1_5배다(@TempDir Path dir) throws IOException {
		Perk perk = perk(dir, "sharedfate:void_step");
		AttributeEffect fall = attributeEffect(perk, "minecraft:fall_damage_multiplier");

		assertEquals(0.5, fall.amount(), 1.0e-9);
	}

	@Test
	void 굴착기는_속성으로_3배_빨라지고_공격력_15퍼센트를_잃는다(@TempDir Path dir) throws IOException {
		// 예전에는 mining_speed ×3 이었는데 그 길로는 절대 빨라지지 않는다 — 그 타입은 서버에서만
		// 계산되고, 26.2 에서 블록이 부서지는 시점은 클라이언트의 STOP_DESTROY_BLOCK 이 정한다.
		// 그래서 이득이 0인 채로 광석 페널티만 물고 있었다(2026-09-02 확인).
		// block_break_speed 는 setSyncable(true) 라 클라이언트까지 내려가 양쪽이 같은 값을 본다.
		// 자세한 것은 PlayerMiningSpeedMixin 과 MiningSpeedEffect 의 클래스 주석에 있다.
		Perk perk = perk(dir, "sharedfate:excavator");
		AttributeEffect speed = attributeEffect(perk, "minecraft:block_break_speed");
		AttributeEffect damage = attributeEffect(perk, "minecraft:attack_damage");

		// 기본값 1.0 에 add_multiplied_total 이라 1.0 × (1 + 2.0) = 3.0 이다.
		assertEquals(2.0, speed.amount(), 1.0e-9);
		assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, speed.operation());
		assertEquals(-0.15, damage.amount(), 1.0e-9);
		assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, damage.operation());

		// 서버 전용 경로에는 아무것도 남기지 않는다. 남아 있으면 그만큼이 다시 먹히지 않는 효과다.
		com.sharedfate.team.TeamState state = com.sharedfate.team.TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:excavator");
		assertEquals(1.0,
				PerkBlockBreaks.multiplierFor(state,
						net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),
				1.0e-6, "굴착기에 mining_speed 효과가 남아 있으면 안 된다");
		assertEquals(1.0,
				PerkBlockBreaks.multiplierFor(state,
						net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS.defaultBlockState()),
				1.0e-6, "광석 예외는 사라졌다");
	}

	@Test
	void 기본_풀에는_빨라지는_mining_speed_가_하나도_없다(@TempDir Path dir) throws IOException {
		// mining_speed 는 느리게 하는 데만 쓸 수 있다. 1 보다 큰 배율은 조용히 아무 일도 하지
		// 않으므로, 새 증강이 그 함정에 다시 빠지면 여기서 걸린다. 빠르게 하려면
		// minecraft:block_break_speed 속성을 써야 한다.
		loadDefaultPool(dir);
		for (Perk perk : PerkRegistry.all()) {
			for (PerkEffect effect : perk.effects()) {
				if (effect instanceof com.sharedfate.perk.effect.MiningSpeedEffect mining) {
					assertTrue(mining.multiplier() <= 1.0,
							perk.name() + " 의 mining_speed 가 " + mining.multiplier()
									+ " 다. 빨라지는 쪽은 효과가 없으니 block_break_speed 속성을 쓸 것");
				}
			}
		}
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

	/**
	 * 두 증강의 대가는 <b>받는 피해 배율 하나뿐</b>이다.
	 *
	 * <p>예전에는 최대 체력을 깎았다(숨은 재능 −4, 하늘의 은총 −8). 최대 체력은 팀 전체가
	 * 나눠 쓰는 값이라 나 하나가 증강 두 장을 겹쳐 고르면 팀의 목숨이 통째로 줄고, 그 손해가
	 * 증강을 고르지 않은 사람에게도 그대로 간다. 대가는 받는 피해 배율로 옮겼다 — 이쪽도
	 * 팀 전체에 걸리지만 <b>맞았을 때만</b> 드러나므로, 조심하면 줄일 수 있다는 점이 다르다.
	 */
	@Test
	void 숨은_재능과_하늘의_은총의_대가는_받는_피해뿐이다(@TempDir Path dir) throws IOException {
		Perk hidden = perk(dir, "sharedfate:hidden_talent");
		assertEquals(2, hidden.effects().size(), "지급 하나 + 대가 하나");
		assertEquals(1.15,
				assertInstanceOf(DamageTakenEffect.class, hidden.effects().get(1)).multiplier(),
				1.0e-9);
		assertTrue(hidden.effects().stream().noneMatch(e -> e instanceof MaxHealthBonusEffect),
				"최대 체력을 깎지 않는다");

		Perk blessing = perk(dir, "sharedfate:blessing_of_heaven");
		assertEquals(2, blessing.effects().size(), "지급 하나 + 대가 하나");
		assertEquals(1.25,
				assertInstanceOf(DamageTakenEffect.class, blessing.effects().get(1)).multiplier(),
				1.0e-9);
		assertTrue(blessing.effects().stream().noneMatch(e -> e instanceof MaxHealthBonusEffect),
				"최대 체력을 깎지 않는다");
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

	/** 번들 기본 풀을 임시 폴더에 풀어 레지스트리에 올린다. 이미 올렸으면 아무 일도 하지 않는다. */
	private static void loadDefaultPool(Path dir) throws IOException {
		Path target = dir.resolve(PerkRegistry.FILE_NAME);
		if (Files.exists(target)) {
			return;
		}
		try (InputStream bundled = DefaultPerkPoolValuesTest.class
				.getResourceAsStream("/sharedfate-perks-default.json")) {
			Files.copy(bundled, target);
		}
		PerkRegistry.load(dir);
	}

	private static Perk perk(Path dir, String id) throws IOException {
		loadDefaultPool(dir);
		return PerkRegistry.byId(id).orElseThrow(() -> new AssertionError(id + " 를 찾을 수 없다"));
	}
}
