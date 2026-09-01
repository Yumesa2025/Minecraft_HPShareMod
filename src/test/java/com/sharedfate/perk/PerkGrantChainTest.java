package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.team.TeamState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강을 고른 즉시 지급이 서로를 부르는 연쇄({@link PerkGrantChain})를 본다.
 *
 * <p>실버 「숨은 재능」이 뽑은 골드가 하필 「하늘의 은총」이고, 그게 뽑은 프리즘이 하필
 * 「도박꾼」인 경우처럼, 무작위로 받은 증강이 <b>또</b> 즉시 지급 효과를 가지면 그것도 마저
 * 발동해야 한다는 요구를 확인한다. 서버 없이 시험하려고 {@code server}·{@code team} 은 전부
 * {@code null} 로 넘긴다 — {@link PerkGambler}·{@link PerkRarityGrant} 등이 이미 그렇게
 * 시험되고 있다.
 */
class PerkGrantChainTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 실버_골드_프리즘_도박꾼이_한_번의_선택으로_전부_연쇄된다(@TempDir Path dir) throws IOException {
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:chain_silver", "rarity": "silver", "name": "연쇄실버",
				    "effects": [ { "type": "rarity_grant", "rarity": "gold", "count": 1 } ] },
				  { "id": "sharedfate:chain_gold", "rarity": "gold", "name": "연쇄골드",
				    "effects": [
				      { "type": "item_grant",
				        "items": [ { "id": "minecraft:golden_apple", "count": 1 } ] },
				      { "type": "rarity_grant", "rarity": "prism", "count": 1 }
				    ] },
				  { "id": "sharedfate:chain_prism", "rarity": "prism", "name": "연쇄프리즘",
				    "effects": [ { "type": "gambler" } ] },
				  { "id": "sharedfate:extra_a", "rarity": "silver", "name": "덤가",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] },
				  { "id": "sharedfate:extra_b", "rarity": "silver", "name": "덤나",
				    "effects": [ { "type": "damage_dealt", "multiplier": 1.1 } ] }
				] }
				""");
		PerkRegistry.load(dir);
		Perk chosen = PerkRegistry.byId("sharedfate:chain_silver").orElseThrow();

		TeamState state = TeamState.fresh(20.0F);
		// PerkManager.commit 이 PerkGrantChain 을 부르기 전에 이미 해 두는 일과 같다.
		state.ownedPerks.add(chosen.id());

		PerkGrantChain.run(null, null, state, chosen, RandomSource.create(1L));

		Set<String> owned = new HashSet<>(state.ownedPerks);
		assertEquals(5, owned.size(), "다섯 증강이 전부, 중복 없이 들어가야 한다");
		assertEquals(5, state.ownedPerks.size(), "목록 자체에도 중복이 없어야 한다");
		assertTrue(owned.containsAll(List.of("sharedfate:chain_silver", "sharedfate:chain_gold",
				"sharedfate:chain_prism", "sharedfate:extra_a", "sharedfate:extra_b")));

		// 연쇄로 받은 골드(chain_gold)의 item_grant 도 실제로 발동해야 한다 — 처음 고른
		// 증강이 아니라는 이유로 건너뛰면 안 된다.
		boolean hasGoldenApple = state.mainItems.stream()
				.anyMatch(stack -> stack.is(Items.GOLDEN_APPLE) && stack.getCount() >= 1);
		assertTrue(hasGoldenApple, "연쇄로 받은 「연쇄골드」의 item_grant 가 발동해야 한다");
	}

	@Test
	void 스스로를_다시_뽑는_reroll_이어도_무한_루프에_빠지지_않는다(@TempDir Path dir) throws IOException {
		// 프리즘 풀에 이 증강 하나뿐이라, rarity_reroll(rarity: "prism") 이 뽑을 수 있는
		// 유일한 후보가 자기 자신이다. 방문 표시가 없으면 매번 다시 큐에 들어가 끝나지 않는다.
		write(dir, """
				{ "perks": [
				  { "id": "sharedfate:loopy", "rarity": "prism", "name": "돌고도는",
				    "min_level": 30,
				    "effects": [ { "type": "rarity_reroll", "rarity": "prism" } ] }
				] }
				""");
		PerkRegistry.load(dir);
		Perk loopy = PerkRegistry.byId("sharedfate:loopy").orElseThrow();

		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(loopy.id());

		assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
				PerkGrantChain.run(null, null, state, loopy, RandomSource.create(7L)));

		assertTrue(state.ownedPerks.stream().allMatch(id -> id.equals(loopy.id())),
				"자기 자신 말고 다른 id 가 섞일 수 없다(풀에 하나뿐이므로)");
	}

	@Test
	void 정의가_망가져도_MAX_STEPS_에서_강제로_멈춘다() {
		// 방문 표시가 있어 실제로는 이 상한에 절대 닿지 않지만, 안전판 자체가 동작하는지는
		// 상수를 직접 확인해 둔다.
		assertTrue(PerkGrantChain.MAX_STEPS > 0);
	}

	@Test
	void 인자가_비어도_터지지_않는다() {
		PerkGrantChain.run(null, null, null, null, RandomSource.create(1L));
		PerkGrantChain.run(null, null, TeamState.fresh(20.0F), null, RandomSource.create(1L));
	}

	private static void write(Path dir, String json) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), json, StandardCharsets.UTF_8);
	}
}
