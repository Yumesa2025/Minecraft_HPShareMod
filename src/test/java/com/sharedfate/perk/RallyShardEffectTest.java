package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DiamondSundialEffect;
import com.sharedfate.perk.effect.RallyShardEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseCooldown;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rally_shard}(프리즘 「소집의 조각」)의 정의 읽기와 아이템 표식을 본다.
 *
 * <p>굳히고 끌어오는 일은 살아 있는 서버와 접속한 팀원이 있어야 하므로 여기서 다루지 않는다.
 * 대신 월드를 읽지 않는 조각({@link RallyShardEffect#fromJson},
 * {@link RallyShardEffect#isRallyShard}, {@link RallyShardEffect#createItem})은 전부 여기서
 * 확인한다.
 *
 * <p><b>엑스레이와 서로를 알아보지 않는지</b>도 여기서 못박는다. 둘은
 * {@code minecraft:custom_data} 의 <b>같은 키</b>에 값만 달리해 표식을 남기므로, 판정이 값을
 * 보지 않고 키만 보게 되는 순간 조각을 우클릭했는데 광석 탐지가 도는 일이 생긴다.
 */
class RallyShardEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 값을_적지_않으면_기본값이다() {
		RallyShardEffect effect = create("{ \"type\": \"rally_shard\" }");

		assertEquals(RallyShardEffect.DEFAULT_FREEZE_SECONDS * 20, effect.freezeTicks());
		assertEquals(RallyShardEffect.DEFAULT_COOLDOWN_SECONDS * 20, effect.cooldownTicks());
	}

	@Test
	void 기본값이_설명과_맞는다() {
		assertEquals(3, RallyShardEffect.DEFAULT_FREEZE_SECONDS, "굳는 시간 기본값은 3초다");
		assertEquals(240, RallyShardEffect.DEFAULT_COOLDOWN_SECONDS, "쿨타임 기본값은 4분이다");
	}

	@Test
	void 적은_값을_그대로_읽는다() {
		RallyShardEffect effect = create(
				"{ \"type\": \"rally_shard\", \"freeze_seconds\": 5, \"cooldown_seconds\": 600 }");

		assertEquals(100, effect.freezeTicks());
		assertEquals(12000, effect.cooldownTicks());
	}

	@Test
	void camelCase_로_적어도_같다() {
		RallyShardEffect effect = create(
				"{ \"type\": \"rally_shard\", \"freezeSeconds\": 5, \"cooldownSeconds\": 600 }");

		assertEquals(100, effect.freezeTicks());
		assertEquals(12000, effect.cooldownTicks());
	}

	@Test
	void 범위를_벗어난_값은_잘라_쓴다() {
		RallyShardEffect tooBig = create(
				"{ \"type\": \"rally_shard\", \"freeze_seconds\": 999, \"cooldown_seconds\": 99999 }");
		RallyShardEffect tooSmall = create(
				"{ \"type\": \"rally_shard\", \"freeze_seconds\": 0, \"cooldown_seconds\": 0 }");

		assertEquals(RallyShardEffect.MAX_FREEZE_SECONDS * 20, tooBig.freezeTicks());
		assertEquals(RallyShardEffect.MAX_COOLDOWN_SECONDS * 20, tooBig.cooldownTicks());
		assertEquals(RallyShardEffect.MIN_FREEZE_SECONDS * 20, tooSmall.freezeTicks());
		assertEquals(RallyShardEffect.MIN_COOLDOWN_SECONDS * 20, tooSmall.cooldownTicks());
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		RallyShardEffect effect = create("{ \"type\": \"rally_shard\" }");

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	// ------------------------------------------------------------------ 26.2 에 있는 id 인가

	/**
	 * 아이템 레지스트리는 기본값이 공기라 {@code get} 이 비어 있는지 보는 것으로는 없는 id 를
	 * 잡아내지 못한다. 그래서 반대로 아이템에서 id 를 되뽑아 우리가 적어 둔 문자열과 비교한다.
	 */
	@Test
	void 쓰는_id_가_26_2_의_id_와_같다() {
		assertEquals(RallyShardEffect.ITEM, BuiltInRegistries.ITEM.getKey(Items.ECHO_SHARD));
	}

	// ------------------------------------------------------------------ 아이템 표식

	@Test
	void 지급한_조각에_표식과_쿨타임_묶음이_붙는다() {
		RallyShardEffect effect = create("{ \"type\": \"rally_shard\" }");

		ItemStack stack = effect.createItem();

		assertNotNull(stack);
		assertEquals(Items.ECHO_SHARD, stack.getItem(),
				"새 아이템을 등록하지 않고 바닐라 메아리 조각을 쓴다");
		assertEquals(1, stack.getCount());
		assertTrue(RallyShardEffect.isRallyShard(stack));

		UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
		assertNotNull(cooldown, "쿨타임 묶음이 있어야 평범한 메아리 조각까지 잠기지 않는다");
		assertEquals(Optional.of(RallyShardEffect.COOLDOWN_GROUP), cooldown.cooldownGroup());
		assertEquals((float) RallyShardEffect.DEFAULT_COOLDOWN_SECONDS, cooldown.seconds(), 0.001F);
	}

	@Test
	void 지급할_때마다_새_사본을_준다() {
		RallyShardEffect effect = create("{ \"type\": \"rally_shard\" }");

		ItemStack first = effect.createItem();
		ItemStack second = effect.createItem();

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(first, second, "견본을 그대로 넘기면 두 번째 지급이 빈 묶음이 된다");
		first.shrink(1);
		assertEquals(1, second.getCount(), "앞의 묶음을 깎아도 뒤의 묶음은 멀쩡해야 한다");
	}

	@Test
	void 표식이_없는_메아리_조각은_소집의_조각이_아니다() {
		assertFalse(RallyShardEffect.isRallyShard(new ItemStack(Items.ECHO_SHARD)));
	}

	@Test
	void 이름만_바꾼_조각은_소집의_조각이_아니다() {
		ItemStack fake = new ItemStack(Items.ECHO_SHARD);
		fake.set(DataComponents.CUSTOM_NAME, Component.literal("소집의 조각"));

		assertFalse(RallyShardEffect.isRallyShard(fake),
				"모루로 이름만 바꿔서는 소집의 조각이 만들어지지 않아야 한다");
	}

	@Test
	void 빈_묶음과_null_은_소집의_조각이_아니다() {
		assertFalse(RallyShardEffect.isRallyShard(ItemStack.EMPTY));
		assertFalse(RallyShardEffect.isRallyShard(null));
	}

	/**
	 * 엑스레이와 소집의 조각은 표식 키가 같고 값만 다르다. 판정이 값을 보지 않게 되는 순간
	 * 조각을 우클릭했는데 광석 탐지가 도는 일이 생긴다.
	 */
	@Test
	void 엑스레이와_소집의_조각은_서로를_알아보지_않는다() {
		ItemStack shard = create("{ \"type\": \"rally_shard\" }").createItem();
		ItemStack sundial = sundial();

		assertNotNull(shard);
		assertNotNull(sundial);
		assertTrue(RallyShardEffect.isRallyShard(shard));
		assertFalse(DiamondSundialEffect.isSundial(shard), "조각을 엑스레이로 보면 안 된다");
		assertTrue(DiamondSundialEffect.isSundial(sundial));
		assertFalse(RallyShardEffect.isRallyShard(sundial), "엑스레이를 조각으로 보면 안 된다");
		assertEquals(RallyShardEffect.MARKER_KEY, DiamondSundialEffect.MARKER_KEY,
				"키가 갈라지면 이 시험의 전제가 사라진다");
	}

	// ------------------------------------------------------------------ 도우미

	private static RallyShardEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(RallyShardEffect.class,
				PerkEffectType.RALLY_SHARD.create("sharedfate:테스트", 0, parsed));
	}

	private static ItemStack sundial() {
		JsonObject parsed = JsonParser.parseString("{ \"type\": \"diamond_sundial\" }")
				.getAsJsonObject();
		DiamondSundialEffect effect = assertInstanceOf(DiamondSundialEffect.class,
				PerkEffectType.DIAMOND_SUNDIAL.create("sharedfate:테스트", 0, parsed));
		return effect.createItem();
	}
}
