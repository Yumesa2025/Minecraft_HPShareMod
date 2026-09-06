package com.sharedfate.client.hud;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 바이옴 id → 한글 이름 변환.
 *
 * <p>실제로 화면에 그리는 {@code CoordinateHud} 는 살아 있는 클라이언트가 있어야 해서 단위
 * 시험으로 닿지 않는다. 그래서 표를 읽는 부분만 순수 함수로 떼어 냈고, 여기서는 <b>표에 있는
 * id·없는 id·{@code null}</b> 세 갈래가 각각 무엇을 돌려주는지만 확인한다.
 *
 * <p>이 시험은 {@code TestBootstrap} 을 부르지 않는다. {@link Identifier} 와 {@link Biomes} 는
 * 등록표가 아니라 이름표만 만드는 클래스라 게임 부트스트랩 없이도 그대로 쓸 수 있다.
 */
class BiomeNamesTest {

	@Test
	void 표에_있는_id_는_한글_이름이_나온다() {
		assertEquals("평원", BiomeNames.korean(Identifier.parse("minecraft:plains")));
		assertEquals("깊은 어둠", BiomeNames.korean(Identifier.parse("minecraft:deep_dark")));
		// 오버월드만이 아니라 네더·엔드도 표에 있어야 한다.
		assertEquals("영혼 모래 골짜기",
				BiomeNames.korean(Identifier.parse("minecraft:soul_sand_valley")));
		assertEquals("작은 엔드 섬",
				BiomeNames.korean(Identifier.parse("minecraft:small_end_islands")));
		// 어느 차원에도 속하지 않는 공허까지 포함한다.
		assertEquals("공허", BiomeNames.korean(Identifier.parse("minecraft:the_void")));
	}

	/**
	 * 표에 없는 id 는 터지지 않고 path 만 보여 준다.
	 *
	 * <p>다른 모드가 넣은 바이옴, 데이터팩이 만든 바이옴, 다음 판올림에서 늘어난 바이옴이
	 * 여기에 걸린다. HUD 는 매 프레임 돌기 때문에 여기서 예외가 나면 화면 전체가 죽는다.
	 */
	@Test
	void 표에_없는_id_는_path_를_그대로_보여_준다() {
		assertEquals("alpine_grove", BiomeNames.korean(Identifier.parse("terralith:alpine_grove")));
		// namespace 가 minecraft 라도 표에 없으면 마찬가지다.
		assertEquals("made_up_biome",
				BiomeNames.korean(Identifier.parse("minecraft:made_up_biome")));
	}

	/**
	 * 아직 바이옴을 모르는 상황은 빈 문자열이다.
	 *
	 * <p>{@code CoordinateHud} 는 빈 문자열을 「그 줄을 그리지 말라」로 읽는다. 「알 수 없음」
	 * 같은 글자를 대신 적으면 청크가 아직 안 온 한두 프레임 동안 그 글자가 번쩍인다.
	 */
	@Test
	void 바이옴을_모르면_빈_문자열이다() {
		assertEquals("", BiomeNames.korean(null));
	}

	/**
	 * 바닐라 바이옴이 하나도 빠지지 않았는가.
	 *
	 * <p>{@code Biomes} 에 등록된 것을 반사로 훑어 표와 맞춰 본다. 손으로 옮겨 적은 표라
	 * 판올림에서 바이옴이 늘면 조용히 빠지는데, 그러면 그 바이옴에서만 영어 path 가 나오고
	 * 아무도 눈치채지 못한다. 이 시험이 그때 알려 준다.
	 */
	@Test
	void 바닐라_바이옴은_하나도_빠지지_않았다() throws IllegalAccessException {
		List<String> missing = new ArrayList<>();
		int vanillaCount = 0;
		for (Field field : Biomes.class.getFields()) {
			if (!Modifier.isStatic(field.getModifiers())
					|| !ResourceKey.class.isAssignableFrom(field.getType())) {
				continue;
			}
			@SuppressWarnings("unchecked")
			ResourceKey<Biome> key = (ResourceKey<Biome>) field.get(null);
			Identifier id = key.identifier();
			vanillaCount++;
			// path 가 그대로 돌아왔다면 표에서 못 찾았다는 뜻이다.
			if (BiomeNames.korean(id).equals(id.getPath())) {
				missing.add(id.toString());
			}
		}

		assertTrue(vanillaCount > 0, "Biomes 에서 바이옴을 하나도 못 읽었다");
		assertEquals(List.of(), missing, "표에 빠진 바닐라 바이옴");
		assertEquals(vanillaCount, BiomeNames.size(),
				"표에 바닐라에 없는 id 가 섞여 있거나 개수가 어긋난다");
	}

	@Test
	void 표는_예순여섯_개다() {
		assertEquals(66, BiomeNames.size());
	}

	/** 옮겨 적다가 이름을 비워 두거나 영어를 그대로 남기지 않았는지. */
	@Test
	void 표의_이름은_비어_있지_않다() {
		assertFalse(BiomeNames.korean(Identifier.parse("minecraft:plains")).isEmpty());
		assertFalse(BiomeNames.korean(Identifier.parse("minecraft:the_end")).isEmpty());
	}
}
