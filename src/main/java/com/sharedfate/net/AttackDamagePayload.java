package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.ui.StatSummary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * S2C — 이 사람의 근접 공격력({@code minecraft:attack_damage}).
 *
 * <h2>왜 이 값만 따로 보내야 하는가</h2>
 * <p>바닐라는 이 속성을 <b>클라이언트에 동기화하지 않는다.</b> {@code Attributes} 의 등록부에서
 * 이 속성만 {@code setSyncable(true)} 없이 등록되고, {@code AttributeMap.getSyncableAttributes}
 * 가 그 표를 보고 거른다. 게다가 장비의 속성 수정자를 실제로 붙이는
 * {@code LivingEntity.detectEquipmentUpdates} 는 {@code !level.isClientSide} 안에서만 돈다.
 * 그래서 클라이언트가 스스로 읽는 공격력은 <b>맨손 기본값 1.0</b> 뿐이고 무기도 증강도 들어
 * 있지 않다. 팀 화면 「능력치」 탭이 공격력을 적으려면 서버가 알려 주는 수밖에 없다.
 *
 * <h2>왜 {@link TeamSyncPayload} 에 얹지 않았는가</h2>
 * <ul>
 *   <li><b>팀 단위가 아니라 사람 단위다.</b> {@code TeamSyncPayload} 는 팀마다 <b>하나를 만들어
 *       전원에게 같은 것을 보내는</b> 묶음이다. 공격력은 받는 사람마다 다르므로 저기 얹으면
 *       팀원 수만큼 다른 묶음을 만들어야 해서 그 패킷의 성격 자체가 바뀐다.</li>
 *   <li><b>팀이 없어도 적어야 한다.</b> 「능력치」 탭은 팀에 속하지 않아도 네 줄을 모두 그린다.
 *       팀이 없을 때 나가는 것은 {@code TeamSyncPayload.EMPTY} 뿐이라 얹을 자리가 없다.</li>
 *   <li><b>보내는 때가 다르다.</b> 저쪽은 명단·레벨이 바뀔 때 나가고 무기를 바꿔도 나가지
 *       않는다.</li>
 *   <li><b>자리도 없다.</b> {@code TeamSyncPayload} 의 {@code StreamCodec.composite} 는 항목
 *       8개가 상한이고 이미 다 차 있다.</li>
 * </ul>
 *
 * <p>대신 이 패킷을 모르는 클라이언트는 「능력치」 탭에서 공격력 줄이 <b>조용히 빠진 채</b>
 * 보이게 된다. 그래서 {@link SharedFateNetworking#PROTOCOL_VERSION} 을 올려 악수 단계에서
 * 걸러지도록 했다.
 *
 * @param base    바닐라 기본값. {@code AttributeInstance.getBaseValue()} 이며 플레이어는 1.0 이다
 * @param current 지금 값. {@code AttributeInstance.getValue()} 라 증강 수정자와 <b>손에 든
 *                무기</b>가 모두 들어 있다. 마법부여와 치명타는 이 속성 밖에서 더해지므로
 *                들어 있지 않다
 */
public record AttackDamagePayload(float base, float current) implements CustomPacketPayload {

	public static final Type<AttackDamagePayload> TYPE =
			new Type<>(SharedFateMod.id("attack_damage"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AttackDamagePayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.FLOAT, AttackDamagePayload::base,
					ByteBufCodecs.FLOAT, AttackDamagePayload::current,
					AttackDamagePayload::new);

	public AttackDamagePayload {
		base = sanitize(base);
		current = sanitize(current);
	}

	/**
	 * 무한대·NaN·음수를 0 으로 눕힌다.
	 *
	 * <p>바닐라 {@code attack_damage} 는 0 아래로 내려가지 않게 이미 묶여 있지만, 다른 모드가
	 * 속성 범위를 바꿔 놓았을 때 화면이 「1 → -Infinity」 같은 글자를 그리는 일만은 막는다.
	 */
	private static float sanitize(float value) {
		if (!Float.isFinite(value)) {
			return 0.0F;
		}
		return Math.max(0.0F, value);
	}

	/**
	 * 지난번에 보낸 것과 <b>화면이 다르게 그릴 만큼</b> 다른가.
	 *
	 * <p>기준을 {@link StatSummary#changed} 로 잡은 이유는, 화면이 같은 글자를 그릴 값이라면
	 * 패킷을 쓸 이유가 없기 때문이다. 「무엇이 달라진 것인가」의 답이 화면과 네트워크에서
	 * 갈라지지 않도록 한 곳에서만 정한다.
	 *
	 * @param sent 지난번에 보낸 것. 아직 한 번도 보내지 않았으면 null
	 */
	public boolean differsFrom(@Nullable AttackDamagePayload sent) {
		return sent == null
				|| StatSummary.changed(sent.base, base)
				|| StatSummary.changed(sent.current, current);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
