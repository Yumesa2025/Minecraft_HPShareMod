package com.sharedfate.perk;

import com.sharedfate.TestBootstrap;
import com.sharedfate.perk.effect.EchoMiningEffect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code echo_mining}(골드 「메아리 채굴」)의 정의 읽기를 본다.
 *
 * <p>실제로 팀원 발밑을 캐고, 도구가 추가로 닳고, 로드 안 된 청크에서 건너뛰는지는 살아 있는
 * 서버·{@code ServerLevel}·{@code ServerPlayer}가 있어야 확인할 수 있어({@code
 * PositionSwapManagerTest}와 같은 이유) 여기서 다루지 않는다.
 */
class EchoMiningEffectTest {

	@BeforeAll
	static void setUp() {
		TestBootstrap.ensureInitialized();
	}

	@AfterEach
	void 정리() {
		PerkRegistry.clear();
	}

	@Test
	void 필드가_없고_인스턴스를_돌려쓴다() {
		PerkEffect first = create();
		PerkEffect second = create();

		assertSame(EchoMiningEffect.INSTANCE, first);
		assertSame(first, second);
	}

	@Test
	void 효과_타입_문자열로_찾을_수_있다() {
		assertSame(PerkEffectType.ECHO_MINING, PerkEffectType.fromId("echo_mining"));
	}

	@Test
	void apply_와_remove_는_아무_일도_하지_않는다() {
		EchoMiningEffect effect = assertInstanceOf(EchoMiningEffect.class, create());

		assertDoesNotThrow(() -> effect.apply(null));
		assertDoesNotThrow(() -> effect.remove(null));
	}

	private static PerkEffect create() {
		com.google.gson.JsonObject parsed =
				com.google.gson.JsonParser.parseString("{ \"type\": \"echo_mining\" }").getAsJsonObject();
		return PerkEffectType.ECHO_MINING.create("sharedfate:테스트", 0, parsed);
	}
}
