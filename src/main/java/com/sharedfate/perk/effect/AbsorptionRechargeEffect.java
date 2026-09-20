package com.sharedfate.perk.effect;

import com.google.gson.JsonObject;
import com.sharedfate.SharedFateMod;
import com.sharedfate.perk.PerkEffect;
import com.sharedfate.perk.PerkEffectType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 주기마다 <b>꽉 차는</b> 흡수 보호막.
 *
 * <p>정의는 이렇다.
 *
 * <pre>{@code
 * { "type": "absorption_recharge", "amplifier": 1, "period_ticks": 6000 }
 * }</pre>
 *
 * <p>등급 1이면 흡수 II, 곧 흡수량 8(=4하트)이다. 5분(6000틱)마다 그 보호막이 최대치로
 * 다시 찬다. 절반만 닳아 있어도 주기가 오면 가득 찬다.
 *
 * <h2>왜 {@code periodic} + {@code status_effect} 로는 안 되는가</h2>
 * <p>{@link PeriodicEffect} 는 구간이 끝나는 틱에 그 구간의 효과를 {@code remove} 한다. 흡수
 * 상태이상을 떼면 {@code minecraft:max_absorption} 수정자가 사라지고, 그 순간 남아 있던
 * 보호막이 통째로 0 이 된다. 그래서 「5초 동안만 흡수 II」밖에 표현할 수 없다. 원하는 것은
 * 「닳을 때까지 계속 달고 있다가 주기가 오면 다시 채운다」라서 구간이라는 개념 자체가 맞지 않는다.
 *
 * <h2>26.2 바닐라에서 확인한 사실</h2>
 * <p>설계가 이 둘 위에 서 있다. 둘 다 {@code minecraft-common-deobf-26.2.jar} 의 바이트코드로
 * 확인했다.
 *
 * <ul>
 *   <li>{@code LivingEntity.setAbsorptionAmount(float)} 는 값을
 *       {@code Mth.clamp(v, 0, getMaxAbsorption())} 로 <b>자른다.</b> 그리고
 *       {@code getMaxAbsorption()} 은 {@code minecraft:max_absorption} 속성값 그대로인데,
 *       플레이어의 기본값은 0 이다. 흡수 상태이상이 그 속성에 {@code ADD_VALUE 4 × (등급+1)} 을
 *       붙여 주는 것이 유일한 공급원이다({@code MobEffects} 의 등록,
 *       {@code MobEffect$AttributeTemplate.create} 가 {@code amount × (amplifier + 1)}).
 *       <b>상태이상 없이 흡수량만 올리면 조용히 0 으로 잘린다.</b> 그래서 채우기 전에 반드시
 *       상태이상부터 붙인다.</li>
 *   <li>{@code AbsorptionMobEffect.applyEffectTick} 은 {@code getAbsorptionAmount() > 0} 을
 *       그대로 돌려주고, {@code shouldApplyEffectTickThisTick} 은 늘 {@code true} 다. 곧
 *       <b>보호막이 0 이 되면 상태이상이 다음 틱에 스스로 사라진다.</b> 「다 닳으면 그 자리에서
 *       다시 생기지 않는다」를 우리가 따로 지킬 필요가 없다 — 바닐라가 알아서 벗겨 주고, 우리는
 *       다음 주기가 올 때까지 다시 붙이지 않기만 하면 된다.</li>
 *   <li>{@code AbsorptionMobEffect.onEffectStarted} 는
 *       {@code setAbsorptionAmount(max(현재, 4 × (1 + 등급)))} 을 한다. 상태이상을 붙이는 순간
 *       <b>그 사람의</b> 흡수량이 곧바로 최대치로 뛴다는 뜻이다. 팀 공유 풀과 어긋나므로
 *       붙인 직후에 팀 값으로 다시 맞춰 줘야 한다 — 그 일은
 *       {@link com.sharedfate.sync.AbsorptionRechargeManager} 가 한다.</li>
 * </ul>
 *
 * <h2>{@link #apply} 가 아무 일도 하지 않는 이유</h2>
 * <p><b>이 클래스에서 가장 중요한 줄이 "아무 것도 하지 않는다"이다.</b>
 * {@code PerkManager.refreshPlayer} 는 접속·부활·세트 변동·상태이상 재적용마다 보유 증강의
 * {@code apply} 를 전부 다시 부른다. 게다가 보호막이 다 닳아 상태이상이 스스로 벗겨지면
 * {@code EffectSync.onRemoved} 가 「증강분이 벗겨졌다」로 보고 그 사람을 재적용 대기열에
 * 넣는다. 여기서 보호막을 붙이면 <b>닳자마자 다음 틱에 도로 가득 차서</b> 「다 닳으면 다음
 * 주기까지 없는 채로 버틴다」가 통째로 무너진다. 그래서 붙이는 일은 오직 주기를 세는
 * 매니저만 한다.
 *
 * <h2>최상위에만 놓을 수 있다</h2>
 * <p>{@code conditional}·{@code holder}·{@code periodic} 의 하위로 들어가면 순번이
 * {@value #CHILD_INDEX_STRIDE} 이상이 된다. 매니저는 증강의 <b>최상위</b> 효과만 훑으므로
 * 하위로 들어간 것은 아무도 주기를 돌려 주지 않아 영영 멈춰 있다. {@link PeriodicEffect} 와
 * 같은 이유로 정의를 읽을 때 걸러 낸다.
 */
public final class AbsorptionRechargeEffect implements PerkEffect {
	/** 흡수 상태이상의 이름. */
	public static final Identifier ABSORPTION = Identifier.withDefaultNamespace("absorption");

	/**
	 * 등급 한 칸이 주는 흡수량. 26.2 의 {@code MobEffects} 등록값 4.0 과
	 * {@code AbsorptionMobEffect.onEffectStarted} 의 {@code 4 * (1 + amplifier)} 가 같은 값이다.
	 */
	public static final float ABSORPTION_PER_LEVEL = 4.0F;

	/**
	 * 등급 상한. 바닐라 상한(255)이 아니라 훨씬 낮게 잡는다. 등급 9 면 흡수량 40(20하트)이라
	 * 어떤 증강에도 충분하고, 자릿수를 잘못 적은 정의를 여기서 잡아 준다.
	 */
	private static final int MAX_AMPLIFIER = 9;

	/** 주기 상한. {@link PeriodicEffect} 와 같은 한 시간이다. */
	private static final int MAX_PERIOD_TICKS = 72_000;

	/** 주기 하한. 1초보다 짧으면 「닳으면 다음 주기까지 버틴다」가 뜻을 잃는다. */
	private static final int MIN_PERIOD_TICKS = 20;

	/** 하위 효과에 주는 순번의 간격. {@link PeriodicEffect} · {@link ConditionalEffect} 와 같다. */
	private static final int CHILD_INDEX_STRIDE = 100;

	private final int amplifier;
	private final int periodTicks;

	/**
	 * 이 증강이 거는 흡수 상태이상.
	 *
	 * <p>{@link StatusEffectPerk} 를 품는 이유는 두 가지다. 상태이상을 찾고 인스턴스를 만드는
	 * 방식이 다른 증강과 한 글자도 다르지 않아야 하고, {@code PerkStatusEffects} 가
	 * 「증강이 건 상태이상」을 모을 때 {@link #statusEffects()} 로 이것을 그대로 가져가야
	 * 하기 때문이다. 그쪽에 안 잡히면 이 흡수가 {@code EffectSync} 의 팀 공유 풀로 새어 나가
	 * 증강을 잃은 뒤에도 되살아난다.
	 */
	private final StatusEffectPerk shield;

	public AbsorptionRechargeEffect(int amplifier, int periodTicks) {
		this.amplifier = amplifier;
		this.periodTicks = periodTicks;
		this.shield = new StatusEffectPerk(ABSORPTION, amplifier);
	}

	// ------------------------------------------------------------------ 정의 읽기

	/** JSON에서 만든다. 정의가 잘못됐으면 경고를 남기고 null. */
	public static @Nullable PerkEffect fromJson(String perkId, int index, JsonObject json) {
		int amplifier = PerkEffectType.readInt(json, "amplifier", 0);
		if (amplifier < 0 || amplifier > MAX_AMPLIFIER) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: absorption_recharge 의 amplifier 가 범위를 벗어났습니다 ({})",
					perkId, amplifier);
			return null;
		}

		int periodTicks = PerkEffectType.readInt(json, "period_ticks", 0);
		if (periodTicks < MIN_PERIOD_TICKS || periodTicks > MAX_PERIOD_TICKS) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: absorption_recharge 의 period_ticks 가 없거나 범위를 벗어났습니다 ({})",
					perkId, periodTicks);
			return null;
		}

		if (index < 0 || index >= CHILD_INDEX_STRIDE) {
			SharedFateMod.LOGGER.warn(
					"증강 {}: absorption_recharge 는 최상위에만 놓을 수 있습니다 (순번 {})", perkId, index);
			return null;
		}

		return new AbsorptionRechargeEffect(amplifier, periodTicks);
	}

	// ------------------------------------------------------------------ 조회

	public int amplifier() {
		return amplifier;
	}

	public int periodTicks() {
		return periodTicks;
	}

	/** 가득 찼을 때의 흡수량. 등급 1이면 8(=4하트)이다. */
	public float shieldAmount() {
		return ABSORPTION_PER_LEVEL * (amplifier + 1);
	}

	/**
	 * 이 효과가 품은 상태이상.
	 *
	 * <p>{@code PerkStatusEffects.statusEffectsIn} 이 부른다. 거기에 이 갈래를 적어 두지 않으면
	 * 흡수가 팀 공유 상태이상 풀로 새어 나간다.
	 */
	public List<StatusEffectPerk> statusEffects() {
		return List.of(shield);
	}

	// ------------------------------------------------------------------ 붙이고 떼기

	/**
	 * <b>아무 일도 하지 않는다.</b> 까닭은 이 클래스 문서의 「{@link #apply} 가 아무 일도 하지
	 * 않는 이유」에 적어 뒀다. 보호막을 채우는 유일한 길은
	 * {@link com.sharedfate.sync.AbsorptionRechargeManager} 다.
	 */
	@Override
	public void apply(ServerPlayer player) {
	}

	/** 증강을 잃거나 팀에서 빠질 때 보호막 상태이상을 걷어낸다. */
	@Override
	public void remove(ServerPlayer player) {
		stripShield(player, amplifier);
	}

	/**
	 * 보호막 상태이상을 붙인다. 이미 우리 것이 붙어 있으면 아무 일도 하지 않는다.
	 *
	 * <p>붙는 순간 바닐라 {@code onEffectStarted} 가 <b>이 사람의</b> 흡수량을 최대치로 올린다.
	 * 팀 공유 값과 어긋나므로 부르는 쪽이 곧바로 팀 값으로 다시 맞춰야 한다.
	 *
	 * @return 이번에 새로 붙였으면 true
	 */
	public boolean grantShield(@Nullable ServerPlayer player) {
		if (player == null || hasShield(player)) {
			return false;
		}
		MobEffectInstance granted = shield.grantedInstance();
		if (granted == null) {
			return false;
		}
		player.addEffect(granted);
		return true;
	}

	/** 이 사람에게 우리 보호막이 붙어 있는가. */
	public boolean hasShield(@Nullable ServerPlayer player) {
		if (player == null) {
			return false;
		}
		Holder<MobEffect> resolved = shield.resolvedEffect();
		return resolved != null && isPerkShield(player.getEffect(resolved), amplifier);
	}

	/**
	 * 증강분 흡수 상태이상만 골라 걷어낸다.
	 *
	 * <p>판별 기준은 {@code PerkStatusEffects.grants} 와 같다 — 무한 지속이고 등급이 우리 것
	 * 이하일 때만 우리 것이다. 황금 사과나 「전리품 방패」가 준 <b>유한 지속</b> 흡수는 건드리지
	 * 않는다. 종류가 같아도 원인이 다르면 남의 것이고, 그걸 함께 지우면
	 * {@code EffectSync} 가 그 제거를 팀 전원에게 퍼뜨려 남의 보호막까지 함께 사라진다.
	 *
	 * @param amplifier 여기까지가 우리 것이다. 증강을 이미 잃어 등급을 모르는 자리에서는
	 *                  {@link #MAX_AMPLIFIER} 를 넘기면 된다 — 무한 지속 흡수는 증강 말고
	 *                  걸어 주는 것이 없다
	 */
	public static void stripShield(@Nullable ServerPlayer player, int amplifier) {
		if (player == null) {
			return;
		}
		Holder<MobEffect> resolved = resolveAbsorption();
		if (resolved == null) {
			return;
		}
		if (isPerkShield(player.getEffect(resolved), amplifier)) {
			player.removeEffect(resolved);
		}
	}

	/** 등급을 모르는 자리에서 증강분 흡수를 걷어낸다. {@link #stripShield} 의 얇은 껍데기다. */
	public static void stripAnyShield(@Nullable ServerPlayer player) {
		stripShield(player, MAX_AMPLIFIER);
	}

	/** 등급 상한. 정의 검사에도 쓰고, 등급을 모르는 자리의 걷어내기 기준으로도 쓴다. */
	public static int maxAmplifier() {
		return MAX_AMPLIFIER;
	}

	private static boolean isPerkShield(@Nullable MobEffectInstance current, int amplifier) {
		return current != null && current.isInfiniteDuration()
				&& current.getAmplifier() <= amplifier;
	}

	/**
	 * 흡수 상태이상 홀더. 레지스트리가 준비되기 전에는 null 이다.
	 *
	 * <p>{@link StatusEffectPerk} 와 같은 이유로 미리 붙잡아 두지 않는다 — 정의를 읽는 시점에는
	 * 레지스트리가 아직 없을 수 있다. 인스턴스 쪽은 {@link StatusEffectPerk#resolvedEffect()}
	 * 의 캐시를 쓰고, 정적 도우미만 여기서 직접 찾는다.
	 */
	private static @Nullable Holder<MobEffect> resolveAbsorption() {
		Optional<Holder.Reference<MobEffect>> found = BuiltInRegistries.MOB_EFFECT.get(ABSORPTION);
		return found.isPresent() ? found.get() : null;
	}
}
