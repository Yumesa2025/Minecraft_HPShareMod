package com.sharedfate.net;

import com.sharedfate.SharedFateMod;
import com.sharedfate.ui.StatSummary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * S2C — 능력치 화면이 그려야 하지만 <b>클라이언트가 스스로는 알 수 없는</b> 값들.
 *
 * <h2>여기 실리는 것과 실리지 않는 것</h2>
 * <ul>
 *   <li><b>공격력</b>({@code minecraft:attack_damage}) — 바닐라가 이 속성만은 클라이언트에
 *       동기화하지 않는다. {@code Attributes} 등록부에서 이 속성만 {@code setSyncable(true)}
 *       없이 등록되고, 장비의 속성 수정자를 실제로 붙이는
 *       {@code LivingEntity.detectEquipmentUpdates} 도 {@code !level.isClientSide} 안에서만
 *       돈다. 그래서 클라이언트가 스스로 읽는 공격력은 <b>맨손 기본값 1.0</b> 뿐이다.</li>
 *   <li><b>받는 피해 배율</b> — 증강이 만드는 값이라 바닐라 속성이 아니다. 서버의
 *       {@code PerkManager.damageTakenMultiplier} 가 유일한 출처다.</li>
 *   <li><b>몹 최대 체력·공격력 배율</b> — 증강({@code mob_health}/{@code mob_damage})과
 *       「난이도 상승」이 함께 만든다. 몹에게 붙는 값이라 사람의 속성에는 흔적이 없다.</li>
 *   <li><b>공격 속도</b>는 여기 없다. {@code minecraft:attack_speed} 는 공격력과 달리
 *       {@code setSyncable(true)} 로 등록되어 <b>수정자까지 그대로 클라이언트에 온다.</b>
 *       이미 있는 값을 또 보내면 두 값이 어긋날 자리만 생긴다.</li>
 * </ul>
 *
 * @param attackDamageBase    공격력의 바닐라 기본값. {@code getBaseValue()} 이며 플레이어는 1.0
 * @param attackDamageCurrent 공격력의 지금 값. {@code getValue()} 라 증강 수정자와 <b>손에 든
 *                            무기</b>가 모두 들어 있다. 마법부여와 치명타는 이 속성 밖에서
 *                            더해지므로 들어 있지 않다
 * @param damageTaken         팀원이 받는 피해에 곱해지는 배율. 아무것도 안 걸렸으면 1.0.
 *                            피해 종류를 가리는 {@code damage_taken_from} 은 들어 있지 않다
 * @param mobHealth           적대적 몹의 최대 체력에 곱해지는 배율(증강 × 난이도 상승)
 * @param mobDamage           적대적 몹이 주는 피해에 곱해지는 배율(증강 × 난이도 상승)
 */
public record StatSnapshotPayload(float attackDamageBase, float attackDamageCurrent,
		float damageTaken, float mobHealth, float mobDamage) implements CustomPacketPayload {

	public static final Type<StatSnapshotPayload> TYPE =
			new Type<>(SharedFateMod.id("stat_snapshot"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatSnapshotPayload> CODEC =
			StreamCodec.composite(
					ByteBufCodecs.FLOAT, StatSnapshotPayload::attackDamageBase,
					ByteBufCodecs.FLOAT, StatSnapshotPayload::attackDamageCurrent,
					ByteBufCodecs.FLOAT, StatSnapshotPayload::damageTaken,
					ByteBufCodecs.FLOAT, StatSnapshotPayload::mobHealth,
					ByteBufCodecs.FLOAT, StatSnapshotPayload::mobDamage,
					StatSnapshotPayload::new);

	public StatSnapshotPayload {
		attackDamageBase = sanitize(attackDamageBase, 0.0F);
		attackDamageCurrent = sanitize(attackDamageCurrent, 0.0F);
		// 배율은 0 이나 음수가 될 수 없다. 이상한 값이 오면 「아무것도 안 걸렸다」로 눕힌다 —
		// 화면이 「100% → 0%」 같은 없는 사실을 적는 것보다 낫다.
		damageTaken = sanitizeMultiplier(damageTaken);
		mobHealth = sanitizeMultiplier(mobHealth);
		mobDamage = sanitizeMultiplier(mobDamage);
	}

	/**
	 * 무한대·NaN·음수를 {@code fallback} 으로 눕힌다.
	 *
	 * <p>바닐라 {@code attack_damage} 는 0 아래로 내려가지 않게 이미 묶여 있지만, 다른 모드가
	 * 속성 범위를 바꿔 놓았을 때 화면이 「1 → -Infinity」 같은 글자를 그리는 일만은 막는다.
	 */
	private static float sanitize(float value, float fallback) {
		if (!Float.isFinite(value)) {
			return fallback;
		}
		return Math.max(0.0F, value);
	}

	/** 배율은 0 보다 커야 한다. 아니면 1.0. */
	private static float sanitizeMultiplier(float value) {
		return Float.isFinite(value) && value > 0.0F ? value : 1.0F;
	}

	/**
	 * 지난번에 보낸 것과 <b>화면이 다르게 그릴 만큼</b> 다른가.
	 *
	 * <p>기준을 {@link StatSummary#changed} 로 잡은 이유는, 화면이 같은 글자를 그릴 값이라면
	 * 패킷을 쓸 이유가 없기 때문이다. 「무엇이 달라진 것인가」의 답이 화면과 네트워크에서
	 * 갈라지지 않도록 한 곳에서만 정한다.
	 *
	 * <p>다섯 값을 모두 본다. 무기를 바꾸면 공격력이, 증강을 고르면 배율이 달라지는데 어느
	 * 하나만 보면 나머지가 바뀐 순간을 놓친다.
	 *
	 * @param sent 지난번에 보낸 것. 아직 한 번도 보내지 않았으면 null
	 */
	public boolean differsFrom(@Nullable StatSnapshotPayload sent) {
		return sent == null
				|| StatSummary.changed(sent.attackDamageBase, attackDamageBase)
				|| StatSummary.changed(sent.attackDamageCurrent, attackDamageCurrent)
				|| StatSummary.changed(sent.damageTaken, damageTaken)
				|| StatSummary.changed(sent.mobHealth, mobHealth)
				|| StatSummary.changed(sent.mobDamage, mobDamage);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
