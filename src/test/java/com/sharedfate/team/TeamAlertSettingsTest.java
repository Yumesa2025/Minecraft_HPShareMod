package com.sharedfate.team;

import com.sharedfate.TestBootstrap;
import com.sharedfate.net.TeamSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 피격·사망 알림 설정이 저장과 통신을 왕복하는지 본다.
 *
 * <p>이 둘은 <b>팀을 만들 때만 정할 수 있다.</b> 그래서 한 번 잘못 저장되면 고칠 방법이
 * 팀 해체뿐이다. 기존 월드와 예전 명단 파일이 「둘 다 꺼짐」으로 열리는지도 함께 본다.
 */
class TeamAlertSettingsTest {
	private static final UUID LEADER = new UUID(1L, 2L);

	@BeforeAll
	static void bootstrap() {
		TestBootstrap.ensureInitialized();
	}

	private static CompoundTag encode(TeamState state) {
		return (CompoundTag) TeamState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
	}

	private static TeamState decode(CompoundTag tag) {
		return TeamState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	}

	@Test
	void 새_팀은_두_알림이_모두_꺼져_있다() {
		TeamState state = TeamState.fresh(20.0F);

		assertFalse(state.damageAlertEnabled);
		assertFalse(state.deathAlertEnabled);
	}

	@Test
	void 두_알림은_따로따로_왕복_저장된다() {
		TeamState state = TeamState.fresh(20.0F);
		state.damageAlertEnabled = true;

		TeamState round = decode(encode(state));

		assertTrue(round.damageAlertEnabled);
		assertFalse(round.deathAlertEnabled, "한쪽만 켠 것이 둘 다 켜진 것으로 바뀌면 안 된다");
	}

	@Test
	void 둘_다_끈_팀은_alerts_항목을_아예_저장하지_않는다() {
		CompoundTag encoded = encode(TeamState.fresh(20.0F));

		assertFalse(encoded.contains("alerts"),
				"알림을 안 쓰면 저장 형태가 이 기능 도입 전과 같아야 한다");
	}

	@Test
	void alerts_항목이_없는_기존_월드는_둘_다_꺼진_채로_열린다() {
		TeamState state = TeamState.fresh(20.0F);
		state.damageAlertEnabled = true;
		state.deathAlertEnabled = true;
		state.xpLevel = 16;
		CompoundTag encoded = encode(state);
		assertTrue(encoded.contains("alerts"), "켜 두었으면 저장에 있어야 한다");
		encoded.remove("alerts");

		TeamState round = decode(encoded);

		assertFalse(round.damageAlertEnabled);
		assertFalse(round.deathAlertEnabled);
		assertEquals(16, round.xpLevel, "알림과 무관한 값은 그대로여야 한다");
	}

	@Test
	void 알림_묶음은_기존_필드와_같은_깊이에_한_항목으로만_붙는다() {
		TeamState on = TeamState.fresh(20.0F);
		on.deathAlertEnabled = true;

		CompoundTag legacy = encode(TeamState.fresh(20.0F));
		CompoundTag withAlerts = encode(on);

		assertTrue(withAlerts.keySet().containsAll(legacy.keySet()),
				"기존 항목 이름이 하나도 바뀌면 안 된다");
		assertEquals(legacy.keySet().size() + 1, withAlerts.keySet().size(),
				"알림은 alerts 한 항목만 늘려야 한다");
	}

	@Test
	void 켜고_끄기_넷은_동기화_묶음을_그대로_왕복한다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", 5, 6, 24.0F, 3,
				new TeamSyncPayload.Options(true, false, true, true), LEADER);

		RegistryFriendlyByteBuf buffer =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TeamSyncPayload.CODEC.encode(buffer, payload);
		TeamSyncPayload round = TeamSyncPayload.CODEC.decode(buffer);

		assertTrue(round.perksEnabled());
		assertFalse(round.damageAlertEnabled());
		assertTrue(round.deathAlertEnabled());
		assertTrue(round.runStarted());
		assertEquals(24.0F, round.maxHealth(), "넷을 묶느라 다른 항목이 밀리면 안 된다");
		assertEquals(3, round.swapIntervalMinutes());
		assertEquals("우리팀", round.teamName());
	}

	/** 회차 시작 여부만 다른 두 묶음이 실제로 다르게 실려 가는지. */
	@Test
	void 시작_대기_묶음은_회차가_시작되지_않았다고_실려_간다() {
		TeamSyncPayload payload = new TeamSyncPayload(List.of(), "우리팀", 0, 3, 20.0F, 0,
				new TeamSyncPayload.Options(true, false, false, false), LEADER);

		RegistryFriendlyByteBuf buffer =
				new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		TeamSyncPayload.CODEC.encode(buffer, payload);
		TeamSyncPayload round = TeamSyncPayload.CODEC.decode(buffer);

		assertFalse(round.runStarted());
		assertTrue(round.perksEnabled(), "회차 시작 항목이 앞의 셋을 밀면 안 된다");
	}

	@Test
	void 팀에_속하지_않은_묶음은_넷_다_꺼져_있다() {
		assertFalse(TeamSyncPayload.EMPTY.perksEnabled());
		assertFalse(TeamSyncPayload.EMPTY.damageAlertEnabled());
		assertFalse(TeamSyncPayload.EMPTY.deathAlertEnabled());
		assertFalse(TeamSyncPayload.EMPTY.runStarted());
	}
}
