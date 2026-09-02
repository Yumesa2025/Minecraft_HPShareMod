package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.EchoMiningEffect;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code echo_mining}(골드 「메아리 채굴」)의 정의 읽기와, 「팀원 발밑은 캐지 않는다」 규칙을 본다.
 *
 * <p>실제로 블록이 사라지고 도구가 추가로 닳고 로드 안 된 청크에서 건너뛰는지는 살아 있는
 * 서버·{@code ServerLevel}·{@code ServerPlayer}가 있어야 확인할 수 있어({@code
 * PositionSwapManagerTest}와 같은 이유) 여기서 다루지 않는다.
 *
 * <p>다만 <b>발밑 제외만은 반드시 여기서 못박는다.</b> 이 규칙이 깨지면 팀원이 서 있던 칸이
 * 사라져 떨어져 죽고, 이 모드는 체력을 공유하므로 그 사고 하나가 팀 전체를 죽인다. 그래서
 * 판정을 {@link PerkBlockBreaks#isUnderFoot} 라는 좌표 계산만으로 떼어 두고 그 함수를 직접
 * 두들긴다.
 */
class EchoMiningEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create();
		PerkEffect second = create();

		assertSame(EchoMiningEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.ECHO_MINING, PerkEffectType.fromId("echo_mining"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		EchoMiningEffect effect = assertInstanceOf(EchoMiningEffect.class, create());

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	@Test
	void 덤으로_캐지는_블록은_두_개다() {
		assertEquals(2, EchoMiningEffect.EXTRA_BLOCKS);
	}

	// ------------------------------------------------------------------ 발밑 제외

	@Test
	void 서_있는_칸과_그_아래_한_칸을_발밑으로_친다() {
		// 보통 서 있을 때 딛고 선 것은 아래 칸이지만, 반 블록·계단 위에서는 서 있는 칸 자체가
		// 딛고 선 블록이다. 한쪽만 빼면 나머지 한쪽이 사라져 사람이 떨어진다.
		BlockPos feet = new BlockPos(10, 64, 10);
		List<BlockPos> standing = List.of(feet);

		assertTrue(PerkBlockBreaks.isUnderFoot(feet, standing), "서 있는 칸");
		assertTrue(PerkBlockBreaks.isUnderFoot(feet.below(), standing), "그 아래 한 칸");
	}

	@Test
	void 발밑이_아닌_칸은_캐도_된다() {
		BlockPos feet = new BlockPos(10, 64, 10);
		List<BlockPos> standing = List.of(feet);

		assertFalse(PerkBlockBreaks.isUnderFoot(feet.above(), standing), "머리 위는 상관없다");
		assertFalse(PerkBlockBreaks.isUnderFoot(feet.below().below(), standing),
				"두 칸 아래는 딛고 선 블록이 아니다");
		assertFalse(PerkBlockBreaks.isUnderFoot(feet.north(), standing));
		assertFalse(PerkBlockBreaks.isUnderFoot(feet.east(), standing));
		assertFalse(PerkBlockBreaks.isUnderFoot(new BlockPos(11, 63, 10), standing),
				"대각선 아래도 딛고 선 블록이 아니다");
	}

	@Test
	void 팀원이_여럿이면_모두의_발밑을_뺀다() {
		BlockPos mine = new BlockPos(0, 64, 0);
		BlockPos teammate = new BlockPos(1, 64, 1);
		BlockPos far = new BlockPos(50, 64, 50);
		List<BlockPos> standing = List.of(mine, teammate, far);

		for (BlockPos feet : standing) {
			assertTrue(PerkBlockBreaks.isUnderFoot(feet, standing), feet + " 는 누군가의 발밑이다");
			assertTrue(PerkBlockBreaks.isUnderFoot(feet.below(), standing));
		}
		assertFalse(PerkBlockBreaks.isUnderFoot(new BlockPos(0, 70, 0), standing));
	}

	@Test
	void 지킬_사람이_없으면_아무것도_빼지_않는다() {
		BlockPos anywhere = new BlockPos(3, 5, 7);

		assertFalse(PerkBlockBreaks.isUnderFoot(anywhere, List.of()));
		assertFalse(PerkBlockBreaks.isUnderFoot(anywhere, null));
		assertFalse(PerkBlockBreaks.isUnderFoot(null, List.of(anywhere)));
	}

	@Test
	void 목록에_섞인_null_은_건너뛴다() {
		// 접속이 끊기는 순간의 목록처럼 빈자리가 섞여 들어와도 판정 자체는 계속돼야 한다.
		BlockPos feet = new BlockPos(2, 30, 2);
		List<BlockPos> standing = new ArrayList<>(Arrays.asList(null, feet, null));

		assertTrue(PerkBlockBreaks.isUnderFoot(feet, standing));
		assertFalse(PerkBlockBreaks.isUnderFoot(feet.above(), standing));
	}

	/**
	 * 실제로 후보를 고르는 자리에서 이 규칙이 쓰이는지를 좌표로만 확인한다.
	 *
	 * <p>{@code pickEchoTargets} 는 방금 캔 자리를 둘러싼 26칸을 훑는데, 그 안에 팀원이 서 있는
	 * 일이 <b>흔하다</b> — 발밑을 캐면 바로 그 상황이다. 26칸이 전부 발밑으로 덮이는 배치가
	 * 있는지 좌표만으로 확인해 둔다.
	 */
	@Test
	void 캔_자리_바로_옆에_선_사람은_이웃_칸을_통째로_지킨다() {
		BlockPos origin = new BlockPos(0, 64, 0);
		// 캔 자리 바로 위에 서 있으면 origin 은 그 사람의 발밑(아래 한 칸)이다.
		List<BlockPos> standing = List.of(origin.above());

		assertTrue(PerkBlockBreaks.isUnderFoot(origin, standing),
				"자기가 서 있는 발판을 스스로 캐는 상황이 바로 이것이다");
		assertTrue(PerkBlockBreaks.isUnderFoot(origin.above(), standing));
		assertFalse(PerkBlockBreaks.isUnderFoot(origin.north(), standing),
				"옆 칸까지 지키지는 않는다 — 발밑 두 칸만이다");
	}

	private static PerkEffect create() {
		com.google.gson.JsonObject parsed =
				com.google.gson.JsonParser.parseString("{ \"type\": \"echo_mining\" }").getAsJsonObject();
		return PerkEffectType.ECHO_MINING.create("sharedfate:테스트", 0, parsed);
	}
}
