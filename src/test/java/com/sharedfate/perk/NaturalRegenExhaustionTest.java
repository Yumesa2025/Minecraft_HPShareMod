package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.world.food.FoodData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「고행자」가 자연 회복까지 공짜로 만들지 않는지 본다.
 *
 * <p>{@code no_hunger_drain} 의 약속은 <b>움직여서 생기는</b> 허기 소모를 면제해 주는 것이다.
 * 마인크래프트는 체력을 자연 회복해 줄 때 그 대가로 소모도를 치르게 하는데, 그 대가까지 0 이
 * 되면 체력이 공짜로 무한히 차오른다. 그래서 회복의 대가는 배율을 타지 않고 그대로 지나가야
 * 한다.
 *
 * <h2>26.2 에서 갈림이 어떻게 성립하는가</h2>
 * <p>{@link #자연_회복은_causeFoodExhaustion_을_지나지_않는다} 가 {@code FoodData.tick} 의
 * 바이트코드를 직접 읽어 확인한다. 회복 두 갈래는 {@code player.causeFoodExhaustion(..)} 이
 * 아니라 {@code FoodData} 자신의 {@code addExhaustion(..)} 을 부르므로, 배율이 걸리는 자리
 * ({@code Player.causeFoodExhaustion})를 애초에 지나가지 않는다.
 *
 * <p>그건 <b>우연히</b> 성립한 것이라 다음 버전에서 조용히 무너질 수 있다. 그래서 두 가지를
 * 함께 못 박아 둔다. 위 바이트코드 확인이 통로가 바뀌는 순간 실패로 알려 주고,
 * {@link PerkFoodRules#addNaturalRegenExhaustion} 이 달아 주는 표시는 통로가 바뀌더라도
 * 회복의 대가에 배율이 걸리지 않게 한다.
 */
class NaturalRegenExhaustionTest {
	private static final String FOOD_DATA = "net/minecraft/world/food/FoodData";
	/** 「고행자」가 만드는 배율. 이게 회복의 대가에까지 걸리면 회복이 공짜가 된다. */
	private static final double ASCETIC = 0.0;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 표시가 하는 일

	@Test
	void 자연_회복이_치르는_대가에는_증강_배율을_걸지_않는다() {
		RecordingFoodData food = new RecordingFoodData();

		PerkFoodRules.addNaturalRegenExhaustion(food, 6.0F, null);

		assertEquals(6.0F, food.scaledWhileAdding,
				"회복의 대가가 고행자 배율 0 을 타면 체력이 공짜로 차오른다");
		assertEquals(6.0F, food.received);
	}

	@Test
	void 표시_밖의_소모도에는_그대로_배율이_걸린다() {
		assertEquals(0.0F, PerkFoodRules.applyExhaustionMultiplier(ASCETIC, 6.0F),
				"달리기·채굴로 생긴 소모도는 여전히 막혀야 한다");
		assertEquals(0.1F, PerkFoodRules.applyExhaustionMultiplier(2.0, 0.05F), 1.0e-6F);
	}

	@Test
	void 표시는_끝나면_반드시_돌아온다() {
		PerkFoodRules.addNaturalRegenExhaustion(new FoodData(), 6.0F, null);

		assertEquals(0.0F, PerkFoodRules.applyExhaustionMultiplier(ASCETIC, 6.0F),
				"표시가 남아 있으면 그 뒤의 모든 소모도가 배율을 잃는다");
	}

	@Test
	void 도중에_예외가_나도_표시가_남지_않는다() {
		FoodData exploding = new FoodData() {
			@Override
			public void addExhaustion(float exhaustion) {
				throw new IllegalStateException("다른 모드가 터졌다");
			}
		};

		try {
			PerkFoodRules.addNaturalRegenExhaustion(exploding, 6.0F, null);
		} catch (IllegalStateException expected) {
			// 예외 자체는 우리가 삼킬 일이 아니다. 표시만 남지 않으면 된다.
		}

		assertEquals(0.0F, PerkFoodRules.applyExhaustionMultiplier(ASCETIC, 6.0F));
	}

	@Test
	void null_이_들어와도_표시가_남지_않는다() {
		PerkFoodRules.addNaturalRegenExhaustion(null, 6.0F, null);

		assertEquals(0.0F, PerkFoodRules.applyExhaustionMultiplier(ASCETIC, 6.0F));
	}

	// ------------------------------------------------------------------ includeNaturalRegen

	@Test
	void includeNaturalRegen_이_없으면_자연_회복까지_면제하지_않는다() {
		assertFalse(PerkFoodRules.blocksNaturalRegenExhaustion(null));
		assertFalse(PerkFoodRules.blocksNaturalRegenExhaustion(TeamState.fresh(20.0F)));
	}

	@Test
	void includeNaturalRegen_이_참이면_자연_회복의_대가도_쌓지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:고행자");

		assertTrue(PerkFoodRules.blocksNaturalRegenExhaustion(state));
	}

	@Test
	void includeNaturalRegen_이_거짓인_no_hunger_drain_은_자연_회복을_면제하지_않는다(@TempDir Path dir)
			throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("sharedfate:다른_고행");

		assertFalse(PerkFoodRules.blocksNaturalRegenExhaustion(state),
				"includeNaturalRegen 을 안 적었으면 기본값 거짓이라 자연 회복 대가는 그대로 치러야 한다");
	}

	/** 자연 회복까지 면제하는 고행자와, 행동만 면제하는 다른 no_hunger_drain 을 담은 풀. */
	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:고행자", "rarity": "prism", "name": "고행자",
				      "effects": [
				        { "type": "no_hunger_drain", "includeNaturalRegen": true },
				        { "type": "max_health_lock", "value": 10.0 }
				      ] },
				    { "id": "sharedfate:다른_고행", "rarity": "prism", "name": "다른 고행",
				      "effects": [ { "type": "no_hunger_drain" } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}

	// ------------------------------------------------------------------ 26.2 의 실제 갈래

	/**
	 * 자연 회복의 대가가 배율이 걸리는 자리를 지나지 않는지 확인한다.
	 *
	 * <p>이 단정이 깨지면 「고행자」가 자연 회복까지 공짜로 만든다. 그때는
	 * {@code FoodDataRegenExhaustionMixin} 의 우회 지점을 새 호출에 맞춰 옮겨야 한다.
	 */
	@Test
	void 자연_회복은_causeFoodExhaustion_을_지나지_않는다() throws IOException {
		List<String> calls = invocationsIn("tick");

		assertFalse(calls.stream().anyMatch(call -> call.contains("causeFoodExhaustion")),
				"FoodData.tick 이 causeFoodExhaustion 을 부르기 시작하면 회복의 대가가 증강 배율을 탄다: "
						+ calls);
	}

	/**
	 * {@code FoodDataRegenExhaustionMixin} 의 우회 지점이 실제로 있는지 확인한다.
	 *
	 * <p>mixin 의 {@code target} 문자열은 컴파일러가 봐 주지 않는다. 서명이 바뀌면 서버가 뜰 때
	 * 터지므로, 여기서 먼저 걸리게 해 둔다.
	 */
	@Test
	void 자연_회복은_FoodData_자신의_addExhaustion_으로_대가를_치른다() throws IOException {
		List<String> calls = invocationsIn("tick");

		assertTrue(calls.contains(FOOD_DATA + ".addExhaustion(F)V"),
				"mixin 이 우회할 지점이 사라졌다: " + calls);
		assertEquals(2, calls.stream()
						.filter(call -> call.equals(FOOD_DATA + ".addExhaustion(F)V")).count(),
				"자연 회복 갈래는 둘이고 둘 다 대가를 치러야 한다");
	}

	/**
	 * 소모도를 실제로 쌓는 자리가 {@code FoodData.addExhaustion} 하나뿐인지 확인한다.
	 *
	 * <p>{@code Player.causeFoodExhaustion} 도 결국 여기로 들어온다. 즉 이 메서드는 "행동으로
	 * 생긴 소모도"와 "회복이 치르는 대가"가 합류하는 지점이라, 여기에 배율을 걸면 둘을 구분할
	 * 수 없게 된다. 배율을 {@code causeFoodExhaustion} 쪽에 두는 이유다.
	 */
	@Test
	void 행동으로_생긴_소모도는_causeFoodExhaustion_을_지난다() throws IOException {
		List<String> calls = invocationsIn(
				net.minecraft.world.entity.player.Player.class, "causeFoodExhaustion");

		assertTrue(calls.contains(FOOD_DATA + ".addExhaustion(F)V"),
				"행동 쪽 통로가 바뀌면 배율을 거는 자리도 옮겨야 한다: " + calls);
	}

	// ------------------------------------------------------------------ 도우미

	/** {@code FoodData} 의 메서드 하나가 부르는 것들을 {@code 소유자.이름서명} 으로 늘어놓는다. */
	private static List<String> invocationsIn(String methodName) throws IOException {
		return invocationsIn(FoodData.class, methodName);
	}

	private static List<String> invocationsIn(Class<?> owner, String methodName) throws IOException {
		ClassModel model = ClassFile.of().parse(bytecodeOf(owner));
		List<String> calls = new ArrayList<>();
		for (MethodModel method : model.methods()) {
			if (!method.methodName().stringValue().equals(methodName)) {
				continue;
			}
			for (CodeElement element : method.code().orElseThrow()) {
				if (element instanceof InvokeInstruction invoke) {
					calls.add(invoke.owner().asInternalName()
							+ "." + invoke.name().stringValue()
							+ invoke.type().stringValue());
				}
			}
		}
		assertFalse(calls.isEmpty(), owner.getName() + "." + methodName + " 을 찾지 못했다");
		return calls;
	}

	/** 클래스 파일 원본을 읽는다. 로더가 무엇을 하든 자원 자체는 그대로다. */
	private static byte[] bytecodeOf(Class<?> type) throws IOException {
		String path = type.getName().replace('.', '/') + ".class";
		try (InputStream stream = type.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(stream, path + " 을 읽지 못했다");
			return stream.readAllBytes();
		}
	}

	/** 표시가 걸려 있는 동안 배율이 어떻게 계산되는지 그 자리에서 받아 적는 {@code FoodData}. */
	private static final class RecordingFoodData extends FoodData {
		private float scaledWhileAdding = Float.NaN;
		private float received = Float.NaN;

		@Override
		public void addExhaustion(float exhaustion) {
			received = exhaustion;
			scaledWhileAdding = PerkFoodRules.applyExhaustionMultiplier(ASCETIC, exhaustion);
			super.addExhaustion(exhaustion);
		}
	}
}
