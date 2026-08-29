package com.sharedfate.perk;

import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.net.PerkClientFeaturesPayload;
import com.sharedfate.perk.effect.DoubleJumpEffect;
import com.sharedfate.perk.effect.HideHudEffect;
import com.sharedfate.team.TeamState;
import net.minecraft.core.registries.BuiltInRegistries;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 클라이언트가 있어야 성립하는 증강 두 가지({@code double_jump} / {@code hide_hud})가
 * 정의를 제대로 읽고, 서버가 그것을 패킷 하나로 옳게 접어 내는지 본다.
 *
 * <p>실제로 뛰어오르거나 HUD 를 지우는 부분은 살아 있는 클라이언트가 있어야 확인할 수 있다.
 * 여기서는 그 앞단, 즉 "무엇을 켤 것인가"를 정하는 계산까지만 확인한다.
 */
class ClientPerkEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ double_jump 정의

	@Test
	void 세기를_적지_않으면_바닐라_점프와_같은_세기다() {
		PerkEffect effect = create("double_jump", "{ \"type\": \"double_jump\" }");

		assertInstanceOf(DoubleJumpEffect.class, effect);
		assertEquals(DoubleJumpEffect.DEFAULT_POWER, ((DoubleJumpEffect) effect).power());
	}

	@Test
	void 적어_둔_세기를_읽는다() {
		PerkEffect effect = create("double_jump", "{ \"type\": \"double_jump\", \"power\": 0.8 }");

		assertEquals(0.8, ((DoubleJumpEffect) effect).power());
	}

	@Test
	void 세기가_범위를_벗어나면_정의를_버린다() {
		// 너무 약하면 뛴 티가 안 나고, 너무 세면 낙하 피해로 죽는다. 값을 몰래 깎아 주기보다
		// 증강 하나가 통째로 빠지는 편이 알아채기 쉽다.
		assertNull(create("double_jump", "{ \"type\": \"double_jump\", \"power\": 0.05 }"));
		assertNull(create("double_jump", "{ \"type\": \"double_jump\", \"power\": 2.5 }"));
		assertNull(create("double_jump", "{ \"type\": \"double_jump\", \"power\": -0.42 }"));
	}

	@Test
	void 경계값은_받아들인다() {
		assertEquals(DoubleJumpEffect.MIN_POWER,
				((DoubleJumpEffect) create("double_jump",
						"{ \"type\": \"double_jump\", \"power\": 0.1 }")).power());
		assertEquals(DoubleJumpEffect.MAX_POWER,
				((DoubleJumpEffect) create("double_jump",
						"{ \"type\": \"double_jump\", \"power\": 2.0 }")).power());
	}

	// ------------------------------------------------------------------ hide_hud 정의

	@Test
	void 가릴_칸들을_읽는다() {
		PerkEffect effect = create("hide_hud",
				"{ \"type\": \"hide_hud\", \"elements\": [\"health\", \"food\"] }");

		assertInstanceOf(HideHudEffect.class, effect);
		HideHudEffect hide = (HideHudEffect) effect;
		assertTrue(hide.hides(HideHudEffect.Element.HEALTH));
		assertTrue(hide.hides(HideHudEffect.Element.FOOD));
		assertFalse(hide.hides(HideHudEffect.Element.ARMOR));
		assertFalse(hide.hides(HideHudEffect.Element.AIR));
	}

	@Test
	void 모르는_칸_이름은_건너뛰고_나머지는_살린다() {
		HideHudEffect hide = (HideHudEffect) create("hide_hud",
				"{ \"type\": \"hide_hud\", \"elements\": [\"health\", \"경험치\"] }");

		assertEquals(1, hide.elements().size());
		assertTrue(hide.hides(HideHudEffect.Element.HEALTH));
	}

	@Test
	void 남은_칸이_하나도_없으면_정의를_버린다() {
		assertNull(create("hide_hud", "{ \"type\": \"hide_hud\" }"));
		assertNull(create("hide_hud", "{ \"type\": \"hide_hud\", \"elements\": [] }"));
		assertNull(create("hide_hud", "{ \"type\": \"hide_hud\", \"elements\": [\"경험치\"] }"));
	}

	@Test
	void 칸_이름은_대소문자와_공백을_가리지_않는다() {
		HideHudEffect hide = (HideHudEffect) create("hide_hud",
				"{ \"type\": \"hide_hud\", \"elements\": [\" HEALTH \", \"Air\"] }");

		assertTrue(hide.hides(HideHudEffect.Element.HEALTH));
		assertTrue(hide.hides(HideHudEffect.Element.AIR));
	}

	// ------------------------------------------------------------------ 보유 기능 계산

	@Test
	void 증강이_없으면_클라이언트가_할_일도_없다() {
		assertEquals(PerkClientFeaturesPayload.NONE, PerkClientRules.featuresOf((TeamState) null));
		assertEquals(PerkClientFeaturesPayload.NONE,
				PerkClientRules.featuresOf(TeamState.fresh(20.0F)));
	}

	@Test
	void 증강을_끈_팀은_가진_것이_있어도_켜지지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = false;
		state.ownedPerks.add("sharedfate:허공답보");

		assertEquals(PerkClientFeaturesPayload.NONE, PerkClientRules.featuresOf(state));
	}

	@Test
	void 공중_점프_증강을_가지면_세기까지_실린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		PerkClientFeaturesPayload features = PerkClientRules.featuresOf(perkTeam("sharedfate:허공답보"));

		assertTrue(features.doubleJump());
		assertEquals(0.42, features.doubleJumpPower());
		assertTrue(features.hiddenHudElements().isEmpty());
	}

	@Test
	void 가림_증강을_가지면_칸_이름이_실린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		PerkClientFeaturesPayload features = PerkClientRules.featuresOf(perkTeam("sharedfate:장님거인"));

		assertFalse(features.doubleJump());
		assertEquals(List.of("health", "food"), features.hiddenHudElements());
	}

	@Test
	void 여러_증강의_가림은_합쳐진다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = perkTeam("sharedfate:장님거인");
		state.ownedPerks.add("sharedfate:숨막힘");

		assertEquals(List.of("health", "food", "air"),
				PerkClientRules.featuresOf(state).hiddenHudElements());
	}

	@Test
	void 가림_차례는_보유_순서가_아니라_늘_같다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState 먼저 = perkTeam("sharedfate:장님거인");
		먼저.ownedPerks.add("sharedfate:숨막힘");
		TeamState 나중 = perkTeam("sharedfate:숨막힘");
		나중.ownedPerks.add("sharedfate:장님거인");

		// 차례가 들쭉날쭉하면 내용이 같아도 equals 가 거짓이 되어 매 점검마다 패킷이 다시 나간다.
		assertEquals(PerkClientRules.featuresOf(먼저), PerkClientRules.featuresOf(나중));
	}

	@Test
	void 공중_점프가_둘이면_센_쪽이_이긴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = perkTeam("sharedfate:허공답보");
		state.ownedPerks.add("sharedfate:강한도약");

		assertEquals(1.2, PerkClientRules.featuresOf(state).doubleJumpPower());
	}

	@Test
	void 풀에서_사라진_증강_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertEquals(PerkClientFeaturesPayload.NONE,
				PerkClientRules.featuresOf(perkTeam("sharedfate:사라진것")));
	}

	@Test
	void 상관없는_증강만_가진_팀은_아무것도_켜지지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertEquals(PerkClientFeaturesPayload.NONE,
				PerkClientRules.featuresOf(perkTeam("sharedfate:피통")));
	}

	// ------------------------------------------------------------------ 낙하 피해 2배

	@Test
	void 낙하_피해_배율_속성이_26_2_에_실제로_있다() {
		// 「허공답보」의 대가는 새 효과 타입이 아니라 기존 attribute 로 건다. 그 속성이
		// 정말 있는지 확인해 두지 않으면 증강이 조용히 반쪽만 걸린다.
		assertTrue(BuiltInRegistries.ATTRIBUTE
						.get(Identifier.fromNamespaceAndPath("minecraft", "fall_damage_multiplier"))
						.isPresent(),
				"minecraft:fall_damage_multiplier 가 없으면 낙하 피해 2배를 걸 수 없다");
	}

	@Test
	void 낙하_피해_2배_정의가_그대로_읽힌다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		Perk perk = PerkRegistry.byId("sharedfate:허공답보").orElseThrow();
		assertEquals(2, perk.effects().size(), "공중 점프와 낙하 피해 배율 둘이다");
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect create(String type, String json) {
		return PerkEffectType.fromId(type)
				.create("sharedfate:테스트", 0, JsonParser.parseString(json).getAsJsonObject());
	}

	/** 증강 하나를 든 팀. */
	private static TeamState perkTeam(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:허공답보", "rarity": "prism", "name": "허공답보",
				      "effects": [
				        { "type": "double_jump", "power": 0.42 },
				        { "type": "attribute", "attribute": "minecraft:fall_damage_multiplier",
				          "operation": "add_multiplied_total", "amount": 1.0 }
				      ] },
				    { "id": "sharedfate:강한도약", "rarity": "gold", "name": "강한 도약",
				      "effects": [ { "type": "double_jump", "power": 1.2 } ] },
				    { "id": "sharedfate:장님거인", "rarity": "prism", "name": "장님 거인",
				      "effects": [ { "type": "hide_hud", "elements": ["health", "food"] } ] },
				    { "id": "sharedfate:숨막힘", "rarity": "gold", "name": "숨막힘",
				      "effects": [ { "type": "hide_hud", "elements": ["air", "food"] } ] },
				    { "id": "sharedfate:피통", "rarity": "silver", "name": "뚝배기 대신 피통",
				      "effects": [ { "type": "max_health_bonus", "amount": 6.0 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
