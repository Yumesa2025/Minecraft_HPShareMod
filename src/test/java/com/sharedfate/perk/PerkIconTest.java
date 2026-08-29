package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.net.PerkOfferPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 증강 정의의 {@code icon} 이 화면까지 무사히 실려 가는지 본다.
 *
 * <p>아이콘은 있으면 좋은 장식이라, 잘못 적혀 있어도 증강 자체는 살아 있어야 한다.
 * 증강이 통째로 사라지면 플레이어는 후보가 왜 줄었는지 알 길이 없다.
 */
class PerkIconTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 아이콘을_적어_두면_그대로_읽는다(@TempDir Path dir) throws IOException {
		write(dir, perkJson("\"icon\": \"minecraft:feather\","));
		PerkRegistry.load(dir);

		Perk perk = perk();
		assertEquals(Identifier.parse("minecraft:feather"), perk.icon());
	}

	@Test
	void 아이콘이_없으면_null_이고_증강은_살아_있다(@TempDir Path dir) throws IOException {
		write(dir, perkJson(""));
		PerkRegistry.load(dir);

		assertNull(perk().icon(), "아이콘이 없으면 화면이 등급별 기본 아이콘을 쓴다");
	}

	@Test
	void 없는_아이템을_가리키면_아이콘만_버린다(@TempDir Path dir) throws IOException {
		write(dir, perkJson("\"icon\": \"minecraft:없는아이템\","));
		PerkRegistry.load(dir);

		assertNull(perk().icon());
		assertFalse(PerkRegistry.all().isEmpty(), "아이콘이 틀렸다고 증강을 버리면 안 된다");
	}

	@Test
	void 아이템_이름_형식이_아니어도_증강은_남는다(@TempDir Path dir) throws IOException {
		write(dir, perkJson("\"icon\": \"이건 아이템 이름이 아니다\","));
		PerkRegistry.load(dir);

		assertNull(perk().icon());
		assertEquals(1, PerkRegistry.all().size());
	}

	@Test
	void 빈_문자열은_아이콘_없음과_같다(@TempDir Path dir) throws IOException {
		write(dir, perkJson("\"icon\": \"   \","));
		PerkRegistry.load(dir);

		assertNull(perk().icon());
	}

	@Test
	void 화면으로_보낼_때_아이콘_이름이_실린다(@TempDir Path dir) throws IOException {
		write(dir, perkJson("\"icon\": \"minecraft:feather\","));
		PerkRegistry.load(dir);

		List<PerkOfferPayload.PerkOption> options = PerkManager.describeOptions(
				new PendingOffer(15, Optional.empty(), List.of("sharedfate:light_step")));

		assertEquals(1, options.size());
		assertEquals("minecraft:feather", options.getFirst().icon());
		assertEquals("silver", options.getFirst().rarity());
	}

	@Test
	void 아이콘이_없는_증강은_빈_문자열로_나간다(@TempDir Path dir) throws IOException {
		write(dir, perkJson(""));
		PerkRegistry.load(dir);

		List<PerkOfferPayload.PerkOption> options = PerkManager.describeOptions(
				new PendingOffer(15, Optional.empty(), List.of("sharedfate:light_step")));

		assertEquals("", options.getFirst().icon(),
				"빈 문자열을 보고 클라이언트가 등급별 기본 아이콘을 고른다");
	}

	/** {@code iconLine} 자리에 {@code "icon": ...,} 한 줄을 끼워 넣은 증강 정의 하나짜리 파일. */
	private static String perkJson(String iconLine) {
		return """
				{
				  "perks": [
				    {
				      "id": "sharedfate:light_step",
				      "name": "가벼운 발걸음",
				      "description": "발이 가벼워진다",
				      "rarity": "silver",
				      %s
				      "effects": [
				        { "type": "attribute", "attribute": "minecraft:max_health",
				          "operation": "add_value", "amount": 2.0 }
				      ]
				    }
				  ]
				}
				""".formatted(iconLine);
	}

	private static Perk perk() {
		return PerkRegistry.byId("sharedfate:light_step").orElseThrow();
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
