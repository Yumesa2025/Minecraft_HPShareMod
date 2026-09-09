package com.sharedfate.mixin;

import com.sharedfate.sync.SanctuaryManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 프리즘 「성역」이 몹의 <b>틱 자체</b>를 건너뛰거나 한 번 더 돌리는 자리.
 *
 * <p>「행동이 느려진다」는 이동 속도만이 아니다. 공격 간격·크리퍼 부풀기·활 쏘기가 전부 함께
 * 느려져야 한다. 그 넷은 바닐라에서 서로 다른 곳에 흩어져 있다.
 *
 * <ul>
 *   <li>이동 — {@code Mob.aiStep} 아래의 이동 제어</li>
 *   <li>근접 공격 간격 — {@code MeleeAttackGoal} 의 goal 갱신</li>
 *   <li>활 쏘기 — {@code RangedAttackGoal.attackTime}, 역시 goal 갱신</li>
 *   <li>크리퍼 부풀기 — {@code Creeper.swell}, <b>{@code Creeper.tick()} 안</b></li>
 * </ul>
 *
 * <p>이 넷을 따로 건드리면 손댈 자리가 넷이고 새 몹이 생길 때마다 늘어난다. 대신
 * <b>몹의 틱을 통째로 확률로 건너뛰면</b> 넷이 한 자리에서 같은 비율로 느려진다. 40% 감속이면
 * 틱의 40%를 건너뛴다.
 *
 * <h2>왜 {@code ServerLevel.tickNonPassenger} 인가 — 여기가 아니면 크리퍼가 안 느려진다</h2>
 * <p>처음에는 {@code Mob.tick()} 을 자르려 했으나 26.2 바이트코드를 읽고 <b>버렸다.</b>
 * {@code Creeper.tick()} 은 이렇게 생겼다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/entity/monster/Creeper.class
 *
 * public void tick();
 *      0: aload_0 / invokevirtual isAlive:()Z
 *      7: …  swell 을 oldSwell 에 넣고 swellDir 만큼 더한다
 *     93: …  swell >= maxSwell 이면 explodeCreeper:()V
 *    105: aload_0
 *    106: invokespecial net/minecraft/world/entity/monster/Monster.tick:()V   ← super 는 맨 끝
 *    109: return
 * }</pre>
 *
 * <p>부풀기가 {@code super.tick()} <b>보다 먼저</b> 끝난다. 즉 {@code Mob.tick()} 에서 취소해
 * 봐야 그 틱의 부풀기는 이미 지나간 뒤다. 크리퍼만이 아니라 {@code tick()} 을 재정의해 자기 일을
 * 먼저 하는 몹은 모두 같다. <b>재정의보다 위</b>에서 잘라야 한다.
 *
 * <p>그 자리가 {@code ServerLevel.tickNonPassenger(Entity)} 다. 서버가 엔티티 하나에게 「이번
 * 틱을 주는」 유일한 입구이고, 여기서 취소하면 {@code Creeper.tick()} 이 <b>시작조차 하지
 * 않는다.</b> 26.2 바이트코드로 확인한 본문은 이렇다.
 *
 * <pre>{@code
 * public void tickNonPassenger(Entity);
 *      0: Entity.setOldPosAndRot:()V
 *      8: entity.tickCount++
 *     47: Entity.tick:()V            ← 여기서 Creeper.tick 이 가상 호출된다
 *     57: getPassengers → tickPassenger(…)  ← 태우고 있는 것도 이때 돈다
 *     97: return
 * }</pre>
 *
 * <h2>고른 자리의 부작용 — 확인한 것</h2>
 * <ul>
 *   <li><b>물리·중력도 함께 멈춘다.</b> 건너뛴 틱에는 낙하도 밀림도 없다. 속도는 그대로
 *       남아 있으므로 공중에 영영 뜨지는 않고 <b>40% 느리게 떨어질</b> 뿐이다. 「몹의 시간이
 *       느려진다」로 읽으면 오히려 앞뒤가 맞는다.</li>
 *   <li><b>{@code tickCount} 도 늘지 않는다.</b> 위 바이트코드의 8번 줄이 건너뛰는 대상 안에
 *       있다. {@code tickCount % N} 으로 도는 몹의 주기 행동까지 같은 비율로 느려진다 —
 *       빠뜨리면 안 되는 자리를 덤으로 덮는 셈이라 이대로 둔다.</li>
 *   <li><b>무적 시간도 느리게 준다.</b> {@code LivingEntity.baseTick} 의
 *       {@code invulnerableTime} 감소가 함께 건너뛰이므로, 느려진 몹은 피격 무적이 그만큼
 *       오래간다. 공격 속도 1.6짜리 검(0.625초)은 무적 0.5초가 0.83초로 늘어 초당 피해가
 *       조금 줄고, 도끼·활처럼 원래 0.83초보다 느린 무기는 영향이 없다. <b>알면서 그대로
 *       둔다</b> — 여기만 예외로 되돌리면 「틱을 건너뛴다」라는 한 줄짜리 규칙이 깨지고,
 *       몹이 40% 덜 때리고 40% 느리게 다가오는 이득이 이 손해보다 훨씬 크다.</li>
 *   <li><b>불·독·질식·디스폰도 같은 비율로 느려진다.</b> 전부 건너뛴 틱 안에 있다. 몹에게
 *       흐르는 시간이 통째로 느려지는 것이라 서로 어긋나지 않는다.</li>
 *   <li><b>탈것에 탄 몹은 탈것을 따라간다.</b> 위 57번 줄이 태우고 있는 것을 함께 돌리므로
 *       거미에 탄 스켈레톤은 거미와 같이 느려진다. 반대로 <b>보트·광산 수레에 탄 몹</b>은
 *       탈것이 {@code Mob} 이 아니라 느려지지 않는다. 드문 경우라 그대로 둔다.</li>
 *   <li><b>플레이어는 손대지 않는다.</b> 판정
 *       ({@code AuraDamageManager.hostile})이 {@code Mob} 이면서 {@code Enemy} 인 것만
 *       통과시킨다. 엔더 드래곤은 양쪽 모두 제외다.</li>
 * </ul>
 *
 * <h2>비용</h2>
 * <p>이 주입은 <b>모든 엔티티가 매 틱</b> 지난다. 그래서 두 진입점 모두 첫 줄이
 * {@code volatile boolean} 한 번 읽기로 끝난다({@code SanctuaryManager.active}). 성역을 가진
 * 팀이 없는 서버에서는 그 한 줄이 비용의 전부다. 나머지 계산이 어떻게 접히는지는
 * {@link SanctuaryManager} 주석에 적어 두었다.
 *
 * <h2>재진입 막기</h2>
 * <p>가속은 <b>같은 메서드를 다시 부르는 것</b>이라 이 주입이 또 걸린다. 깃발을 세워 두 번째
 * 호출에서는 아무 일도 하지 않게 한다 — 그래야 한 틱에 최대 한 번만 더 돈다. 엔티티 틱은
 * 서버 스레드 하나에서만 도는 자리라 깃발 하나면 충분하고, {@code finally} 로 반드시 내린다.
 * {@code NaturalSpawnerRateMixin} 이 추가 스폰을 도는 방식 그대로다.
 *
 * <p>refmap 이 없어 대상 서술자가 틀리면 <b>빌드는 통과하고 서버가 뜬 뒤 첫 틱에 터진다.</b>
 * 그것을 못박는 시험이 {@code SanctuaryTargetTest} 다.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSanctuaryTickMixin {

	/** 가속으로 한 번 더 도는 동안 켜 둔다. 켜져 있으면 이 주입들은 아무 일도 하지 않는다. */
	@Unique
	private static boolean sharedfate$extraTick;

	/**
	 * 성역 안의 몹이면 이번 틱을 통째로 건너뛴다.
	 *
	 * <p>{@code Entity.tick()} 이 가상 호출되기 전이므로 {@code Creeper.tick()} 의 부풀기도
	 * 함께 멈춘다. 바로 이것 때문에 이 자리를 골랐다.
	 */
	@Inject(
			method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
			at = @At("HEAD"), cancellable = true)
	private void sharedfate$slowMobsInSanctuary(Entity entity, CallbackInfo callbackInfo) {
		if (sharedfate$extraTick) {
			return;
		}
		if (SanctuaryManager.shouldSkipTick((ServerLevel) (Object) this, entity)) {
			callbackInfo.cancel();
		}
	}

	/**
	 * 팀이 흩어져 있는 동안의 대가. 확률에 걸린 몹은 이번 틱을 한 번 더 돈다.
	 *
	 * <p>{@code HEAD} 에서 취소된 틱에는 이 자리에 오지 않는다. 취소는 본문이 시작하기 전에
	 * 돌아가므로 본문 끝의 {@code RETURN} 을 지날 일이 없기 때문이다. 즉 <b>같은 틱에 건너뛰기와
	 * 한 번 더 돌기가 함께 일어나지 않는다.</b>
	 */
	@Inject(
			method = "tickNonPassenger(Lnet/minecraft/world/entity/Entity;)V",
			at = @At("RETURN"))
	private void sharedfate$hasteMobsWhileApart(Entity entity, CallbackInfo callbackInfo) {
		if (sharedfate$extraTick) {
			return;
		}
		ServerLevel level = (ServerLevel) (Object) this;
		if (!SanctuaryManager.shouldRunExtraTick(level, entity)) {
			return;
		}
		sharedfate$extraTick = true;
		try {
			// 방금 돈 틱에서 죽었거나 사라졌으면 한 번 더 돌리지 않는다.
			if (!entity.isRemoved()) {
				level.tickNonPassenger(entity);
			}
		} finally {
			sharedfate$extraTick = false;
		}
	}
}
