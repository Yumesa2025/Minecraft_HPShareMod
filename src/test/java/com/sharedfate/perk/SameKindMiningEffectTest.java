package com.sharedfate.perk;

import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.SameKindMiningEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code same_kind_mining}(세트 「채굴 4단계 — 곡괭이가 알아서」)의 정의 읽기와,
 * 「같은 종류만 캔다」·「연쇄가 연쇄를 부르지 않는다」 규칙을 본다.
 *
 * <p>실제로 블록이 사라지고 도구가 닳는 순간은 살아 있는 서버·{@code ServerLevel}·
 * {@code ServerPlayer} 가 있어야 확인할 수 있어 여기서 다루지 않는다. 대신 그 코드가 부르는
 * 판단 셋을 직접 두들긴다.
 *
 * <ul>
 *   <li>{@link SameKindMiningEffect#isSameKind} — 무엇을 「같은 종류」로 보는가</li>
 *   <li>{@link PerkBlockBreaks#neighborCandidates} — 26칸에서 무엇을 골라내는가.
 *       「메아리 채굴」과 <b>같은 함수를 같은 규칙으로</b> 쓴다</li>
 *   <li>{@link PerkBlockBreaks#kindFilterOf} — 두 효과가 무엇을 기준으로 삼는가</li>
 *   <li>{@link PerkBlockBreaks#beginChain()} — 연쇄가 자기 자신을 다시 부르지 않는가</li>
 * </ul>
 */
class SameKindMiningEffectTest {

	@BeforeAll
	static void setUp() {
		// 블록 레지스트리를 보므로 최소한의 초기화를 해 둔다.
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkSetRegistry.clear();
		PerkBlockBreaks.resetForTesting();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 필드를_안_적으면_두_개를_캐고_내구도가_1_더_닳는다() {
		SameKindMiningEffect effect = sameKind("{ \"type\": \"same_kind_mining\" }");

		assertEquals(2, effect.extraBlocks(), "「메아리 채굴」과 같은 2개다");
		assertEquals(1, effect.extraDurability(), "원래 1 + 추가 1 = 정확히 2배");
		assertEquals(SameKindMiningEffect.DEFAULT_EXTRA_BLOCKS, effect.extraBlocks());
		assertEquals(SameKindMiningEffect.DEFAULT_EXTRA_DURABILITY, effect.extraDurability());
	}

	@Test
	void 개수와_대가를_정의_파일에서_바꿀_수_있다() {
		// 세트 보상 값은 자주 바뀐다. 코드가 아니라 JSON 한 곳에서 조절돼야 한다.
		SameKindMiningEffect effect = sameKind("""
				{ "type": "same_kind_mining", "extra": 4, "extraDurability": 0 }
				""");

		assertEquals(4, effect.extraBlocks());
		assertEquals(0, effect.extraDurability(), "0 이면 대가 없이 캔다");
	}

	@Test
	void 개수가_범위를_벗어나면_버린다() {
		assertNull(create("{ \"type\": \"same_kind_mining\", \"extra\": 0 }"),
				"0 개면 아무 일도 안 일어나는 정의라 오타로 본다");
		assertNull(create("{ \"type\": \"same_kind_mining\", \"extra\": -2 }"));
		assertNull(create("{ \"type\": \"same_kind_mining\", \"extra\": 9 }"),
				"이웃이 26칸뿐이라 그보다 크게 적을 이유가 없다");
	}

	@Test
	void 추가_내구도가_음수거나_지나치면_버린다() {
		assertNull(create("{ \"type\": \"same_kind_mining\", \"extraDurability\": -1 }"));
		assertNull(create("{ \"type\": \"same_kind_mining\", \"extraDurability\": 9999 }"));
	}

	@Test
	void 숫자가_아닌_값은_기본값으로_본다() {
		// readInt 가 숫자가 아닌 값을 기본값으로 되돌린다. 정의 하나가 잘못됐다고 세트 단계가
		// 통째로 사라지는 것보다는 기본값으로 도는 편이 낫다.
		SameKindMiningEffect effect = sameKind("""
				{ "type": "same_kind_mining", "extra": "많이", "extraDurability": "조금" }
				""");

		assertEquals(2, effect.extraBlocks());
		assertEquals(1, effect.extraDurability());
	}

	/**
	 * {@link PerkEffectType} 에 등록되어 있는가.
	 *
	 * <p><b>이 시험이 지키는 것은 조용한 실패다.</b> 등록 줄을 빠뜨리면 빌드도 통과하고 서버도
	 * 뜨는데 채굴 4단계만 아무 일도 하지 않는다.
	 */
	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		PerkEffectType type = PerkEffectType.fromId("same_kind_mining");

		assertNotNull(type, "PerkEffectType 에 SAME_KIND_MINING 줄이 빠져 있다");
		assertInstanceOf(SameKindMiningEffect.class,
				type.create("sharedfate:테스트", 0,
						JsonParser.parseString("{ \"type\": \"same_kind_mining\" }").getAsJsonObject()));
	}

	// ------------------------------------------------------------------ 「같은 종류」의 정의

	/**
	 * <b>블록 상태가 달라도 같은 종류다.</b> 종류({@code Block})만 본다.
	 *
	 * <p>눕혀 놓은 원목과 세워 둔 원목은 {@code BlockState} 가 서로 다르다. 상태까지 맞춰야
	 * 한다고 하면 이 효과는 원목·계단·레드스톤 광석 앞에서 조용히 아무 일도 안 하게 된다.
	 */
	@Test
	void 블록_상태가_달라도_같은_종류다() {
		BlockState upright = Blocks.OAK_LOG.defaultBlockState();
		BlockState sideways = upright.setValue(BlockStateProperties.AXIS, Direction.Axis.X);

		assertNotEquals(upright, sideways, "상태로는 서로 다른 블록이다");
		assertTrue(SameKindMiningEffect.isSameKind(upright, sideways));
		assertTrue(SameKindMiningEffect.isSameKind(sideways, upright), "방향이 없는 판정이다");
	}

	/**
	 * 레드스톤 광석의 {@code lit} 이 이 규칙이 필요한 가장 뚜렷한 예다.
	 *
	 * <p>사람이 밟거나 치기만 해도 {@code lit=true} 로 바뀐다. 상태까지 따지면 광맥 한복판에서
	 * 방금 캔 광석과 바로 옆 광석이 「다른 종류」가 되어 효과가 사라진다.
	 */
	@Test
	void 켜진_레드스톤_광석도_같은_종류다() {
		BlockState dark = Blocks.REDSTONE_ORE.defaultBlockState();
		BlockState lit = dark.setValue(BlockStateProperties.LIT, Boolean.TRUE);

		assertNotEquals(dark, lit);
		assertTrue(SameKindMiningEffect.isSameKind(dark, lit));
	}

	/**
	 * <b>딥슬레이트 변종은 다른 종류다.</b> 정한 규칙이고, 여기서 못박는다.
	 */
	@Test
	void 딥슬레이트_변종은_다른_종류다() {
		assertFalse(SameKindMiningEffect.isSameKind(
				state(Blocks.DIAMOND_ORE), state(Blocks.DEEPSLATE_DIAMOND_ORE)));
		assertFalse(SameKindMiningEffect.isSameKind(
				state(Blocks.REDSTONE_ORE), state(Blocks.DEEPSLATE_REDSTONE_ORE)));
		assertFalse(SameKindMiningEffect.isSameKind(
				state(Blocks.STONE), state(Blocks.DEEPSLATE)));
	}

	@Test
	void 전리품이_같아도_다른_종류다() {
		// 원석 구리 블록과 구리 광석은 같은 것을 떨어뜨리지만 같은 블록이 아니다.
		assertFalse(SameKindMiningEffect.isSameKind(
				state(Blocks.COPPER_ORE), state(Blocks.RAW_COPPER_BLOCK)));
	}

	@Test
	void 빈자리는_어떤_것과도_같은_종류가_아니다() {
		assertFalse(SameKindMiningEffect.isSameKind(null, state(Blocks.STONE)));
		assertFalse(SameKindMiningEffect.isSameKind(state(Blocks.STONE), null));
		assertFalse(SameKindMiningEffect.isSameKind(null, null));
	}

	// ------------------------------------------------------------------ 같은 종류만 캐진다

	/**
	 * <b>돌 사이의 다이아 광석 하나를 캐면 옆의 돌은 안 캐진다.</b> 이 효과의 핵심 약속이다.
	 */
	@Test
	void 돌_사이의_다이아를_캐면_돌은_안_캐진다() {
		BlockPos origin = new BlockPos(0, -50, 0);
		BlockState diamond = state(Blocks.DEEPSLATE_DIAMOND_ORE);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.DEEPSLATE));
		// 26칸 중 딱 두 칸만 같은 종류다. 하나는 바로 옆, 하나는 대각선이다.
		BlockPos beside = origin.east();
		BlockPos diagonal = origin.north().above();
		world.put(beside, diamond);
		world.put(diagonal, diamond);

		List<BlockPos> picked = PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, diamond);

		assertEquals(2, picked.size(), "같은 종류인 두 칸만 남는다");
		assertTrue(picked.contains(beside));
		assertTrue(picked.contains(diagonal), "대각선도 「바로 옆」으로 친다 — 광맥은 대각선으로 잇는다");
	}

	@Test
	void 같은_종류가_하나도_없으면_아무것도_캐지_않는다() {
		BlockPos origin = new BlockPos(0, -50, 0);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.DEEPSLATE));

		assertTrue(PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, state(Blocks.DEEPSLATE_DIAMOND_ORE)).isEmpty());
	}

	@Test
	void 딥슬레이트_변종이_섞인_광맥은_한쪽만_캐진다() {
		BlockPos origin = new BlockPos(0, 4, 0);
		BlockState plain = state(Blocks.DIAMOND_ORE);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.STONE));
		world.put(origin.east(), plain);
		world.put(origin.west(), state(Blocks.DEEPSLATE_DIAMOND_ORE));

		List<BlockPos> picked = PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, plain);

		assertEquals(List.of(origin.east()), picked, "딥슬레이트 쪽은 다른 종류라 빠진다");
	}

	// ------------------------------------------------------------------ 「메아리 채굴」과 같은 길

	/**
	 * <b>기준 블록을 안 넘기면 종류를 안 가린다.</b> 지금 이 갈래로 들어오는 효과는 없다.
	 *
	 * <p>두 효과 모두 방금 캔 블록을 넘기고, {@link PerkBlockBreaks#kindFilterOf} 가
	 * {@code null} 을 주면 부르는 쪽이 아예 돌아간다. 누군가 여기에 {@code null} 을 넘기는 순간
	 * 주변 아무 블록이나 캐지는 예전 동작이 소리 없이 돌아온다.
	 */
	@Test
	void 기준_블록이_없으면_종류를_안_가린다() {
		BlockPos origin = new BlockPos(0, -50, 0);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.DEEPSLATE));
		world.put(origin.east(), state(Blocks.DEEPSLATE_DIAMOND_ORE));

		List<BlockPos> picked = PerkBlockBreaks.neighborCandidates(
				origin, world::get, ANYTHING_GOES, null);

		assertEquals(26, picked.size(), "3×3×3 에서 가운데를 뺀 26칸이 전부 후보다");
		assertFalse(picked.contains(origin), "캔 자리 자신은 후보가 아니다");
	}

	@Test
	void 종류와_상관없는_거르기는_따로_지난다() {
		BlockPos origin = new BlockPos(0, 5, 0);
		BlockState stone = state(Blocks.STONE);
		Map<BlockPos, BlockState> world = filledNeighbors(origin, stone);
		// 로드되지 않은 자리(표에 없음), 공기, 캐면 안 되는 자리를 하나씩 만든다.
		world.remove(origin.above());
		world.put(origin.below(), state(Blocks.AIR));
		BlockPos underFoot = origin.north();

		BiPredicate<BlockPos, BlockState> guarded = (pos, ignored) -> !pos.equals(underFoot);

		assertEquals(23, PerkBlockBreaks.neighborCandidates(origin, world::get, guarded, stone).size(),
				"두 효과가 함께 쓰는 길이다 — 돌밭이라 종류로는 하나도 안 빠지고 셋만 빠진다");
		assertEquals(23, PerkBlockBreaks.neighborCandidates(origin, world::get, guarded, null).size(),
				"발밑·미로드·공기 제외는 종류 거르기와 상관없이 언제나 지난다");
	}

	/**
	 * 두 효과가 <b>같은 기준</b>으로 이웃을 거른다.
	 *
	 * <p>기준을 잡는 자리가 {@link PerkBlockBreaks#kindFilterOf} 한 곳뿐이라, 한쪽만 조용히
	 * 갈라질 자리가 없다. 캔 자리가 이미 공기인 경우를 여기서 못박는 것이 중요하다 — 그때
	 * {@code null} 이 그대로 {@code neighborCandidates} 로 흘러가면 뜻이 뒤집혀 주변 아무
	 * 블록이나 캐진다.
	 */
	@Test
	void 두_효과가_같은_기준_블록을_쓴다() {
		BlockState diamond = state(Blocks.DEEPSLATE_DIAMOND_ORE);

		assertEquals(diamond, PerkBlockBreaks.kindFilterOf(diamond), "방금 캔 블록 그대로다");
		assertNull(PerkBlockBreaks.kindFilterOf(state(Blocks.AIR)),
				"이미 공기인 자리를 기준으로 삼으면 아무것과도 같은 종류가 아니다");
		assertNull(PerkBlockBreaks.kindFilterOf(null));
	}

	@Test
	void 인자가_비면_빈_목록이다() {
		BlockPos origin = BlockPos.ZERO;
		Map<BlockPos, BlockState> world = filledNeighbors(origin, state(Blocks.STONE));

		assertTrue(PerkBlockBreaks.neighborCandidates(null, world::get, ANYTHING_GOES, null).isEmpty());
		assertTrue(PerkBlockBreaks.neighborCandidates(origin, null, ANYTHING_GOES, null).isEmpty());
		assertTrue(PerkBlockBreaks.neighborCandidates(origin, world::get, null, null).isEmpty());
	}

	// ------------------------------------------------------------------ 연쇄 막기

	/**
	 * <b>연쇄가 연쇄를 부르지 않는다.</b> 깨지면 서버가 멈추는 규칙이다.
	 *
	 * <p>「같은 종류」는 이웃이 같은 종류일수록 잘 걸리므로, 고리가 한 번 열리면 광맥 하나가
	 * 아니라 돌밭 전체가 도미노로 무너진다.
	 */
	@Test
	void 연쇄가_연쇄를_부르지_않는다() {
		assertFalse(PerkBlockBreaks.isChaining(), "시작할 때는 꺼져 있다");
		assertTrue(PerkBlockBreaks.beginChain(), "처음 들어오는 것은 통과한다");
		assertTrue(PerkBlockBreaks.isChaining());

		assertFalse(PerkBlockBreaks.beginChain(), "처리 도중의 재진입은 막힌다");
		assertFalse(PerkBlockBreaks.beginChain(), "몇 번을 다시 들어와도 막힌다");
	}

	@Test
	void 한_번_캐고_나면_다시_들어올_수_있다() {
		assertTrue(PerkBlockBreaks.beginChain());
		PerkBlockBreaks.endChain();

		assertFalse(PerkBlockBreaks.isChaining(), "표시가 남아 있으면 그 뒤로 증강이 통째로 죽는다");
		assertTrue(PerkBlockBreaks.beginChain(), "다음 블록은 평소대로 처리된다");
	}

	@Test
	void 다른_스레드는_서로의_표시에_걸리지_않는다() throws InterruptedException {
		assertTrue(PerkBlockBreaks.beginChain());

		boolean[] other = new boolean[1];
		Thread thread = new Thread(() -> other[0] = PerkBlockBreaks.beginChain());
		thread.start();
		thread.join();

		assertTrue(other[0], "전역 플래그였다면 다른 스레드의 증강이 조용히 사라진다");
	}

	// ------------------------------------------------------------------ 세트와 잇기

	/** 세트 정의가 없으면 아무 일도 일어나지 않는다. */
	@Test
	void 세트가_없으면_아무_일도_안_일어난다() {
		TeamState state = team("sharedfate:하나", "sharedfate:둘", "sharedfate:셋", "sharedfate:넷");

		assertTrue(PerkSetRegistry.isEmpty(), "정의를 읽지 않았다");
		assertTrue(PerkSetEffects.activeEffectsOf(state).isEmpty());
		assertTrue(PerkSetEffects.activeEffectsOf(null).isEmpty());
	}

	/** 채굴 증강 네 개를 모으면 4단계가 켜지고 이 효과가 실행부로 넘어간다. */
	@Test
	void 채굴_넷을_모으면_같은_종류_채굴이_켜진다(@TempDir Path dir) throws IOException {
		loadMiningSet(dir);
		TeamState state = team("sharedfate:광부1", "sharedfate:광부2", "sharedfate:광부3",
				"sharedfate:광부4");

		List<PerkEffect> effects = PerkSetEffects.activeEffectsOf(state);

		SameKindMiningEffect effect = effects.stream()
				.filter(SameKindMiningEffect.class::isInstance)
				.map(SameKindMiningEffect.class::cast)
				.findFirst()
				.orElseThrow(() -> new AssertionError("채굴 4단계의 same_kind_mining 이 안 보인다"));
		assertEquals(2, effect.extraBlocks());
	}

	/** 세 개까지는 4단계가 안 켜진다. */
	@Test
	void 채굴이_셋이면_아직_안_켜진다(@TempDir Path dir) throws IOException {
		loadMiningSet(dir);
		TeamState state = team("sharedfate:광부1", "sharedfate:광부2", "sharedfate:광부3");

		assertTrue(PerkSetEffects.activeEffectsOf(state).stream()
						.noneMatch(SameKindMiningEffect.class::isInstance),
				"3개로는 4단계가 켜지면 안 된다");
	}

	// ------------------------------------------------------------------ 도우미

	/** 무엇이든 캘 수 있는 자리로 보는 판정. 종류 거르기만 따로 보고 싶을 때 쓴다. */
	private static final BiPredicate<BlockPos, BlockState> ANYTHING_GOES = (pos, state) -> true;

	private static BlockState state(net.minecraft.world.level.block.Block block) {
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

	private static TeamState team(String... perkIds) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String perkId : perkIds) {
			state.ownedPerks.add(perkId);
		}
		return state;
	}

	private static SameKindMiningEffect sameKind(String json) {
		return assertInstanceOf(SameKindMiningEffect.class, create(json));
	}

	private static PerkEffect create(String json) {
		return SameKindMiningEffect.fromJson(
				"sharedfate:테스트", 0, JsonParser.parseString(json).getAsJsonObject());
	}

	/** 채굴 증강 네 개와 채굴 4단계만 들어 있는 최소 정의를 임시 폴더에 올린다. */
	private static void loadMiningSet(Path dir) throws IOException {
		StringBuilder perks = new StringBuilder("{ \"perks\": [");
		for (int i = 1; i <= 4; i++) {
			perks.append(i == 1 ? "" : ",").append("""
					{ "id": "sharedfate:광부%d", "name": "광부%d", "description": "설명",
					  "rarity": "silver", "set_types": ["mining"],
					  "effects": [ { "type": "mining_speed", "multiplier": 0.9 } ] }
					""".formatted(i, i));
		}
		perks.append("] }");
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), perks, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);

		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), """
				{ "sets": [ { "type": "mining", "tiers": [
				  { "count": 4, "name": "곡괭이가 알아서", "description": "설명",
				    "effects": [ { "type": "same_kind_mining" } ] }
				] } ] }
				""", StandardCharsets.UTF_8);
		PerkSetRegistry.load(dir);
	}
}
