package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.DiamondSundialEffect;
import com.sharedfate.perk.effect.FlightCharmEffect;
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
 * {@code flight_charm}(프리즘 「비행 부적」)의 정의 읽기와 아이템 표식을 본다.
 *
 * <p>비행을 켜고 끄는 일과 칸 잠금은 살아 있는 서버와 접속한 팀원이 있어야 하므로 여기서
 * 다루지 않는다. 대신 월드를 읽지 않는 조각({@link FlightCharmEffect#fromJson},
 * {@link FlightCharmEffect#isFlightCharm}, {@link FlightCharmEffect#createItem})은 전부 여기서
 * 확인한다. 바닐라 자리를 못박는 일은 {@code FlightCharmTargetTest} 가 맡는다.
 *
 * <p><b>해시계·소집의 조각과 서로를 알아보지 않는지</b>도 여기서 못박는다. 셋은
 * {@code minecraft:custom_data} 의 <b>같은 키</b>에 값만 달리해 표식을 남기므로, 판정이 값을
 * 보지 않고 키만 보게 되는 순간 부적을 우클릭했는데 광석 탐지가 도는 일이 생긴다.
 *
 * <p>{@code PerkEffectType} 을 거치지 않고 {@link FlightCharmEffect#fromJson} 을 직접 부르는
 * 것은, 그 enum 이 이 시험과 따로 손질되기 때문이다. 읽는 규칙 자체는 같은 자리를 지난다.
 */
class FlightCharmEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 값을_적지_않으면_기본값이다() {
		FlightCharmEffect effect = create("{ \"type\": \"flight_charm\" }");

		assertEquals(FlightCharmEffect.DEFAULT_FLIGHT_SECONDS * 20, effect.flightTicks());
		assertEquals(FlightCharmEffect.DEFAULT_COOLDOWN_SECONDS * 20, effect.cooldownTicks());
	}

	@Test
	void 기본값이_설명과_맞는다() {
		assertEquals(10, FlightCharmEffect.DEFAULT_FLIGHT_SECONDS, "비행 시간 기본값은 10초다");
		assertEquals(60, FlightCharmEffect.DEFAULT_COOLDOWN_SECONDS, "쿨타임 기본값은 1분이다");
	}

	@Test
	void 범위가_설명과_맞는다() {
		assertEquals(1, FlightCharmEffect.MIN_FLIGHT_SECONDS);
		assertEquals(60, FlightCharmEffect.MAX_FLIGHT_SECONDS);
		assertEquals(5, FlightCharmEffect.MIN_COOLDOWN_SECONDS);
		assertEquals(600, FlightCharmEffect.MAX_COOLDOWN_SECONDS);
	}

	@Test
	void 적은_값을_그대로_읽는다() {
		FlightCharmEffect effect = create(
				"{ \"type\": \"flight_charm\", \"flight_seconds\": 30, \"cooldown_seconds\": 120 }");

		assertEquals(600, effect.flightTicks());
		assertEquals(2400, effect.cooldownTicks());
	}

	@Test
	void camelCase_로_적어도_같다() {
		FlightCharmEffect effect = create(
				"{ \"type\": \"flight_charm\", \"flightSeconds\": 30, \"cooldownSeconds\": 120 }");

		assertEquals(600, effect.flightTicks());
		assertEquals(2400, effect.cooldownTicks());
	}

	/** 범위를 벗어나도 정의를 버리지 않고 잘라 쓴다. 경고만 남는다. */
	@Test
	void 범위를_벗어난_값은_잘라_쓴다() {
		FlightCharmEffect tooBig = create(
				"{ \"type\": \"flight_charm\", \"flight_seconds\": 999, \"cooldown_seconds\": 99999 }");
		FlightCharmEffect tooSmall = create(
				"{ \"type\": \"flight_charm\", \"flight_seconds\": 0, \"cooldown_seconds\": 0 }");

		assertEquals(FlightCharmEffect.MAX_FLIGHT_SECONDS * 20, tooBig.flightTicks());
		assertEquals(FlightCharmEffect.MAX_COOLDOWN_SECONDS * 20, tooBig.cooldownTicks());
		assertEquals(FlightCharmEffect.MIN_FLIGHT_SECONDS * 20, tooSmall.flightTicks());
		assertEquals(FlightCharmEffect.MIN_COOLDOWN_SECONDS * 20, tooSmall.cooldownTicks());
	}

	@Test
	void 숫자가_아닌_값은_기본값으로_돌아간다() {
		FlightCharmEffect effect = create(
				"{ \"type\": \"flight_charm\", \"flight_seconds\": \"열\", \"cooldown_seconds\": null }");

		assertEquals(FlightCharmEffect.DEFAULT_FLIGHT_SECONDS * 20, effect.flightTicks());
		assertEquals(FlightCharmEffect.DEFAULT_COOLDOWN_SECONDS * 20, effect.cooldownTicks());
	}

	/**
	 * 지급은 {@code PerkGrantChain} 한 곳에서만 한다. {@code apply} 에서 주면 접속할 때마다
	 * 부적이 늘어난다.
	 */
	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		FlightCharmEffect effect = create("{ \"type\": \"flight_charm\" }");

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
		assertEquals(FlightCharmEffect.ITEM, BuiltInRegistries.ITEM.getKey(Items.PHANTOM_MEMBRANE));
	}

	// ------------------------------------------------------------------ 아이템 표식

	@Test
	void 지급한_부적에_표식과_쿨타임_묶음이_붙는다() {
		FlightCharmEffect effect = create("{ \"type\": \"flight_charm\" }");

		ItemStack stack = effect.createItem();

		assertNotNull(stack);
		assertEquals(Items.PHANTOM_MEMBRANE, stack.getItem(),
				"새 아이템을 등록하지 않고 바닐라 팬텀 막을 쓴다");
		assertEquals(1, stack.getCount());
		assertTrue(FlightCharmEffect.isFlightCharm(stack));

		UseCooldown cooldown = stack.get(DataComponents.USE_COOLDOWN);
		assertNotNull(cooldown, "쿨타임 묶음이 있어야 평범한 팬텀 막까지 잠기지 않는다");
		assertEquals(Optional.of(FlightCharmEffect.COOLDOWN_GROUP), cooldown.cooldownGroup());
		assertEquals((float) FlightCharmEffect.DEFAULT_COOLDOWN_SECONDS, cooldown.seconds(), 0.001F);
	}

	/** 쿨타임 묶음은 다른 부적 아이템과 갈라져 있어야 한다. 안 그러면 서로를 잠근다. */
	@Test
	void 쿨타임_묶음이_해시계_소집의_조각과_다르다() {
		assertNotEqualsGroup(FlightCharmEffect.COOLDOWN_GROUP, DiamondSundialEffect.COOLDOWN_GROUP);
		assertNotEqualsGroup(FlightCharmEffect.COOLDOWN_GROUP, RallyShardEffect.COOLDOWN_GROUP);
	}

	@Test
	void 지급할_때마다_새_사본을_준다() {
		FlightCharmEffect effect = create("{ \"type\": \"flight_charm\" }");

		ItemStack first = effect.createItem();
		ItemStack second = effect.createItem();

		assertNotNull(first);
		assertNotNull(second);
		assertNotSame(first, second, "견본을 그대로 넘기면 두 번째 지급이 빈 묶음이 된다");
		first.shrink(1);
		assertEquals(1, second.getCount(), "앞의 묶음을 깎아도 뒤의 묶음은 멀쩡해야 한다");
	}

	@Test
	void 표식이_없는_팬텀_막은_부적이_아니다() {
		assertFalse(FlightCharmEffect.isFlightCharm(new ItemStack(Items.PHANTOM_MEMBRANE)));
	}

	@Test
	void 이름만_바꾼_팬텀_막은_부적이_아니다() {
		ItemStack fake = new ItemStack(Items.PHANTOM_MEMBRANE);
		fake.set(DataComponents.CUSTOM_NAME, Component.literal(FlightCharmEffect.DISPLAY_NAME));

		assertFalse(FlightCharmEffect.isFlightCharm(fake),
				"모루로 이름만 바꿔서는 비행 부적이 만들어지지 않아야 한다");
	}

	@Test
	void 빈_묶음과_null_은_부적이_아니다() {
		assertFalse(FlightCharmEffect.isFlightCharm(ItemStack.EMPTY));
		assertFalse(FlightCharmEffect.isFlightCharm(null));
	}

	/**
	 * 셋은 표식 키가 같고 값만 다르다. 판정이 값을 보지 않게 되는 순간 부적을 우클릭했는데
	 * 광석 탐지가 돌거나 팀원이 끌려오는 일이 생긴다.
	 */
	@Test
	void 해시계_소집의_조각과_서로를_알아보지_않는다() {
		ItemStack charm = create("{ \"type\": \"flight_charm\" }").createItem();
		ItemStack sundial = sundial();
		ItemStack shard = shard();

		assertNotNull(charm);
		assertNotNull(sundial);
		assertNotNull(shard);
		assertTrue(FlightCharmEffect.isFlightCharm(charm));
		assertFalse(DiamondSundialEffect.isSundial(charm), "부적을 해시계로 보면 안 된다");
		assertFalse(RallyShardEffect.isRallyShard(charm), "부적을 소집의 조각으로 보면 안 된다");
		assertFalse(FlightCharmEffect.isFlightCharm(sundial), "해시계를 부적으로 보면 안 된다");
		assertFalse(FlightCharmEffect.isFlightCharm(shard), "소집의 조각을 부적으로 보면 안 된다");
		assertEquals(FlightCharmEffect.MARKER_KEY, DiamondSundialEffect.MARKER_KEY,
				"키가 갈라지면 이 시험의 전제가 사라진다");
		assertEquals(FlightCharmEffect.MARKER_KEY, RallyShardEffect.MARKER_KEY,
				"키가 갈라지면 이 시험의 전제가 사라진다");
	}

	/** 부적이 고정되는 칸은 핫바 맨 왼쪽, 곧 인벤토리 0번이다. */
	@Test
	void 잠그는_칸은_핫바_맨_왼쪽이다() {
		assertEquals(0, FlightCharmEffect.LOCKED_SLOT);
	}

	// ------------------------------------------------------------------ 도우미

	private static FlightCharmEffect create(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return assertInstanceOf(FlightCharmEffect.class,
				FlightCharmEffect.fromJson("sharedfate:테스트", 0, parsed));
	}

	private static ItemStack sundial() {
		JsonObject parsed = JsonParser.parseString("{ \"type\": \"diamond_sundial\" }")
				.getAsJsonObject();
		DiamondSundialEffect effect = assertInstanceOf(DiamondSundialEffect.class,
				DiamondSundialEffect.fromJson("sharedfate:테스트", 0, parsed));
		return effect.createItem();
	}

	private static ItemStack shard() {
		JsonObject parsed = JsonParser.parseString("{ \"type\": \"rally_shard\" }").getAsJsonObject();
		RallyShardEffect effect = assertInstanceOf(RallyShardEffect.class,
				RallyShardEffect.fromJson("sharedfate:테스트", 0, parsed));
		return effect.createItem();
	}

	private static void assertNotEqualsGroup(Object left, Object right) {
		assertFalse(left.equals(right), "쿨타임 묶음이 겹치면 서로의 쿨타임에 함께 잠긴다");
	}
}
