package com.sharedfate.mixin;

import com.sharedfate.sync.ExperienceBonus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 광물에서 나오는 경험치에 세트 배율({@code experience_bonus}, {@code source: "ore"})을 먹인다.
 *
 * <p>규칙과 계산은 {@link ExperienceBonus} 에 있다. 여기는 「어디서 곱하는가」와 「누가 캤는지를
 * 어떻게 아는가」만 정한다.
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과한다.</b> 그래서 26.2 바이트코드를 직접 읽어 확인했다. 서술자를 못박는 시험은
 * {@code ExperienceSourceTargetTest} 에 있다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/level/block/Block.class
 *
 * public void playerDestroy(Level, Player, BlockPos, BlockState, BlockEntity, ItemStack);
 *      0: … Player.awardStat / causeFoodExhaustion
 *     27: invokestatic  // dropResources:(BlockState;Level;BlockPos;BlockEntity;Entity;ItemStack;)V
 *     30: return
 *
 * public static void dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack);
 *     22: invokestatic  // getDrops(...)  ← 아이템 전리품
 *     44: invokevirtual // BlockState.spawnAfterBreak:(ServerLevel;BlockPos;ItemStack;Z)V
 *
 * protected void tryDropExperience(ServerLevel, BlockPos, ItemStack, IntProvider);
 *     13: invokestatic  // EnchantmentHelper.processBlockExperience(...)I   ← 행운·섬세한 손길
 *     28: invokevirtual // popExperience:(Lnet/minecraft/server/level/ServerLevel;
 *                       //                Lnet/minecraft/core/BlockPos;I)V
 *
 * protected void popExperience(ServerLevel, BlockPos, int);
 *      7: … GameRules.BLOCK_DROPS 확인
 *     25: invokestatic  // ExperienceOrb.award:(ServerLevel;Vec3;I)V
 * }</pre>
 *
 * <p>여기서 세 가지가 확인된다.
 * <ul>
 *   <li>광석의 경험치는 {@code DropExperienceBlock.spawnAfterBreak} → {@code tryDropExperience}
 *       → {@code popExperience} 로 흐른다. 26.2 에는 {@code BlockBehaviour.getExpDrop} 이
 *       <b>없다</b> — 그 자리를 {@code tryDropExperience} 가 대신한다.</li>
 *   <li>{@code popExperience} 를 부르는 것은 공통 jar 전체에서 {@code Block}
 *       ({@code tryDropExperience}) · {@code CreakingHeartBlock} · {@code SpawnerBlock} 뿐이다.
 *       즉 <b>여기에 닿는 경험치는 전부 블록에서 나온 것</b>이고, 화로·낚시·번식·주민 거래·
 *       경험치병은 이 메서드를 지나지 않는다.</li>
 *   <li>{@code playerDestroy} 는 광석 블록들이 <b>재정의하지 않는다</b>
 *       ({@code DropExperienceBlock}·{@code RedStoneOreBlock} 모두 {@code spawnAfterBreak} 만
 *       재정의한다). 그래서 {@code Block} 한 곳에 파고들면 모든 광석이 걸린다.</li>
 * </ul>
 *
 * <h2>왜 문맥을 적어 두는가</h2>
 * <p>{@code popExperience} 의 인자는 월드·좌표·양뿐이라 <b>누가 캤는지가 남아 있지 않다.</b>
 * 배율은 팀마다 다르므로 사람을 알아야 한다. 그래서 사람과 블록이 아직 인자로 남아 있는
 * {@code playerDestroy} 에 들어갈 때 적어 두고, 나올 때 지운다.
 *
 * <p>문맥이 새더라도 위험이 작다. 읽는 자리가 {@code popExperience} 하나뿐이라 블록 밖의
 * 경험치로는 번질 수 없고, 그 위에 <b>좌표까지 맞아야</b> 곱하기 때문이다. 자세한 것은
 * {@link ExperienceBonus} 에 있다.
 *
 * <h2>여기에 걸리지 않는 것</h2>
 * <p>폭발·TNT·좀비의 문 부수기처럼 사람이 캔 것이 아닌 파괴는 {@code playerDestroy} 를 지나지
 * 않으므로 배율이 걸리지 않는다. 배율의 주인이 누구인지 알 수 없으니 이대로가 맞다.
 */
@Mixin(Block.class)
public abstract class BlockExperienceSourceMixin {
	@Inject(
			method = "playerDestroy(Lnet/minecraft/world/level/Level;"
					+ "Lnet/minecraft/world/entity/player/Player;"
					+ "Lnet/minecraft/core/BlockPos;"
					+ "Lnet/minecraft/world/level/block/state/BlockState;"
					+ "Lnet/minecraft/world/level/block/entity/BlockEntity;"
					+ "Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("HEAD"))
	private void sharedfate$beginBlockExperience(Level level, Player player, BlockPos pos,
			BlockState state, BlockEntity blockEntity, ItemStack tool, CallbackInfo callback) {
		ExperienceBonus.beginBlockExperience(player, pos, state);
	}

	@Inject(
			method = "playerDestroy(Lnet/minecraft/world/level/Level;"
					+ "Lnet/minecraft/world/entity/player/Player;"
					+ "Lnet/minecraft/core/BlockPos;"
					+ "Lnet/minecraft/world/level/block/state/BlockState;"
					+ "Lnet/minecraft/world/level/block/entity/BlockEntity;"
					+ "Lnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN"))
	private void sharedfate$endBlockExperience(Level level, Player player, BlockPos pos,
			BlockState state, BlockEntity blockEntity, ItemStack tool, CallbackInfo callback) {
		ExperienceBonus.endBlockExperience();
	}

	/**
	 * 블록이 떨어뜨릴 경험치 양을 고친다.
	 *
	 * <p>{@code argsOnly = true} 에 {@code ordinal = 0} 이면 인자 중 첫 번째 {@code int} —
	 * 이 메서드에는 하나뿐인 그 값이다. 뒤의 두 인자는 <b>대상 메서드의 인자를 그대로 받은
	 * 것</b>이다(Mixin 은 처리기가 대상 인자의 앞부분을 이어 받는 것을 허용한다). 좌표가 필요해서
	 * 받는다 — 적어 둔 문맥과 같은 자리인지 맞춰 보기 위해서다.
	 */
	@ModifyVariable(
			method = "popExperience(Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/core/BlockPos;I)V",
			at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int sharedfate$scaleBlockExperience(int amount, ServerLevel level, BlockPos pos) {
		return ExperienceBonus.scaleBlockExperience(pos, amount);
	}
}
