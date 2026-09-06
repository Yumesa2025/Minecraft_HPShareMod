package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.EchoMiningEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code echo_mining}(골드 「메아리 채굴」)의 정의 읽기와, 「같은 블록만 캔다」·「팀원 발밑은
 * 캐지 않는다」 규칙을 본다.
 *
 * <p>실제로 블록이 사라지고 도구가 추가로 닳고 로드 안 된 청크에서 건너뛰는지는 살아 있는
 * 서버·{@code ServerLevel}·{@code ServerPlayer}가 있어야 확인할 수 있어 여기서 다루지 않는다.
 *
 * <p>다만 <b>발밑 제외만은 반드시 여기서 못박는다.</b> 이 규칙이 깨지면 팀원이 서 있던 칸이
 * 사라져 떨어져 죽고, 이 모드는 체력을 공유하므로 그 사고 하나가 팀 전체를 죽인다. 그래서
 * 판정을 {@link PerkBlockBreaks#isUnderFoot} 라는 좌표 계산만으로 떼어 두고 그 함수를 직접
 * 두들긴다.
 *
 * <p><b>「같은 블록만 캔다」도 여기서 못박는다.</b> 후보를 훑는
 * {@link PerkBlockBreaks#neighborCandidates} 와 기준을 잡는
 * {@link PerkBlockBreaks#kindFilterOf} 가 둘 다 순수 계산이라, 돌 사이의 다이아를 캐는 상황을
 * 서버 없이 그대로 재현할 수 있다.
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

	// ------------------------------------------------------------------ 같은 블록만 캔다

	/**
	 * <b>돌 사이의 다이아를 캐면 돌은 안 캐지고 다이아만 캐진다.</b> 이 증강의 약속이다.
	 *
	 * <p>종류를 안 가리면 붙어 있던 돌 두 칸이 함께 사라진다. 도구 내구도를 2배로 내고 얻는
	 * 것이 돌이고, 정작 이어지는 광석은 그대로 남는다. 이 시험이 깨지면 그 동작으로 돌아간
	 * 것이다.
	 */
	@Test
	void 돌_사이의_다이아를_캐면_돌은_후보에서_빠진다() {
		BlockPos origin = new BlockPos(0, -50, 0);
		BlockState diamond = state(Blocks.DEEPSLATE_DIAMOND_ORE);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.DEEPSLATE));
		// 26칸 중 딱 두 칸만 같은 종류다. 하나는 바로 옆, 하나는 대각선이다.
		BlockPos beside = origin.east();
		BlockPos diagonal = origin.north().above();
		world.put(beside, diamond);
		world.put(diagonal, diamond);

		List<BlockPos> picked = PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, PerkBlockBreaks.kindFilterOf(diamond));

		assertEquals(2, picked.size(), "같은 종류인 두 칸만 남는다 — 돌 24칸은 빠진다");
		assertTrue(picked.contains(beside));
		assertTrue(picked.contains(diagonal), "대각선도 「근처」로 친다 — 광맥은 대각선으로 잇는다");
	}

	@Test
	void 같은_블록이_하나도_없으면_아무것도_더_캐지_않는다() {
		BlockPos origin = new BlockPos(0, -50, 0);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.DEEPSLATE));
		BlockState diamond = state(Blocks.DEEPSLATE_DIAMOND_ORE);

		assertTrue(PerkBlockBreaks.neighborCandidates(
						origin, world::get, ANYTHING_GOES, PerkBlockBreaks.kindFilterOf(diamond))
				.isEmpty(), "돌밭 한복판의 외톨이 광석이면 메아리는 아무 일도 하지 않는다");
	}

	/**
	 * 「같은 블록」의 기준은 {@code Block} 동일성이다. 판정은
	 * {@link com.sharedfate.perk.effect.SameKindMiningEffect#isSameKind} 한 곳에만 있고 메아리도
	 * 그것을 그대로 쓴다. 딥슬레이트 변종은 서로 다른 블록이므로 함께 캐지지 않는다.
	 */
	@Test
	void 딥슬레이트_변종은_같은_블록이_아니다() {
		BlockPos origin = new BlockPos(0, 4, 0);
		BlockState plain = state(Blocks.DIAMOND_ORE);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.STONE));
		world.put(origin.east(), plain);
		world.put(origin.west(), state(Blocks.DEEPSLATE_DIAMOND_ORE));

		List<BlockPos> picked = PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, PerkBlockBreaks.kindFilterOf(plain));

		assertEquals(List.of(origin.east()), picked, "딥슬레이트 쪽은 다른 블록이라 빠진다");
	}

	/**
	 * <b>기준을 못 잡으면 아무것도 캐지 않는다.</b> 여기가 무너지면 예전 동작이 돌아온다.
	 *
	 * <p>{@code null} 은 {@link PerkBlockBreaks#neighborCandidates} 에서 「종류를 안 가림」을
	 * 뜻하므로, 실행부는 {@link PerkBlockBreaks#kindFilterOf} 가 {@code null} 을 준 순간 후보를
	 * 훑지 않고 곧바로 돌아간다.
	 */
	@Test
	void 캔_자리를_모르면_기준이_없다() {
		assertEquals(state(Blocks.DIAMOND_ORE),
				PerkBlockBreaks.kindFilterOf(state(Blocks.DIAMOND_ORE)), "방금 캔 블록이 기준이다");
		assertNull(PerkBlockBreaks.kindFilterOf(state(Blocks.AIR)));
		assertNull(PerkBlockBreaks.kindFilterOf(null));
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

	// ------------------------------------------------------------------ 도우미

	/** 무엇이든 캘 수 있는 자리로 보는 판정. 종류 거르기만 따로 보고 싶을 때 쓴다. */
	private static final BiPredicate<BlockPos, BlockState> ANYTHING_GOES = (pos, state) -> true;

	private static BlockState state(Block block) {
		return block.defaultBlockState();
	}

	/** {@code origin} 을 둘러싼 26칸을 같은 블록으로 채운 가짜 월드. 가운데는 비워 둔다. */
	private static Map<BlockPos, BlockState> filledNeighbors(BlockPos origin, BlockState filler) {
		Map<BlockPos, BlockState> world = new HashMap<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					world.put(origin.offset(dx, dy, dz), filler);
				}
			}
		}
		return world;
	}
}
