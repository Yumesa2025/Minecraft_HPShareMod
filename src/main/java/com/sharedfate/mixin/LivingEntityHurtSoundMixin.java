package com.sharedfate.mixin;

import com.sharedfate.sync.SpreadDamageManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「완충」이 미뤄 둔 몫을 넣을 때 <b>피격 소리를 삼킨다.</b>
 *
 * <h2>왜 필요한가</h2>
 * <p>완충은 한 번 받은 피해를 여러 초에 걸쳐 나눠 넣는다. 넣는 방법이
 * {@code hurtServer} 를 다시 부르는 것이라, 그대로 두면 <b>몫마다</b> 피격 소리가 나고 화면이
 * 붉어진다. 여덟 몫이면 여덟 번 맞은 것처럼 들려, 「완충되고 있다」가 아니라 「계속 맞고
 * 있다」로 읽힌다.
 *
 * <p>화면이 붉어지는 것({@code hurtTime}·{@code hurtDuration})은
 * {@link SpreadDamageManager} 가 값을 되돌리는 것으로 막지만, <b>소리는 그 자리에서 이미
 * 나가 버려</b> 되돌릴 수가 없다. 그래서 나가기 전에 여기서 막는다.
 *
 * <h2>완충 몫일 때만 막는다</h2>
 * <p>{@link SpreadDamageManager#isDeliveringSlice()} 는 <b>미뤄 둔 몫을 넣는 그 순간에만</b>
 * 참이고, 큐가 하나도 없으면 첫 줄에서 곧바로 거짓이다. 이 증강을 아무도 갖고 있지 않은
 * 서버에서는 사실상 비용이 없다.
 *
 * <p><b>처음 맞은 그 한 번은 그대로 소리가 난다.</b> 가로채는 자리는 원래 피해가 아니라
 * 나중에 넣는 몫이기 때문이다 — 맞은 사실 자체는 알아야 한다.
 *
 * <h2>대상</h2>
 * <p>26.2 바이트코드로 확인했다 — {@code LivingEntity.playHurtSound(DamageSource)} 는
 * {@code protected void} 다. refmap 이 없으므로 대상이 틀리면 <b>빌드는 통과하고 실제로
 * 맞는 순간 터진다.</b> 그것을 못박는 시험이 {@code HurtSoundTargetTest} 다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtSoundMixin {

	@Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
	private void sharedfate$silenceSpreadSlice(DamageSource source, CallbackInfo info) {
		if (SpreadDamageManager.isDeliveringSlice()) {
			info.cancel();
		}
	}
}
