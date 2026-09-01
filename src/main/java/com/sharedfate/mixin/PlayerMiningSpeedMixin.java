package com.sharedfate.mixin;

import com.sharedfate.perk.PerkBlockBreaks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code mining_speed} 증강이 채굴 속도에 끼어드는 자리.
 *
 * <p>26.2 에서 "이 플레이어가 이 블록을 얼마나 빨리 캐는가"가 한 숫자로 정해지는 곳은
 * {@code Player.getDestroySpeed(BlockState)} 하나다. 도구 등급·효율 마법·성급함·채굴 피로·
 * 물속·공중까지 전부 여기서 합쳐지고, 진행도를 세는 쪽
 * ({@code BlockStateBase.getDestroyProgress} → {@code ServerPlayerGameMode.tick})은 그 결과만
 * 받아 간다. 그래서 여기 한 곳만 잡으면 모든 경로가 같은 값을 본다.
 *
 * <p>RETURN 에 붙는 이유는 바닐라가 계산을 다 끝낸 값에 배율을 곱해야 하기 때문이다. HEAD 에서
 * 끼어들면 도구·마법·상태이상이 아직 반영되지 않은 값을 보게 된다.
 *
 * <h2>클라이언트에서는 아무 일도 하지 않는다</h2>
 * <p>이 mixin 은 공용 설정에 들어 있어 클라이언트의 {@code LocalPlayer} 에도 걸린다. 하지만
 * {@link PerkBlockBreaks#scaleDestroySpeed} 가 {@code ServerPlayer} 가 아닌 플레이어에게는
 * 원래 값을 그대로 돌려주므로 클라이언트 계산은 바닐라 그대로다.
 *
 * <p>그래서 전용 서버에서는 클라이언트가 서버보다 빨리 "다 캤다"고 판단한다. 이때 바닐라는
 * 블록을 없애 주지 않고 {@code ServerPlayerGameMode} 가 {@code hasDelayedDestroy} 로 자기
 * 진행도를 마저 채운 뒤 부순다. 즉 <b>블록은 늦게, 그러나 반드시 부서진다.</b> 대신 클라이언트
 * 화면에서 금이 한 번 되돌아갔다가 다시 부서지는 것이 보인다. 이것을 없애려면 보유 증강을
 * 클라이언트까지 내려보내야 하는데, 지금 내려가는 {@code PerkSyncPayload} 는 표시용 문자열만
 * 담고 있어 증강 id 를 알 수 없다. 배율을 크게 잡을수록 눈에 띄므로 정의 파일에서 0.5 아래로는
 * 내리지 않는 편이 좋다.
 *
 * <h2>그래서 이 길로는 빠르게 할 수 없다</h2>
 * <p>위 문단은 <b>느려지는 쪽</b>에서만 성립한다. 26.2 의 {@code ServerPlayerGameMode.tick} 은
 * {@code isDestroyingBlock} 분기에서 {@code incrementDestroyProgress} 의 결과를 <b>버린다</b>
 * (바이트코드에서 {@code pop}). 서버가 스스로 블록을 부수는 자리는 {@code START} 시점의 즉시
 * 파괴, 클라이언트의 {@code STOP} 을 받았을 때, 그리고 {@code hasDelayedDestroy} 셋뿐이다.
 * <b>파괴 시점은 전적으로 클라이언트의 {@code STOP_DESTROY_BLOCK} 에 달려 있다.</b> 서버가
 * 아무리 빨라져도 클라이언트가 바닐라 속도로 다 캘 때까지 아무 일도 일어나지 않는다.
 *
 * <p>빠르게 하려면 {@code mining_speed} 가 아니라 <b>{@code minecraft:block_break_speed}
 * 속성</b>을 써야 한다. 그 속성은 {@code setSyncable(true)} 라 클라이언트까지 자동으로
 * 내려가고, {@code Player.getDestroySpeed} 안에서 바닐라가 직접 곱한다. 자세한 것은
 * {@link com.sharedfate.perk.effect.MiningSpeedEffect} 의 클래스 주석에 있다.
 */
@Mixin(Player.class)
public abstract class PlayerMiningSpeedMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void sharedfate$applyPerkMiningSpeed(BlockState state,
			CallbackInfoReturnable<Float> callback) {
		float original = callback.getReturnValueF();
		float scaled = PerkBlockBreaks.scaleDestroySpeed((Player) (Object) this, state, original);
		if (scaled != original) {
			callback.setReturnValue(scaled);
		}
	}
}
