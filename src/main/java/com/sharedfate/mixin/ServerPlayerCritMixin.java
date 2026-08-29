package com.sharedfate.mixin;

import com.sharedfate.perk.PerkTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 치명타에 성공한 순간을 잡아 {@code on_critical} 증강을 발동시킨다.
 *
 * <p>26.2 의 치명타 판정은 {@code Player.attack} 안에 있다. 순서는 이렇다.
 *
 * <ol>
 *   <li>{@code canCriticalAttack(target)} 이 참이면 피해에 1.5 를 곱하고 치명타 깃발을 세운다.</li>
 *   <li>{@code hurtOrSimulate(source, damage)} 로 실제 피해를 넣는다. 여기서 거짓이 나오면
 *       공격이 통하지 않은 것이라 아래 단계로 가지 않고 {@code PLAYER_ATTACK_NODAMAGE} 소리만
 *       난다.</li>
 *   <li>피해가 들어갔을 때만 {@code attackVisualEffects(...)} 를 부르고, 그 안에서 치명타
 *       깃발이 서 있을 때만 {@code this.crit(target)} 을 부른다.</li>
 * </ol>
 *
 * <p>그래서 {@code crit} 진입 시점은 "치명타로 실제 피해를 입혔다"와 정확히 같다. 판정을
 * 우리가 다시 계산하지 않아도 되고, 바닐라의 조건이 바뀌어도 따라간다.
 *
 * <h2>왜 {@code Player} 가 아니라 {@code ServerPlayer} 인가</h2>
 * <p>{@code Player.crit} 은 몸통이 비어 있고 {@code ServerPlayer} 가 이를 재정의해 치명타
 * 입자 꾸러미를 뿌린다. 그 재정의는 {@code super.crit} 을 부르지 않으므로,
 * {@code Player} 쪽에 걸면 서버 플레이어의 치명타에서는 영영 불리지 않는다.
 * ({@code Avatar} 는 {@code crit} 을 재정의하지 않는다.)
 *
 * <p>{@code magicCrit} 은 잡지 않는다. 그건 날카로움 같은 마법부여의 추가 피해 연출이지
 * 점프 치명타가 아니다.
 *
 * <p>{@link PerkTriggers} 가 팀·증강을 먼저 확인하므로, 증강을 쓰지 않는 서버에서는 여기가
 * 하는 일이 조회 두어 번뿐이다.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCritMixin {
	@Inject(method = "crit", at = @At("HEAD"))
	private void sharedfate$onCriticalHit(Entity target, CallbackInfo callback) {
		PerkTriggers.onCriticalHit((ServerPlayer) (Object) this);
	}
}
