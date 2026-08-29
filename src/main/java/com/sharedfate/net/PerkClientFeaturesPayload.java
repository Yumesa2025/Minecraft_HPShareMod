package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.effect.HideHudEffect;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * S2C — 클라이언트가 스스로 해야 하는 증강 기능을 알려 준다.
 *
 * <p>서버 혼자서는 할 수 없는 증강이 둘 있다. 공중 점프({@code double_jump})는 서버가
 * 입력을 볼 수 없고, HUD 가림({@code hide_hud})은 서버에 그릴 화면이 없다. 그래서 "이 팀이
 * 지금 무엇을 갖고 있는가"만 클라이언트로 내려보내고, 나머지 판단은 양쪽이 나눠 맡는다.
 *
 * <h2>왜 {@link PerkSyncPayload} 에 얹지 않았는가</h2>
 * <p>{@code PerkSyncPayload} 는 화면에 글자로 뿌리는 표시용 패킷이고, 보내는 시점도
 * {@code PerkManager} 가 증강 목록이 바뀔 때로 정해 두었다. 여기 필요한 값은 보내는 조건이
 * 다르고(팀 상태가 아니라 <b>보유 효과</b>가 바뀔 때), 무엇보다 기존 레코드에 필드를 더하면
 * 그 패킷을 읽는 모든 자리가 함께 바뀐다. 새 패킷을 하나 더 두면 기존 세 패킷의 형식이
 * 한 바이트도 달라지지 않아, 잘못 건드렸을 때 무너지는 범위가 이 패킷 안으로 갇힌다.
 *
 * <p>대신 클라이언트가 이 패킷을 모르면 공중 점프가 조용히 안 되는 상태가 되므로,
 * {@link SharedFateNetworking#PROTOCOL_VERSION} 은 올려 두었다. 악수 단계에서 걸러진다.
 *
 * @param doubleJump         공중에서 한 번 더 뛸 수 있는가
 * @param doubleJumpPower    공중 점프가 실을 위쪽 속도. {@code doubleJump} 가 거짓이면 0
 * @param hiddenHudElements  가려야 할 HUD 칸 이름. {@link HideHudEffect.Element#id()} 형식이며
 *                           모르는 이름은 클라이언트가 조용히 버린다
 */
public record PerkClientFeaturesPayload(boolean doubleJump, double doubleJumpPower,
		List<String> hiddenHudElements) implements CustomPacketPayload {

	/** 가릴 수 있는 칸의 개수 상한. {@link HideHudEffect.Element} 의 개수와 같다. */
	public static final int MAX_HIDDEN_ELEMENTS = 4;

	/** 클라이언트가 따로 할 일이 없는 상태. 팀이 없거나 해당 증강이 없을 때 보낸다. */
	public static final PerkClientFeaturesPayload NONE =
			new PerkClientFeaturesPayload(false, 0.0, List.of());

	public static final Type<PerkClientFeaturesPayload> TYPE =
			new Type<>(SharedFateMod.id("perk_client_features"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PerkClientFeaturesPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.BOOL, PerkClientFeaturesPayload::doubleJump,
					ByteBufCodecs.DOUBLE, PerkClientFeaturesPayload::doubleJumpPower,
					ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_HIDDEN_ELEMENTS)),
					PerkClientFeaturesPayload::hiddenHudElements,
					PerkClientFeaturesPayload::new);

	public PerkClientFeaturesPayload {
		hiddenHudElements = List.copyOf(hiddenHudElements);
	}

	/**
	 * 칸 묶음으로 만든다.
	 *
	 * <p>이름의 차례는 언제나 {@link HideHudEffect.Element} 의 선언 순서다. 이 패킷은
	 * "지난번에 보낸 것과 같은가"를 {@code equals} 로 따져 달라졌을 때만 다시 보내는데,
	 * 차례가 들쭉날쭉하면 내용이 같아도 다르다고 나와 매번 다시 보내게 된다.
	 */
	public static PerkClientFeaturesPayload of(boolean doubleJump, double doubleJumpPower,
			Set<HideHudEffect.Element> hidden) {
		List<String> names = new ArrayList<>(MAX_HIDDEN_ELEMENTS);
		// 들어온 묶음의 차례에 기대지 않고 선언 순서로 훑는다.
		for (HideHudEffect.Element element : HideHudEffect.Element.values()) {
			if (hidden.contains(element)) {
				names.add(element.id());
			}
		}
		return new PerkClientFeaturesPayload(doubleJump, doubleJump ? doubleJumpPower : 0.0, names);
	}

	/** 패킷에 실린 이름을 칸 묶음으로 되돌린다. 모르는 이름은 버린다. */
	public Set<HideHudEffect.Element> hidden() {
		Set<HideHudEffect.Element> elements = EnumSet.noneOf(HideHudEffect.Element.class);
		for (String name : hiddenHudElements) {
			HideHudEffect.Element element = HideHudEffect.Element.fromId(name);
			if (element != null) {
				elements.add(element);
			}
		}
		return elements;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
