package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.CompassTargetEffect;
import com.sharedfate.perk.effect.OnKillEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code compass_target} 의 정의 읽기와, 나침반에 성분을 꽂고 걷어내는 부분을 본다.
 *
 * <p>구조물 탐색({@code ChunkGenerator.findNearestMapStructure})은 월드가 생성돼 있어야 하므로
 * 여기서 시험하지 않는다. 대신 그 결과를 받았다고 치고 나침반이 어떻게 바뀌는지
 * ({@link PerkCompassTargets#applyTarget}, {@link PerkCompassTargets#clearTargets})와,
 * 플레이어가 직접 만든 자철석 나침반을 건드리지 않는지를 확인한다. 그 구분이 어긋나면 증강을
 * 잃는 순간 남의 자철석 나침반이 함께 초기화된다.
 */
class CompassTargetEffectTest {

	/** 시험에서 "찾았다고 치는" 요새 자리. */
	private static final GlobalPos FORTRESS =
			GlobalPos.of(Level.NETHER, new BlockPos(216, 64, -88));

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
		PerkCompassTargets.reset();
	}

	// ------------------------------------------------------------------ 정의 읽기

	@Test
	void 구조물_이름과_차원을_읽는다() {
		CompassTargetEffect effect = create("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "minecraft:the_nether", "search_radius": 100 }
				""");

		assertNotNull(effect.structureKey());
		assertEquals(Identifier.parse("minecraft:fortress"), effect.structureKey().identifier());
		assertNull(effect.structureTag(), "이름으로 적으면 태그는 비어 있다");
		assertEquals(Level.NETHER, effect.dimension());
		assertEquals(100, effect.searchRadius());
	}

	@Test
	void 태그로도_적을_수_있다() {
		// 26.2 에 #minecraft:fortress 태그는 없지만, 마을처럼 변종이 여럿인 구조물에는 태그가 있다.
		CompassTargetEffect effect = create("""
				{ "type": "compass_target", "structure": "#minecraft:village",
				  "dimension": "minecraft:overworld" }
				""");

		assertNotNull(effect.structureTag());
		assertEquals(Identifier.parse("minecraft:village"), effect.structureTag().location());
		assertNull(effect.structureKey());
		assertEquals(Level.OVERWORLD, effect.dimension());
	}

	@Test
	void search_radius_를_적지_않으면_100_이다() {
		CompassTargetEffect effect = create("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "minecraft:the_nether" }
				""");

		assertEquals(CompassTargetEffect.DEFAULT_SEARCH_RADIUS, effect.searchRadius());
	}

	@Test
	void structure_나_dimension_이_없으면_버린다() {
		assertNull(raw("{ \"type\": \"compass_target\", \"dimension\": \"minecraft:the_nether\" }"));
		assertNull(raw("{ \"type\": \"compass_target\", \"structure\": \"minecraft:fortress\" }"));
		assertNull(raw("""
				{ "type": "compass_target", "structure": "대문자 안 됨",
				  "dimension": "minecraft:the_nether" }
				"""));
		assertNull(raw("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "이것도 안 됨" }
				"""));
	}

	@Test
	void search_radius_가_범위를_벗어나면_버린다() {
		assertNull(raw("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "minecraft:the_nether", "search_radius": 0 }
				"""));
		assertNull(raw("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "minecraft:the_nether", "search_radius": 100000 }
				"""));
	}

	@Test
	void 하위_효과로는_넣을_수_없다() {
		assertNull(raw("""
				{ "type": "compass_target", "structure": "minecraft:fortress",
				  "dimension": "minecraft:the_nether" }
				""", OnKillEffect.nestedIndex(0, 0)));
	}

	// ------------------------------------------------------------------ 팀 판정

	@Test
	void 증강이_없으면_가리킬_것이_없다() {
		assertNull(PerkCompassTargets.firstTarget(null));
		assertNull(PerkCompassTargets.firstTarget(TeamState.fresh(20.0F)));
	}

	@Test
	void 이_효과를_가진_증강이_있으면_정의를_찾아낸다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(fortressPool(dir));

		CompassTargetEffect effect = PerkCompassTargets.firstTarget(owning("sharedfate:fortress_finder"));

		assertNotNull(effect);
		assertEquals(Level.NETHER, effect.dimension());
	}

	@Test
	void 증강이_꺼져_있으면_찾지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(fortressPool(dir));

		TeamState state = owning("sharedfate:fortress_finder");
		state.perksEnabled = false;

		assertNull(PerkCompassTargets.firstTarget(state));
	}

	// ------------------------------------------------------------------ 우리 것 가려내기

	@Test
	void tracked_가_거짓이고_목표가_있는_성분만_우리_것이다() {
		assertTrue(PerkCompassTargets.isOurs(new LodestoneTracker(Optional.of(FORTRESS), false)));
		// 플레이어가 만든 자철석 나침반.
		assertFalse(PerkCompassTargets.isOurs(new LodestoneTracker(Optional.of(FORTRESS), true)));
		// 자철석이 사라진 뒤 바닐라가 남기는 모양.
		assertFalse(PerkCompassTargets.isOurs(new LodestoneTracker(Optional.empty(), true)));
		assertFalse(PerkCompassTargets.isOurs(null));
	}

	// ------------------------------------------------------------------ 나침반 손보기

	@Test
	void 인벤토리와_왼손의_나침반을_가리키게_한다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.COMPASS));
		state.mainItems.set(5, new ItemStack(Items.STONE, 32));
		state.equipment.set(EquipmentSlot.OFFHAND, new ItemStack(Items.COMPASS));

		assertEquals(2, PerkCompassTargets.applyTarget(state, FORTRESS));

		assertEquals(FORTRESS, target(state.mainItems.get(0)));
		assertEquals(FORTRESS, target(state.equipment.get(EquipmentSlot.OFFHAND)));
		assertNull(state.mainItems.get(5).get(DataComponents.LODESTONE_TRACKER),
				"나침반이 아닌 아이템은 건드리지 않는다");
	}

	@Test
	void 이미_같은_곳을_가리키면_다시_쓰지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.COMPASS));

		assertEquals(1, PerkCompassTargets.applyTarget(state, FORTRESS));
		assertEquals(0, PerkCompassTargets.applyTarget(state, FORTRESS),
				"값이 그대로면 묶음을 건드리지 않아 창 갱신도 일어나지 않는다");
	}

	@Test
	void 증강을_잃으면_우리가_꽂은_것만_걷어낸다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.COMPASS));
		ItemStack lodestone = new ItemStack(Items.COMPASS);
		lodestone.set(DataComponents.LODESTONE_TRACKER,
				new LodestoneTracker(Optional.of(FORTRESS), true));
		state.mainItems.set(1, lodestone);

		PerkCompassTargets.applyTarget(state, FORTRESS);
		assertEquals(1, PerkCompassTargets.clearTargets(state));

		assertNull(state.mainItems.get(0).get(DataComponents.LODESTONE_TRACKER),
				"우리가 꽂은 성분은 걷힌다");
		assertTrue(state.mainItems.get(1).get(DataComponents.LODESTONE_TRACKER).tracked(),
				"플레이어가 만든 자철석 나침반은 그대로 남는다");
	}

	@Test
	void 자철석_나침반은_증강이_빼앗지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		GlobalPos lodestonePos = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 70, 0));
		ItemStack lodestone = new ItemStack(Items.COMPASS);
		lodestone.set(DataComponents.LODESTONE_TRACKER,
				new LodestoneTracker(Optional.of(lodestonePos), true));
		state.mainItems.set(0, lodestone);

		assertEquals(0, PerkCompassTargets.applyTarget(state, FORTRESS));
		assertEquals(lodestonePos, target(state.mainItems.get(0)));
	}

	@Test
	void 걷어낼_것이_없으면_아무것도_바꾸지_않는다() {
		TeamState state = TeamState.fresh(20.0F);
		state.mainItems.set(0, new ItemStack(Items.COMPASS));

		assertEquals(0, PerkCompassTargets.clearTargets(state));
	}

	// ------------------------------------------------------------------ 도우미

	private static GlobalPos target(ItemStack stack) {
		LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
		assertNotNull(tracker);
		return tracker.target().orElseThrow();
	}

	private static TeamState owning(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static CompassTargetEffect create(String json) {
		return assertInstanceOf(CompassTargetEffect.class, raw(json));
	}

	private static PerkEffect raw(String json) {
		return raw(json, 0);
	}

	private static PerkEffect raw(String json, int index) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.COMPASS_TARGET.create("sharedfate:테스트", index, parsed);
	}

	/** 요새 탐지기(compass_target + damage_taken_from) 하나짜리 증강 풀. */
	private static Path fortressPool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:fortress_finder", "rarity": "gold", "name": "요새 탐지기",
				      "effects": [
				        { "type": "compass_target", "structure": "minecraft:fortress",
				          "dimension": "minecraft:the_nether", "search_radius": 100 },
				        { "type": "damage_taken_from", "multiplier": 1.5,
				          "sources": ["#minecraft:is_fire"] }
				      ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
