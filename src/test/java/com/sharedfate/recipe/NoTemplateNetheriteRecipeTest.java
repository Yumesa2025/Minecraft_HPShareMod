package com.sharedfate.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.sharedfate.TestBootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 대장장이 형판 없이도 네더라이트 업그레이드가 되게 하는 레시피 한 벌
 * ({@code data/sharedfate/recipe/netherite_*_no_template.json})을 지킨다.
 *
 * <p>수법은 이렇다. {@code SmithingTransformRecipe} 의 {@code template} 은
 * {@code Codec.optionalFieldOf} 라 아예 적지 않을 수 있고, 그렇게 비워 두면
 * {@code Ingredient.testOptionalIngredient} 가 <b>「그 칸이 비어 있어야 통과」</b>로 읽는다.
 * 그래서 형판을 적지 않은 레시피를 <b>새 id 로 한 벌 더</b> 얹으면 형판을 낀 사람은 바닐라
 * 레시피로, 안 낀 사람은 이 레시피로 걸린다 — 둘이 동시에 맞을 수는 없다.
 *
 * <p>그래서 이 시험이 진짜로 막는 것은 두 가지다.
 * <ol>
 *   <li>새 레시피에 {@code template} 이 슬그머니 들어가는 것 — 들어가면 이 작업 전체가 무의미해진다.</li>
 *   <li>바닐라 원본을 덮어쓰는 것 — 덮어쓰면 형판을 낀 사람이 조용히 실패한다.</li>
 * </ol>
 */
class NoTemplateNetheriteRecipeTest {

	/** 26.2 에 있는 네더라이트 업그레이드 열두 벌. 창과 앵무조개 갑옷이 빠지기 쉽다. */
	private static final List<String> 장비들 = List.of(
			"sword", "axe", "pickaxe", "shovel", "hoe", "spear",
			"helmet", "chestplate", "leggings", "boots",
			"horse_armor", "nautilus_armor");

	private static final String 새_레시피_폴더 = "/data/sharedfate/recipe/";
	private static final String 바닐라_레시피_폴더 = "/data/minecraft/recipe/";

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@Test
	void 형판_없는_레시피가_열두_벌_있다() throws IOException {
		assertEquals(12, 장비들.size(), "목록 자체가 열두 벌이어야 한다");

		for (String 장비 : 장비들) {
			JsonObject 레시피 = 새_레시피(장비);
			assertEquals("minecraft:smithing_transform", 레시피.get("type").getAsString(),
					장비 + " 의 type 이 대장장이 변형이 아니다");
		}
	}

	@Test
	void 새_레시피에는_template_키가_하나도_없다() throws IOException {
		for (String 장비 : 장비들) {
			JsonObject 레시피 = 새_레시피(장비);
			assertFalse(레시피.has("template"),
					장비 + " 에 template 이 적혀 있다 — 이러면 형판이 여전히 필요해서 이 파일을 둘 이유가 없다");
		}
	}

	@Test
	void 재료와_결과가_바닐라_원본과_똑같다() throws IOException {
		for (String 장비 : 장비들) {
			JsonObject 새것 = 새_레시피(장비);
			JsonObject 원본 = 바닐라_레시피(장비);

			assertEquals(원본.get("base"), 새것.get("base"), 장비 + " 의 base 가 원본과 다르다");
			assertEquals(원본.get("addition"), 새것.get("addition"), 장비 + " 의 addition 이 원본과 다르다");
			assertEquals(원본.get("result"), 새것.get("result"), 장비 + " 의 result 가 원본과 다르다");
		}
	}

