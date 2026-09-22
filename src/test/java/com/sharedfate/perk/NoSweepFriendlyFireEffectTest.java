package com.sharedfate.perk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.NoSweepFriendlyFireEffect;
import com.sharedfate.team.ShareTeam;
import com.sharedfate.team.TeamState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code no_sweep_friendly_fire} 의 정의 읽기와 「이 사람을 휩쓸지 말 것인가」 판정을 본다.
 *
 * <p>실제로 피해를 빼는 자리는 {@code PlayerSweepFriendlyFireMixin} 이고, 그쪽은 살아 있는
 * 서버에서 팀과 상태를 꺼내 오는 일만 한다. 여기서 보는 것은 그 뒤에 붙는 순수한 판정
 * {@link NoSweepFriendlyFireEffect#blocksSweep} 와 {@link NoSweepFriendlyFireEffect#heldBy} 다.
 */
class NoSweepFriendlyFireEffectTest {

	private static final UUID 나 = UUID.randomUUID();
	private static final UUID 동료 = UUID.randomUUID();
	private static final UUID 남 = UUID.randomUUID();

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
	void 필드가_없어도_읽힌다() {
		PerkEffect effect = raw("{ \"type\": \"no_sweep_friendly_fire\" }");

		assertInstanceOf(NoSweepFriendlyFireEffect.class, effect);
		assertSame(NoSweepFriendlyFireEffect.INSTANCE, effect, "상태가 없으므로 하나를 돌려쓴다");
	}

	@Test
	void 모르는_필드는_그냥_무시한다() {
		assertSame(NoSweepFriendlyFireEffect.INSTANCE,
				raw("{ \"type\": \"no_sweep_friendly_fire\", \"rarity\": \"silver\" }"));
	}

	// ------------------------------------------------------------------ 보유 판정

	@Test
	void 가진_팀만_면제된다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertTrue(NoSweepFriendlyFireEffect.heldBy(stateWith("sharedfate:sweeping")));
		assertFalse(NoSweepFriendlyFireEffect.heldBy(stateWith("sharedfate:plain")));
	}

	@Test
	void 팀이_없거나_증강이_없으면_면제되지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSweepFriendlyFireEffect.heldBy(null));

		TeamState empty = TeamState.fresh(20.0F);
		empty.perksEnabled = true;
		assertFalse(NoSweepFriendlyFireEffect.heldBy(empty), "아직 아무 증강도 없다");
	}

	@Test
	void 증강을_끈_팀은_면제되지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		TeamState state = stateWith("sharedfate:sweeping");
		state.perksEnabled = false;

		assertFalse(NoSweepFriendlyFireEffect.heldBy(state), "증강을 끄면 덤도 함께 멈춰야 한다");
	}

	@Test
	void 풀에_없는_id_는_건너뛴다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSweepFriendlyFireEffect.heldBy(stateWith("sharedfate:missing")));
	}

	// ------------------------------------------------------------------ 휩쓸기 대상 판정

	@Test
	void 같은_팀_사람만_휩쓸기에서_빠진다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		ShareTeam team = team();
		TeamState state = stateWith("sharedfate:sweeping");

		assertTrue(NoSweepFriendlyFireEffect.blocksSweep(team, state, 동료), "팀원은 빠진다");
		assertFalse(NoSweepFriendlyFireEffect.blocksSweep(team, state, 남),
				"남은 그대로 휩쓸려야 한다");
	}

	@Test
	void 팀이_없으면_아무도_안_빠진다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSweepFriendlyFireEffect.blocksSweep(null, stateWith("sharedfate:sweeping"),
				동료));
	}

	@Test
	void 효과가_없으면_팀원도_휩쓸린다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSweepFriendlyFireEffect.blocksSweep(team(), stateWith("sharedfate:plain"),
				동료), "증강을 안 가진 팀은 예전 그대로 서로를 휩쓴다");
	}

	@Test
	void 대상_UUID_가_없으면_막지_않는다(@TempDir Path dir) throws IOException {
		PerkRegistry.load(pool(dir));

		assertFalse(NoSweepFriendlyFireEffect.blocksSweep(team(), stateWith("sharedfate:sweeping"),
				null));
	}

	// ------------------------------------------------------------------ 도우미

	private static PerkEffect raw(String json) {
		JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
		return PerkEffectType.NO_SWEEP_FRIENDLY_FIRE.create("sharedfate:테스트", 0, parsed);
	}

	private static ShareTeam team() {
		return ShareTeam.create("시험팀", 나).withMemberAdded(동료);
	}

	private static TeamState stateWith(String perkId) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add(perkId);
		return state;
	}

	private static Path pool(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    { "id": "sharedfate:sweeping", "rarity": "silver", "name": "휩쓸기",
				      "effects": [ { "type": "no_sweep_friendly_fire" } ] },
				    { "id": "sharedfate:plain", "rarity": "silver", "name": "그냥 증강",
				      "effects": [ { "type": "damage_taken", "multiplier": 0.9 } ] }
				  ]
				}
				""", StandardCharsets.UTF_8);
		return dir;
	}
}
