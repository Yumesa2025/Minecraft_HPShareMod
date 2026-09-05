package com.sharedfate.sync;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.PerkRegistry;
import com.sharedfate.perk.PerkSetEffects;
import com.sharedfate.perk.PerkSetRegistry;
import com.sharedfate.perk.effect.ExperienceBonusEffect.Source;
import com.sharedfate.team.TeamState;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 출처별 경험치 배율이 <b>보유 증강과 세트 보상 양쪽</b>에서 걷히는지 본다.
 *
 * <p>여기서 지키는 것은 한 줄이다 — {@code teamMultiplier} 가 보유 증강을 훑은 뒤
 * {@link PerkSetEffects#activeEffectsOf} 도 이어서 훑는가. 그 한 줄을 빠뜨리면 빌드도 통과하고
 * 로그도 남지 않는데 「채굴 2단계」·「사냥 2단계」만 통째로 무동작이 된다.
 *
 * <p>증강과 세트 정의는 임시 폴더에 직접 적어 쓴다. 번들 정의가 바뀌어도 이 시험은 흔들리지
 * 않는다 — {@code DefaultPerkSetValuesTest} 와 달리 여기서 보는 것은 값이 아니라 배선이다.
 */
class ExperienceSourceBonusTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkSetRegistry.clear();
		PerkRegistry.clear();
		ExperienceBonus.reset();
	}

	// ------------------------------------------------------------------ 세트가 셈해지는가

	@Test
	void 세트_보상의_배율이_셈해진다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = miningTeam();

		assertEquals(1.5, ExperienceBonus.teamMultiplier(state, Source.ORE), 1.0e-9,
				"채굴 증강 둘이면 세트 2단계가 켜지고 그 배율이 걸려야 한다");
	}

	@Test
	void 보유_증강의_배율과_세트의_배율은_곱해진다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = miningTeam();
		state.ownedPerks.add("test:ore_bonus");

		assertEquals(3.0, ExperienceBonus.teamMultiplier(state, Source.ORE), 1.0e-9,
				"증강 2.0 배 × 세트 1.5 배");
	}

	@Test
	void 다른_출처에는_걸리지_않는다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = miningTeam();
		state.ownedPerks.add("test:ore_bonus");

		assertEquals(1.0, ExperienceBonus.teamMultiplier(state, Source.MOB), 1.0e-9,
				"광물 전용 배율이 몹에 걸리면 안 된다");
	}

	@Test
	void 세트가_아직_안_켜졌으면_배율이_없다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("test:mine_a");

		assertEquals(1.0, ExperienceBonus.teamMultiplier(state, Source.ORE), 1.0e-9,
				"채굴 하나로는 2단계가 켜지지 않는다");
	}

	@Test
	void 증강을_끈_팀과_팀이_없는_경우는_1이다(@TempDir Path dir) throws IOException {
		load(dir);
		TeamState off = miningTeam();
		off.perksEnabled = false;

		assertEquals(1.0, ExperienceBonus.teamMultiplier(off, Source.ORE), 1.0e-9);
		assertEquals(1.0, ExperienceBonus.teamMultiplier(null, Source.ORE), 1.0e-9);
		assertEquals(1.0, ExperienceBonus.teamMultiplier(miningTeam(), null), 1.0e-9);
	}

	// ------------------------------------------------------------------ 문맥이 새지 않는가

	/**
	 * 캐는 중이 아니면 블록 경험치에 손대지 않는다.
	 *
	 * <p>{@code Block.popExperience} 는 광석뿐 아니라 스포너·삐걱이는 심장에서도 불린다.
	 * 문맥이 없을 때 곱해 버리면 그것들까지 부풀어 오른다.
	 */
	@Test
	void 문맥이_없으면_블록_경험치를_그대로_둔다() {
		assertFalse(ExperienceBonus.hasBlockContext(), "시작할 때는 비어 있어야 한다");

		assertEquals(7, ExperienceBonus.scaleBlockExperience(BlockPos.ZERO, 7));
		assertEquals(0, ExperienceBonus.scaleBlockExperience(BlockPos.ZERO, 0));
		assertEquals(7, ExperienceBonus.scaleBlockExperience(null, 7));
	}

	/** 처치자가 사람이 아니면(몹끼리 싸움·낙하·용암) 손대지 않는다. */
	@Test
	void 처치자가_없으면_몹_경험치를_그대로_둔다() {
		assertEquals(9, ExperienceBonus.scaleMobExperience(9, null));
		assertEquals(0, ExperienceBonus.scaleMobExperience(0, null));
	}

	// ------------------------------------------------------------------ 도우미

	/** 채굴 증강 둘을 가진 팀. 세트 2단계가 켜진다. */
	private static TeamState miningTeam() {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		state.ownedPerks.add("test:mine_a");
		state.ownedPerks.add("test:mine_b");
		return state;
	}

	private static void load(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), PERKS, StandardCharsets.UTF_8);
		Files.writeString(dir.resolve(PerkSetRegistry.FILE_NAME), SETS, StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
		PerkSetRegistry.load(dir);
	}

	private static final String PERKS = """
			{
			  "perks": [
			    {
			      "id": "test:mine_a", "name": "채굴 가", "description": "",
			      "rarity": "silver", "set_types": ["mining"],
			      "effects": [ { "type": "no_sleep" } ]
			    },
			    {
			      "id": "test:mine_b", "name": "채굴 나", "description": "",
			      "rarity": "silver", "set_types": ["mining"],
			      "effects": [ { "type": "no_sleep" } ]
			    },
			    {
			      "id": "test:ore_bonus", "name": "광물 배율", "description": "",
			      "rarity": "gold",
			      "effects": [
			        { "type": "experience_bonus", "source": "ore", "multiplier": 2.0 }
			      ]
			    }
			  ]
			}
			""";

	private static final String SETS = """
			{
			  "sets": [
			    {
			      "type": "mining",
			      "tiers": [
			        {
			          "count": 2, "name": "캔 만큼 배운다", "description": "",
			          "effects": [
			            { "type": "experience_bonus", "source": "ore", "multiplier": 1.5 }
			          ]
			        }
			      ]
			    }
			  ]
			}
			""";
}