	/**
	 * 값을 못박아 둔다. 앞 시험이 원본과의 <b>일치</b>만 보기 때문에, 원본을 읽는 경로가
	 * 어긋나 둘 다 엉뚱한 값이 되는 경우를 못 잡는다.
	 */
	@Test
	void 다이아_장비를_네더라이트_재료로_바꾸는_레시피다() throws IOException {
		for (String 장비 : 장비들) {
			JsonObject 레시피 = 새_레시피(장비);

			assertEquals("minecraft:diamond_" + 장비, 레시피.get("base").getAsString());
			assertEquals("minecraft:netherite_" + 장비,
					레시피.getAsJsonObject("result").get("id").getAsString());
			// 26.2 는 도구든 갑옷이든 전부 이 태그 하나를 쓴다 — 갑옷용 태그가 따로 있지 않다.
			assertEquals("#minecraft:netherite_tool_materials", 레시피.get("addition").getAsString());
		}
	}

	/**
	 * 바닐라 원본은 손대지 않았다.
	 *
	 * <p>시험 클래스패스에서는 모드 리소스가 마인크래프트 jar 보다 앞선다. 그래서 누군가
	 * {@code data/minecraft/recipe/} 밑에 같은 이름을 얹어 원본을 덮으면 여기서 읽히는 것이
	 * 그 덮어쓴 파일이 되고, {@code template} 이 사라져 이 시험이 깨진다.
	 */
	@Test
	void 바닐라_원본은_여전히_형판을_요구한다() throws IOException {
		for (String 장비 : 장비들) {
			JsonObject 원본 = 바닐라_레시피(장비);
			assertEquals("minecraft:netherite_upgrade_smithing_template",
					원본.get("template").getAsString(),
					장비 + " 의 바닐라 원본이 형판을 요구하지 않는다 — 원본을 덮어쓴 것 같다");
		}
	}

	/**
	 * 코덱을 실제로 태워 본다. JSON 이 읽히는 것과 게임이 그것을 「형판 칸이 빈 레시피」로
	 * 받아들이는 것은 다른 문제다.
	 *
	 * <p>{@code addition} 은 아이템 태그인데 단위 시험 환경에는 태그가 붙어 있지 않다. 그래서
	 * {@code 레시피로_해석} 이 그 자리만 구체 아이템으로 갈아 끼우고, 이 시험이 걸리는 곳은
	 * 형판 칸과 {@code base} 둘뿐이다.
	 */
	@Test
	void 형판_칸이_비어야만_새_레시피가_걸린다() throws IOException {
		ItemStack 빈칸 = ItemStack.EMPTY;
		ItemStack 형판 = new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);

