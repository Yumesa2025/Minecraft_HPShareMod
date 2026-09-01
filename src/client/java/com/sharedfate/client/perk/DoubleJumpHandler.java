package com.sharedfate.client.perk;

import com.sharedfate.net.DoubleJumpPayload;
import com.sharedfate.ui.AirJumpInput;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 공중에서 점프 키를 눌렀는지 알아채 서버에 알린다.
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
 * <h2>클라이언트는 밀지 않는다</h2>
 * <p>요청만 보내고 속도는 건드리지 않는다. 서버가 {@code hurtMarked} 를 켜서 내려보내는
 * 속도 패킷은 {@code Entity.lerpMotion} 을 거쳐 <b>클라이언트의 속도를 덮어쓴다.</b> 그래서
 * 클라이언트가 먼저 밀어 두면, 몇 틱 뒤 도착한 패킷이 이미 줄어든 속도를 처음 세기로
 * 되돌려 한 번의 점프가 두 번 튀는 것처럼 보인다. 게다가 서버가 요청을 거절했을 때
 * (증강이 없다, 이미 썼다) 미리 뜬 몸을 되돌릴 방법이 위치 보정밖에 없어, 잠깐 뜬 뒤
 * 끌려 내려오는 더 나쁜 모양이 된다.
 *
 * <p>같은 이유로 <b>효과음도 클라이언트가 내지 않는다.</b> 여기서 내면 서버가 거절한
 * 요청에도 소리가 나 "소리는 났는데 안 뛰었다"가 된다. 소리는 서버가 실제로 밀어 주는
 * 그 자리({@code PerkClientRules.onDoubleJumpRequest})에서 낸다.
 *
 * <p>대신 왕복만큼 늦는다. 이 모드가 노리는 판(최대 4명, 대개 같은 집·LAN)에서는 한두
 * 틱이라 눈에 띄지 않고, 늦더라도 정확한 쪽을 골랐다.
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
		// 서버가 거절할 수도 있지만 한 번은 이미 소비했다. 거절당한 요청을 다시 보내 봐야
		// 서버도 같은 이유로 다시 거절하고, 소비하지 않으면 누르고 있는 동안 패킷이 쏟아진다.
		ClientPlayNetworking.send(DoubleJumpPayload.INSTANCE);
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
