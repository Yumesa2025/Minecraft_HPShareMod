package com.sharedfate.client.perk;

import com.sharedfate.net.DoubleJumpPayload;
import com.sharedfate.ui.AirJumpImpulse;
import com.sharedfate.ui.AirJumpInput;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 공중에서 점프 키를 눌렀는지 알아채 몸을 띄우고 서버에 알린다.
 *
 * <p>서버는 공중 점프 입력을 볼 수 없다. 바닐라가 올려보내는 것은 위치와 이동 입력이고,
 * 땅에서 뛴 결과는 위치로 드러나지만 공중에서 누른 키는 아무 데도 실리지 않는다. 그래서
 * 이 한 가지만은 클라이언트가 알려 줘야 한다.
 *
 * <h2>언제 보내는가</h2>
 * <p>판단은 전부 {@link AirJumpInput} 이 한다. 요약하면 <b>한 번 뜬 뒤 착지하기 전까지,
 * 공중에서 점프 키를 뗐다가 다시 누르면</b> 한 번 보낸다. 높이도, 올라가는 중인지
 * 떨어지는 중인지도 따지지 않는다. "뗐다가 다시"를 요구하는 이유는 저쪽 문서에 적어 두었다.
 * 짧게 말하면 바닐라는 땅에서 점프한 그 틱 안에 이미 땅을 떠나므로, 그 조건을 빼면 스페이스
 * 한 번이 땅 점프와 공중 점프를 함께 써 버려 두 번째 점프가 아예 보이지 않는다.
 *
 * <h2>왜 mixin 이 아닌가</h2>
 * <p>{@code LocalPlayer.aiStep} 같은 자리에 mixin 을 걸 수도 있지만, 필요한 것이 키와
 * 발판의 변화뿐이라 매 틱 한 번 물어보는 것으로 충분하다. 이 모드는 {@code defaultRequire: 1}
 * 이라 mixin 이 하나라도 안 붙으면 게임이 켜지지도 않으므로, 버전이 바뀔 때마다 깨질 자리를
 * 늘리지 않는 편이 낫다.
 *
 * <h2>미는 것은 클라이언트다</h2>
 * <p>누른 그 틱에 {@link AirJumpImpulse} 가 낸 속도를 스스로 싣는다. 예전에는 요청만 보내고
 * 서버가 {@code hurtMarked} 로 속도를 내려보내 주기를 기다렸는데, 그 왕복 동안(빨라야 두 틱,
 * 핑이 붙으면 더) 몸은 계속 떨어진다. 눌렀는데 잠깐 그대로 가라앉다가 갑자기 솟는 이 틈이
 * <b>두 번째 점프에서 살짝 멈추는 것처럼</b> 느껴지던 자리다.
 *
 * <p>바닐라도 같은 방식이다. 땅 점프는 클라이언트가 먼저 뛰고, 서버는 위치 보고를 보고
 * {@code ServerGamePacketListenerImpl} 이 제 쪽 사본에 {@code jumpFromGround()} 를 뒤따라
 * 부를 뿐이다. 마인크래프트에서 위치는 원래 클라이언트가 정한다 — 서버의 이동 검사는
 * 틱당 10칸(제곱 100)까지 허용하므로 0.7 남짓의 도약은 걸릴 일이 없다.
 *
 * <h2>두 번 밀리지 않게</h2>
 * <p>서버는 요청을 받아들여도 {@code hurtMarked} 를 <b>켜지 않는다.</b> 속도 패킷은
 * {@code Entity.lerpMotion} 을 거쳐 클라이언트의 속도를 통째로 덮어쓰므로, 켜 두면 몇 틱 뒤
 * 도착한 패킷이 이미 줄어든 속도를 처음 세기로 되돌려 한 번의 점프가 두 번 튄다. 서버는
 * 제 쪽 사본에만 같은 속도를 적어 두고(그래야 이동 검사와 다른 코드가 같은 값을 본다),
 * 되돌리는 일은 <b>거절할 때만</b> 한다.
 *
 * <p>효과음도 여기서 낸다. {@code level().playSound(뛴 사람, ...)} 는 클라이언트에서
 * <b>그 사람에게만</b> 들린다. 서버는 같은 호출로 <b>그 사람만 빼고</b> 주변에 들려주므로
 * 소리가 겹치지 않고, 몸이 떠오르는 순간과 소리가 같이 온다.
 */
public final class DoubleJumpHandler {
	/** 키와 발판을 보고 언제 보낼지 정하는 상태 기계. 시험할 수 있게 공용 소스셋에 두었다. */
	private static final AirJumpInput INPUT = new AirJumpInput();

	private DoubleJumpHandler() {
	}

	/** {@code ClientTickEvents.END_CLIENT_TICK} 에서 부른다. */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			reset();
			return;
		}

		// 화면이 떠 있는 동안은 키 상태를 알 수 없다. "안 눌림"으로 보면 창을 닫는 순간
		// 새로 누른 것으로 오해해 인벤토리를 닫자마자 뛰어오르고, 창을 여는 것만으로 다시
		// 뛸 준비가 되어 버린다. 그래서 "눌린 채로 얼어붙었다"고 본다.
		boolean jumpDown = client.gui.screen() != null || player.input.keyPresses.jump();
		boolean allowed = ClientPerkFeatures.doubleJumpEnabled()
				&& !player.isSpectator()
				&& !player.getAbilities().flying
				&& ClientPlayNetworking.canSend(DoubleJumpPayload.TYPE);

		if (!INPUT.tick(grounded(player), jumpDown, allowed)) {
			return;
		}
		launch(player);
		// 서버가 거절할 수도 있지만 한 번은 이미 소비했다. 거절당한 요청을 다시 보내 봐야
		// 서버도 같은 이유로 다시 거절하고, 소비하지 않으면 누르고 있는 동안 패킷이 쏟아진다.
		ClientPlayNetworking.send(DoubleJumpPayload.INSTANCE);
	}

	/**
	 * 몸을 띄우고 소리를 낸다.
	 *
	 * <p>세기는 서버가 {@code PerkClientFeaturesPayload} 로 내려 준 값이다. 클라이언트가
	 * 스스로 정하지 않으므로, 이 값을 키워 봐야 서버가 증강 정의에서 다시 읽는 값과
	 * 어긋나기만 한다.
	 */
	private static void launch(LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		AirJumpImpulse.Velocity pushed = AirJumpImpulse.of(motion.x, motion.y, motion.z,
				ClientPerkFeatures.doubleJumpPower());
		player.setDeltaMovement(pushed.x(), pushed.y(), pushed.z());
		// 첫 인자가 "이 소리를 낸 사람"이다. 클라이언트에서는 그 사람이 자기 자신일 때만
		// 울리고, 서버에서는 반대로 그 사람만 빼고 주변에 나간다.
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.0F);
	}

	/**
	 * 발판 위인가.
	 *
	 * <p>땅뿐 아니라 물과 사다리도 발판으로 본다. 서버의 판정
	 * ({@code PerkClientRules.grounded})과 같은 기준이어야 클라이언트만 썼다고 여기고
	 * 서버는 안 썼다고 여기는 어긋남이 생기지 않는다.
	 */
	private static boolean grounded(LocalPlayer player) {
		return player.onGround() || player.isInLiquid() || player.onClimbable()
				|| player.getAbilities().flying || player.isPassenger() || player.isFallFlying();
	}

	/** 월드에서 나가거나 공중 점프가 꺼졌을 때 부른다. */
	public static void reset() {
		INPUT.reset();
	}
}