		for (String 장비 : 장비들) {
			SmithingRecipe 새것 = 레시피로_해석(새_레시피(장비), 장비);
			Optional<Ingredient> 새것의_형판 = 새것.templateIngredient();

			assertTrue(새것의_형판.isEmpty(), 장비 + " 를 읽었더니 형판 재료가 들어 있다");
			assertTrue(Ingredient.testOptionalIngredient(새것의_형판, 빈칸),
					장비 + " 는 형판 칸이 비었을 때 통과해야 한다");
			assertFalse(Ingredient.testOptionalIngredient(새것의_형판, 형판),
					장비 + " 는 형판을 끼우면 탈락해야 한다 — 그래야 바닐라 레시피와 겹치지 않는다");

			// base 는 태그가 아니라 아이템 하나라 태그 없이도 판정된다.
			assertTrue(새것.baseIngredient().test(다이아_장비(장비)),
					장비 + " 의 base 가 다이아몬드 장비를 받지 않는다");
		}
	}

	/** 반대쪽. 바닐라 원본은 형판이 비면 탈락해야 두 레시피가 동시에 걸리지 않는다. */
	@Test
	void 바닐라_원본은_형판이_비면_탈락한다() throws IOException {
		for (String 장비 : 장비들) {
			SmithingRecipe 원본 = 레시피로_해석(바닐라_레시피(장비), 장비 + "(바닐라)");
			Optional<Ingredient> 원본의_형판 = 원본.templateIngredient();

			assertTrue(원본의_형판.isPresent(), 장비 + " 의 바닐라 원본에 형판 재료가 없다");
			assertFalse(Ingredient.testOptionalIngredient(원본의_형판, ItemStack.EMPTY),
					장비 + " 의 바닐라 원본이 빈 형판 칸을 받아 준다");
		}
	}

	/**
	 * 폴더에 열두 벌 말고 다른 것이 끼어 있지 않다.
	 *
	 * <p>파일에서 읽는 경우에만 본다. jar 안이면 폴더를 훑을 수 없어 건너뛴다.
	 */
	@Test
	void 레시피_폴더에는_이_열두_벌뿐이다() throws IOException, URISyntaxException {
		URL 폴더 = getClass().getResource(새_레시피_폴더);
		assertNotNull(폴더, "새 레시피 폴더가 클래스패스에 없다");
		if (!"file".equals(폴더.getProtocol())) {
			return;
		}

		try (Stream<Path> 파일들 = Files.list(Path.of(폴더.toURI()))) {
			List<String> 이름들 = 파일들.map(p -> p.getFileName().toString()).sorted().toList();
			assertEquals(장비들.stream().map(장비 -> "netherite_" + 장비 + "_no_template.json").sorted().toList(),
					이름들, "레시피 폴더 내용이 목록과 다르다");
		}
	}

	// --- 도우미 ---

	private JsonObject 새_레시피(String 장비) throws IOException {
		return 읽기(새_레시피_폴더 + "netherite_" + 장비 + "_no_template.json");
	}

	private JsonObject 바닐라_레시피(String 장비) throws IOException {
		return 읽기(바닐라_레시피_폴더 + "netherite_" + 장비 + "_smithing.json");
	}

	private JsonObject 읽기(String 경로) throws IOException {
		try (InputStream stream = getClass().getResourceAsStream(경로)) {
			assertNotNull(stream, 경로 + " 를 찾을 수 없다");
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
					.getAsJsonObject();
		}
	}

	/**
	 * 레시피 JSON 을 실제 코덱으로 해석한다.
	 *
	 * <p><b>{@code addition} 을 구체 아이템으로 갈아 끼운 뒤에 태운다.</b> 단위 시험 환경에는
	 * 아이템 태그가 묶여 있지 않아({@code MappedRegistry} 가 이미 frozen 이라 뒤늦게 묶을 수도
	 * 없다) {@code #minecraft:netherite_tool_materials} 를 그대로 두면 <b>해석하는 순간</b>
	 * {@code IllegalStateException: Tags not bound} 가 난다. {@code matches} 를 부를 때가 아니라
	 * 코덱을 태울 때 이미 터진다.
	 *
	 * <p>이 시험이 보려는 것은 <b>형판 칸의 성질</b>이지 {@code addition} 이 아니다. 실제 파일의
	 * {@code addition} 이 바닐라와 같은지는 {@code 재료와_결과가_바닐라_원본과_똑같다} 가
	 * 문자열로 지킨다.
	 */
	private static SmithingRecipe 레시피로_해석(JsonElement json, String 이름) {
		JsonObject 태그를_뺀 = json.getAsJsonObject().deepCopy();
		태그를_뺀.addProperty("addition", "minecraft:netherite_ingot");

		RegistryOps<JsonElement> ops = TestBootstrap.registries()
				.createSerializationContext(JsonOps.INSTANCE);
		Recipe<?> recipe = Recipe.CODEC.parse(ops, 태그를_뺀)
				.getOrThrow(message -> new AssertionError(이름 + " 를 레시피로 읽지 못했다: " + message))
				.value();
		assertTrue(recipe instanceof SmithingRecipe, 이름 + " 이 대장장이 레시피가 아니다");
		return (SmithingRecipe) recipe;
	}

	private static ItemStack 다이아_장비(String 장비) {
		Identifier id = Identifier.withDefaultNamespace("diamond_" + 장비);
		Item item = BuiltInRegistries.ITEM.get(id)
				.orElseThrow(() -> new AssertionError(id + " 아이템이 26.2 에 없다"))
				.value();
		return new ItemStack(item);
	}
}
