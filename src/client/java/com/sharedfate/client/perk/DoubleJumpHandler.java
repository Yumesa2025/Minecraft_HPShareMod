package com.sharedfate.client.perk;

import com.sharedfate.net.DoubleJumpPayload;
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
 * <h2>왜 mixin 이 아닌가</h2>
 * <p>{@code LocalPlayer.aiStep} 같은 자리에 mixin 을 걸 수도 있지만, 필요한 것이
 * "이전 틱엔 안 눌렸는데 이번 틱에 눌렸다"뿐이라 매 틱 한 번 물어보는 것으로 충분하다.
 * 이 모드는 {@code defaultRequire: 1} 이라 mixin 이 하나라도 안 붙으면 게임이 켜지지도
 * 않으므로, 버전이 바뀔 때마다 깨질 자리를 늘리지 않는 편이 낫다.
 *
 * <h2>클라이언트는 밀지 않는다</h2>
 * <p>요청만 보내고 속도는 건드리지 않는다. 서버가 {@code hurtMarked} 를 켜서 내려보내는
 * 속도 패킷은 {@code Entity.lerpMotion} 을 거쳐 <b>클라이언트의 속도를 덮어쓴다.</b> 그래서
 * 클라이언트가 먼저 밀어 두면, 몇 틱 뒤 도착한 패킷이 이미 줄어든 속도를 처음 세기로
 * 되돌려 한 번의 점프가 두 번 튀는 것처럼 보인다. 게다가 서버가 요청을 거절했을 때
 * (증강이 없다, 이미 썼다) 미리 뜬 몸을 되돌릴 방법이 위치 보정밖에 없어, 잠깐 뜬 뒤
 * 끌려 내려오는 더 나쁜 모양이 된다.
 *
 * <p>대신 왕복만큼 늦는다. 이 모드가 노리는 판(최대 4명, 대개 같은 집·LAN)에서는 한두
 * 틱이라 눈에 띄지 않고, 늦더라도 정확한 쪽을 골랐다.
 */
public final class DoubleJumpHandler {
	/** 지난 틱에 점프 키가 눌려 있었는가. 누르는 <b>순간</b>만 잡아내려고 들고 있다. */
	private static boolean jumpHeldLastTick;
	/** 지금 뜬 상태에서 이미 요청을 보냈는가. 땅에 닿으면 풀린다. */
	private static boolean airJumpUsed;

	private DoubleJumpHandler() {
	}

	/** {@code ClientTickEvents.END_CLIENT_TICK} 에서 부른다. */
	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			reset();
			return;
		}

		boolean grounded = grounded(player);
		if (grounded) {
			airJumpUsed = false;
		}

		// 화면이 떠 있는 동안은 입력을 보지 않는다. 창을 닫는 순간 눌린 것으로 오해하면
		// 인벤토리를 닫자마자 뛰어오른다.
		boolean jumpDown = client.gui.screen() == null && player.input.keyPresses.jump();
		boolean pressedNow = jumpDown && !jumpHeldLastTick;
		jumpHeldLastTick = jumpDown;

		if (!pressedNow || grounded || airJumpUsed) {
			return;
		}
		if (!ClientPerkFeatures.doubleJumpEnabled() || player.isSpectator()
				|| player.getAbilities().flying) {
			return;
		}
		if (!ClientPlayNetworking.canSend(DoubleJumpPayload.TYPE)) {
			return;
		}
		// 서버가 거절할 수도 있지만 표시는 여기서 켜 둔다. 거절당한 요청을 다시 보내 봐야
		// 서버도 같은 이유로 다시 거절하고, 표시를 안 켜면 누르고 있는 동안 패킷이 쏟아진다.
		airJumpUsed = true;
		ClientPlayNetworking.send(DoubleJumpPayload.INSTANCE);
	}

	/**
	 * 다시 뛸 수 있는 상태인가.
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
		jumpHeldLastTick = false;
		airJumpUsed = false;
	}
}
