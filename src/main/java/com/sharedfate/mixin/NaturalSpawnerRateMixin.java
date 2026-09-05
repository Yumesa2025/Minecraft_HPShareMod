package com.sharedfate.mixin;

import com.sharedfate.perk.MobPerkModifiers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 적대 몹의 자연 스폰 시도 횟수에 배율을 먹이는 자리({@code mob_spawn_rate}).
 *
 * <p>배율을 정하는 규칙은 {@link MobPerkModifiers#spawnRateOf} 에, 설정 항목은
 * {@code SharedFateConfig.mobSpawnRatePerks} 에 있다. 여기는 「어디서 몇 번 도는가」만 정한다.
 *
 * <h2>26.2 에서 자연 스폰이 결정되는 자리</h2>
 * <p>서버는 매 틱 {@code ServerChunkCache.tickChunks} 에서 다음 순서로 돈다.
 *
 * <pre>{@code
 * ServerChunkCache.tickChunks(ProfilerFiller, long)
 *   → NaturalSpawner.createState(I, Iterable, ChunkGetter, LocalMobCapCalculator)
 *        // 이번 틱의 몹 수를 세어 SpawnState 를 만든다 (틱당 한 번)
 *   → NaturalSpawner.getFilteredSpawningCategories(SpawnState, boolean, boolean)
 *        // 전역 상한(mobcap)을 넘지 않은 갈래만 남긴다 (틱당 한 번)
 *   → ServerChunkCache.tickSpawningChunk(LevelChunk, long, List, SpawnState)
 *        → NaturalSpawner.spawnForChunk(ServerLevel, LevelChunk, SpawnState, List)
 *             → NaturalSpawner.spawnCategoryForChunk(
 *                   MobCategory, ServerLevel, LevelChunk, SpawnPredicate, AfterSpawnCallback)
 *                  // 갈래마다 청크당 한 번. 지역 상한을 통과한 갈래만 온다
 *                  → getRandomPosWithin(Level, LevelChunk)  ← 이 청크의 무작위 자리
 *                  → spawnCategoryForPosition(MobCategory, ServerLevel, ChunkAccess,
 *                        BlockPos, SpawnPredicate, AfterSpawnCallback)
 *                       // 무리 3번 × 무리당 1~4마리 시도
 * }</pre>
 *
 * <h2>대상을 어떻게 확인했는가</h2>
 * <p>{@code sharedfate.mixins.json} 에는 refmap 이 없어 <b>대상 서술자가 틀려도 빌드는
 * 통과하고 서버가 뜬 뒤 스폰이 도는 순간에 터진다.</b> 그래서 26.2 공통 jar 의 바이트코드를
 * 직접 읽어 확인했다.
 *
 * <pre>{@code
 * javap -p -c net/minecraft/world/level/NaturalSpawner.class
 *
 * public static void spawnForChunk(ServerLevel, LevelChunk, SpawnState, List<MobCategory>);
 *     44: aload_2 / aload 6 / … canSpawnForCategoryLocal:(MobCategory;ChunkPos;)Z
 *     54: ifeq          86                       ← 지역 상한을 못 넘으면 그 갈래는 거른다
 *     83: invokestatic  spawnCategoryForChunk:(
 *               Lnet/minecraft/world/entity/MobCategory;
 *               Lnet/minecraft/server/level/ServerLevel;
 *               Lnet/minecraft/world/level/chunk/LevelChunk;
 *               Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;
 *               Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V
 *
 * public static void spawnCategoryForChunk(MobCategory, ServerLevel, LevelChunk,
 *                                          SpawnPredicate, AfterSpawnCallback);
 *      0: aload_1 / aload_2
 *      2: invokestatic  getRandomPosWithin:(Level;LevelChunk;)BlockPos;
 *      9: getY / 12: ServerLevel.getMinY / 18: if_icmpge 22 / 21: return
 *     30: invokestatic  spawnCategoryForPosition:(MobCategory;ServerLevel;ChunkAccess;
 *                            BlockPos;SpawnPredicate;AfterSpawnCallback;)V
 *     33: return
 * }</pre>
 *
 * <p>{@code MobCategory} 에 {@code MONSTER} 상수와 {@code getMaxInstancesPerChunk()} 가 그대로
 * 있는 것, {@code SpawnPredicate}/{@code AfterSpawnCallback} 이
 * {@code NaturalSpawner} 의 중첩 인터페이스인 것도 같은 방법으로 확인했다. 이 사실들은
 * {@code MobSpawnRateTest} 가 반사로 다시 붙들어 둔다.
 *
 * <h2>왜 {@code spawnCategoryForChunk} 인가</h2>
 * <ul>
 *   <li><b>갈래를 여기서 고를 수 있다.</b> 첫 인자가 {@code MobCategory} 라
 *       {@code MONSTER} 가 아니면 참조 비교 한 번으로 빠져나온다. 주민·소({@code CREATURE})와
 *       물속 몹은 손대지 않는다.</li>
 *   <li><b>상한(mobcap)을 그대로 존중한다.</b> 이 자리에 오기 전에 전역·지역 상한 검사를 이미
 *       지났고, 넘겨받은 {@code SpawnPredicate}/{@code AfterSpawnCallback} 이 곧
 *       {@code SpawnState} 의 밀도 계산과 마릿수 세기다. 우리가 그것을 <b>그대로 다시
 *       넘겨</b> 부르므로, 늘어나는 것은 「시도 횟수」뿐이고 몹 총량의 천장은 바닐라 그대로다.
 *       상한에 닿은 뒤에는 배율이 아무 일도 하지 않는다 — 이것이 맞는 동작이다.</li>
 *   <li>한 단계 위인 {@code spawnForChunk} 는 {@code SpawnPredicate} 를 만들 길이 없다.
 *       {@code SpawnState.canSpawn}/{@code afterSpawn} 이 {@code private} 이기 때문이다.</li>
 * </ul>
 *
 * <h2>재진입 막기</h2>
 * <p>추가로 도는 것은 <b>같은 메서드를 다시 부르는 것</b>이라 이 주입이 또 걸린다. 깃발을
 * 세워 두 번째부터는 곧바로 돌아가게 한다. 스폰은 서버 스레드 하나에서만 도는 자리라 깃발
 * 하나로 충분하고, {@code finally} 로 반드시 내린다.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerRateMixin {
	/** 추가 스폰을 도는 동안 켜 둔다. 켜져 있으면 이 주입은 아무 일도 하지 않는다. */
	@Unique
	private static boolean sharedfate$spawningExtra;

	@Inject(
			method = "spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;"
					+ "Lnet/minecraft/server/level/ServerLevel;"
					+ "Lnet/minecraft/world/level/chunk/LevelChunk;"
					+ "Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
					+ "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
			at = @At("HEAD"), cancellable = true)
	private static void sharedfate$scaleHostileSpawnRate(
			MobCategory category, ServerLevel level, LevelChunk chunk,
			NaturalSpawner.SpawnPredicate predicate, NaturalSpawner.AfterSpawnCallback callback,
			CallbackInfo callbackInfo) {
		// 뜨거운 자리다. 대부분의 호출은 아래 두 줄에서 끝난다.
		if (category != MobCategory.MONSTER || sharedfate$spawningExtra) {
			return;
		}
		double multiplier = MobPerkModifiers.spawnRateMultiplier(level.getServer());
		if (multiplier == 1.0) {
			return;
		}

		int passes = MobPerkModifiers.spawnPasses(multiplier, level.getRandom().nextDouble());
		if (passes <= 0) {
			// 배율이 1보다 작을 때. 이번 청크의 적대 몹 스폰을 통째로 건너뛴다.
			callbackInfo.cancel();
			return;
		}
		if (passes == 1) {
			// 바닐라 본문이 그대로 한 번 돈다.
			return;
		}

		// 첫 번째는 이 주입이 끝난 뒤 바닐라 본문이 돌므로, 여기서는 나머지만 돈다.
		sharedfate$spawningExtra = true;
		try {
			for (int pass = 1; pass < passes; pass++) {
				NaturalSpawner.spawnCategoryForChunk(category, level, chunk, predicate, callback);
			}
		} finally {
			sharedfate$spawningExtra = false;
		}
	}
}
