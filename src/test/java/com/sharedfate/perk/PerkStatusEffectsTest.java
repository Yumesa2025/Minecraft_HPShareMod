package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.StatusEffectPerk;
import com.sharedfate.team.TeamState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 증강이 건 상태이상을 팀 공유 대상에서 빼는 규칙을 검증한다.
 *
 * <p>{@code EffectSync} 자체는 서버와 플레이어가 있어야 돌아가므로, 그 안에서 쓰는 판별기인
 * {@link PerkStatusEffects} 를 같은 입력으로 직접 시험한다. {@code EffectSync.tick} 의 수집은
 * {@code representative.getActiveEffects()} 를 {@link PerkStatusEffects#shareable} 에 그대로
 * 흘려보내는 것이 전부라, 여기서 검증하는 목록이 곧 {@code TeamState.effects} 에 담기는 값이다.
 */
class PerkStatusEffectsTest {
	/** 포션 한 병 정도의 지속시간. 무한이 아니라는 점만 중요하다. */
	private static final int POTION_TICKS = 3 * 60 * 20;

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	// ------------------------------------------------------------------ 준비

	private static void loadPerks(Path dir) throws IOException {
		Files.writeString(dir.resolve(PerkRegistry.FILE_NAME), """
				{
				  "perks": [
				    {
				      "id": "sharedfate:swift",
				      "name": "쾌속",
				      "rarity": "common",
				      "stackable": true,
				      "maxStacks": 3,
				      "effects": [
				        { "type": "status_effect", "effect": "minecraft:speed", "amplifier": 0 }
				      ]
				    },
				    {
				      "id": "sharedfate:mending_blood",
				      "name": "재생혈",
				      "rarity": "rare",
				      "effects": [
				        { "type": "status_effect", "effect": "minecraft:regeneration", "amplifier": 1 }
				      ]
				    },
				    {
				      "id": "sharedfate:tough_body",
				      "name": "강골",
				      "rarity": "common",
				      "effects": [
				        { "type": "attribute", "attribute": "minecraft:max_health",
				          "operation": "add_value", "amount": 2.0 }
				      ]
				    }
				  ]
				}
				""", StandardCharsets.UTF_8);
		PerkRegistry.load(dir);
	}

	private static TeamState stateWith(String... perkIds) {
		TeamState state = TeamState.fresh(20.0F);
		state.perksEnabled = true;
		for (String id : perkIds) {
			state.ownedPerks.add(new PerkStack(id, 1));
		}
		return state;
	}

	/** 증강이 실제로 걸어 두는 모양 그대로의 인스턴스. */
	private static MobEffectInstance perkGranted(String perkId, int stacks) {
		Perk perk = PerkRegistry.byId(perkId).orElseThrow();
		for (PerkEffect candidate : perk.effects()) {
			if (candidate instanceof StatusEffectPerk status) {
				return status.grantedInstance(stacks);
			}
		}
		throw new IllegalArgumentException("status_effect 효과가 없는 증강: " + perkId);
	}

	// ------------------------------------------------------------------ 증강이 없을 때

	@Test
	void 증강이_하나도_없으면_아무것도_걸러내지_않는다() {
		TeamState state = TeamState.fresh(20.0F);

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertTrue(perkEffects.isEmpty());
		assertFalse(perkEffects.covers(MobEffects.SPEED));
		// 증강이 거는 모양과 똑같은 무한 상태이상이라도 증강이 없으면 그냥 공유 대상이다.
		assertFalse(perkEffects.grants(new MobEffectInstance(
				MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, false, true)));

		List<MobEffectInstance> active = List.of(
				new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0),
				new MobEffectInstance(MobEffects.REGENERATION, POTION_TICKS, 0));

		assertEquals(2, perkEffects.shareable(active).size(), "증강 도입 전과 결과가 같아야 한다");
	}

	@Test
	void 팀_상태가_없어도_안전하게_빈_목록이다() {
		assertTrue(PerkStatusEffects.of(null).isEmpty());
	}

	@Test
	void 상태이상을_걸지_않는_증강만_있으면_걸러낼_것도_없다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:tough_body");

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertTrue(perkEffects.isEmpty(), "속성 증강은 상태이상 목록에 끼어들면 안 된다");
	}

	// ------------------------------------------------------------------ 증강분 제외

	@Test
	void 증강이_건_무한_상태이상은_팀에_공유되지_않는다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:swift");

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertFalse(perkEffects.isEmpty());
		assertTrue(perkEffects.covers(MobEffects.SPEED));
		assertTrue(perkEffects.grants(perkGranted("sharedfate:swift", 1)));
		assertTrue(perkEffects.shareable(List.of(perkGranted("sharedfate:swift", 1))).isEmpty());
	}

	@Test
	void 중첩해서_등급이_오른_증강분도_그대로_걸러진다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = TeamState.fresh(20.0F);
		state.ownedPerks.add(new PerkStack("sharedfate:swift", 3));

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);
		MobEffectInstance granted = perkGranted("sharedfate:swift", 3);

		assertEquals(2, granted.getAmplifier(), "3중첩이면 등급이 두 단계 오른다");
		assertTrue(perkEffects.grants(granted));
	}

	@Test
	void 증강_두_개가_각자_다른_상태이상을_걸어도_모두_걸러진다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:swift", "sharedfate:mending_blood");

		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		assertTrue(perkEffects.shareable(List.of(
				perkGranted("sharedfate:swift", 1),
				perkGranted("sharedfate:mending_blood", 1))).isEmpty());
	}

	// ------------------------------------------------------------------ 포션분은 살아남는다

	@Test
	void 증강과_같은_종류라도_포션분은_팀에_공유된다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:swift");
		PerkStatusEffects perkEffects = PerkStatusEffects.of(state);

		// 신속 II 포션이 증강의 신속 I 위에 덮인 상황. 겉으로 보이는 인스턴스는 포션 쪽이다.
		MobEffectInstance potion = new MobEffectInstance(MobEffects.SPEED, POTION_TICKS, 1);

		assertFalse(perkEffects.grants(potion), "포션은 지속시간이 유한하므로 증강분이 아니다");
		assertEquals(List.of(potion), perkEffects.shareable(List.of(potion)));
	}

	@Test
	void 등급이_같은_포션도_지속시간이_유한하면_공유된다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		PerkStatusEffects perkEffects = PerkStatusEffects.of(stateWith("sharedfate:swift"));

		MobEffectInstance potion = new MobEffectInstance(MobEffects.SPEED, POTION_TICKS, 0);

		assertFalse(perkEffects.grants(potion));
		assertEquals(1, perkEffects.shareable(List.of(potion)).size());
	}

	@Test
	void 증강이_손대지_않는_상태이상은_평소대로_공유된다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		PerkStatusEffects perkEffects = PerkStatusEffects.of(stateWith("sharedfate:swift"));

		MobEffectInstance unrelated = new MobEffectInstance(MobEffects.FIRE_RESISTANCE, POTION_TICKS, 0);

		assertFalse(perkEffects.covers(MobEffects.FIRE_RESISTANCE));
		assertEquals(1, perkEffects.shareable(List.of(unrelated)).size());
	}

	@Test
	void 증강분과_포션분이_섞여_있으면_포션분만_남는다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		PerkStatusEffects perkEffects =
				PerkStatusEffects.of(stateWith("sharedfate:swift", "sharedfate:mending_blood"));

		MobEffectInstance strengthPotion = new MobEffectInstance(MobEffects.STRENGTH, POTION_TICKS, 0);
		MobEffectInstance speedPotion = new MobEffectInstance(MobEffects.SPEED, POTION_TICKS, 1);
		List<MobEffectInstance> active = List.of(
				perkGranted("sharedfate:mending_blood", 1),
				strengthPotion,
				speedPotion);

		List<MobEffectInstance> shared = perkEffects.shareable(active);

		assertEquals(List.of(strengthPotion, speedPotion), shared);
	}

	// ------------------------------------------------------------------ 회차 리셋

	@Test
	void 증강을_잃으면_그_상태이상은_다시_평범한_공유_대상이_된다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:swift");
		MobEffectInstance granted = perkGranted("sharedfate:swift", 1);

		assertTrue(PerkStatusEffects.of(state).shareable(List.of(granted)).isEmpty());

		// 전멸로 회차가 넘어가면 보유 증강이 비워진다.
		state.ownedPerks.clear();

		assertTrue(PerkStatusEffects.of(state).isEmpty());
		// 증강분이 애초에 팀 상태에 들어간 적이 없으므로 되살릴 것도 남지 않는다.
		assertTrue(state.effects.isEmpty());
	}

	@Test
	void 증강_풀에서_사라진_id는_조용히_무시한다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		TeamState state = stateWith("sharedfate:없어진증강");

		assertTrue(PerkStatusEffects.of(state).isEmpty());
	}

	// ------------------------------------------------------------------ 복사 규칙

	@Test
	void 공유_목록은_원본과_분리된_복사본이다(@TempDir Path dir) throws IOException {
		loadPerks(dir);
		PerkStatusEffects perkEffects = PerkStatusEffects.of(stateWith("sharedfate:swift"));
		MobEffectInstance potion = new MobEffectInstance(MobEffects.STRENGTH, POTION_TICKS, 0);

		List<MobEffectInstance> shared = perkEffects.shareable(new ArrayList<>(List.of(potion)));

		assertEquals(1, shared.size());
		assertEquals(potion, shared.getFirst());
		assertNotSame(potion, shared.getFirst(), "플레이어가 들고 있는 인스턴스를 그대로 넘기면 안 된다");
	}

	@Test
	void 증강이_없을_때는_매번_같은_빈_목록을_돌려준다() {
		assertSame(PerkStatusEffects.of(TeamState.fresh(20.0F)),
				PerkStatusEffects.of(TeamState.fresh(20.0F)));
	}
}
