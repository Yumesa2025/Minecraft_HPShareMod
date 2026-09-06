package com.sharedfate.mixin;

import com.sharedfate.perk.PerkBlockBreaks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code drop_replace} 증강이 걸린 블록의 전리품을 모드가 대신 떨어뜨린다.
 *
 * <p>규칙과 실제 처리는 {@link PerkBlockBreaks#replaceDrops} 에 있다. 여기는 「어디서
 * 가로채는가」만 정한다.
 *
 * <h2>왜 여기여야 하는가</h2>
 * <p>이 모드가 이미 쓰고 있는 {@code PlayerBlockBreakEvents.AFTER} 로는 전리품을 <b>없앨 수
 * 없다.</b> Fabric 이 그 사건을 부르는 자리는 {@code ServerPlayerGameMode.destroyBlock} 안의
 * {@code Block.destroy(...)} 호출 <b>직전</b>이고, 전리품을 실제로 떨어뜨리는
 * {@code Block.playerDestroy} 는 그보다 <b>뒤</b>에 불린다. 즉 그 자리에서는 「앞으로 떨어질
 * 것」에 손을 댈 방법이 없다. 덤을 얹는 {@code bonus_drop} 은 그래서 그 자리로 충분하지만,
 * 「밀 대신」처럼 원래 것이 사라져야 하는 요구는 전리품을 만드는 자리를 잡아야 한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다. 서술자를 못박는 시험은
 * {@code DropReplaceTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/level/block/Block.class
 *
 * public void playerDestroy(Level, Player, BlockPos, BlockState, BlockEntity, ItemStack);
 *     27: invokestatic  // dropResources:(BlockState;Level;BlockPos;BlockEntity;Entity;ItemStack;)V
 *
 * public static void dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack);
 *      1: instanceof    // ServerLevel — 클라이언트에서는 통째로 건너뛴다
 *     22: invokestatic  // getDrops(BlockState;ServerLevel;BlockPos;BlockEntity;Entity;ItemInstance;)
 *     32: invokeinterface // List.forEach → popResource(Level;BlockPos;ItemStack;)
 *     44: invokevirtual // BlockState.spawnAfterBreak:(ServerLevel;BlockPos;ItemStack;Z)V
 *     47: return
 * }</pre>
 *
 * <p>여기서 두 가지가 확인된다.
 * <ul>
 *   <li>사람이 캔 블록의 전리품은 전부 이 여섯 인자짜리 {@code dropResources} 한 곳으로 모인다.
 *       {@code playerDestroy} 가 부르는 것이 이것 하나뿐이다.</li>
 *   <li>이 메서드가 하는 일은 <b>전리품 목록을 떨어뜨리는 것과 {@code spawnAfterBreak} 를
 *       부르는 것</b> 둘뿐이다. 그래서 취소하고 대신 처리할 때 흉내 낼 것이 정확히 그 둘이고,
 *       {@link PerkBlockBreaks#replaceDrops} 가 같은 순서로 그 둘을 한다.</li>
 * </ul>
 *
 * <h2>여기에 걸리지 않는 것</h2>
 * <p>증강이 스스로 부수는 블록({@code echo_mining}·{@code same_kind_mining})은 전리품을
 * {@code Block.getDrops} 로 직접 굴려 떨어뜨리므로 이 메서드를 지나지 않는다. 그쪽은
 * {@link PerkBlockBreaks} 안에서 같은 규칙을 따로 적용한다.
 *
 * <p>폭발·피스톤처럼 사람이 아닌 파괴는 인자의 {@code Entity} 가 캔 사람이 아니므로
 * {@link PerkBlockBreaks#replaceDrops} 가 곧바로 돌아간다. 어느 팀의 증강인지 알 수 없으니
 * 이대로가 맞다.
 */
@Mixin(Block.class)
public abstract class BlockDropReplaceMixin {
	@Inject(
			method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;"
					+ "Lnet/minecraft/world/level/Level;"
					+ "Lnet/minecraft/core/BlockPos;"
					+ "Lnet/minecraft/world/level/block/entity/BlockEntity;"
					+ "Lnet/minecraft/world/entity/Entity;"
					+ "Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"), cancellable = true)
	private static void sharedfate$replaceDrops(BlockState state, Level level, BlockPos pos,
			BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo callback) {
		if (PerkBlockBreaks.replaceDrops(state, level, pos, blockEntity, entity, tool)) {
			callback.cancel();
		}
	}
}
