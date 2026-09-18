package com.sharedfate.sync;

import com.sharedfate.SharedFateMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;

/**
 * 로드된 모든 청크에 떨어져 있는 아이템(과 경험치 오브)을 치운다.
 *
 * <h2>왜 인벤토리를 비우는 것만으로는 모자란가</h2>
 * <p>팀 해체나 회차 시작이 인벤토리·엔더상자를 비워도, 그전에 죽거나 버려서 이미 바닥에 널린
 * 아이템은 그대로 남는다. 그 자리를 정리하지 않으면 「전부 지웠다」는 안내와 달리 누군가 주우러
 * 다니면 그대로 되살아난다.
 *
 * <h2>왜 {@code ItemEntity} 뿐인가</h2>
 * <p>아이템 액자({@code ItemFrame})·방어구 거치대({@code ArmorStand})·떨어지는 모래
 * ({@code FallingBlockEntity})는 전부 다른 엔티티 형이라 여기서 건드리지 않는다. 형 검사를
 * {@link EntityTypeTest#forClass}로 정확히 {@code ItemEntity} 하나만 골라내므로 섞일 길이 없다.
 *
 * <h2>왜 경험치 오브도 함께 지우는가</h2>
 * <p>이 유틸을 부르는 자리는 팀의 경험치를 0 으로 되돌리는 자리다 —
 * {@code InventorySwapper.disbandTeam} 이 그렇고, {@code GameStartManager} 의
 * {@code resetRunProgress} 도 같은 순간에 {@code state.totalExperience} 를 0 으로 되돌린다.
 * 바닥에 남은 경험치 오브를 그대로 두면 누군가 그것을 주워 방금 초기화한 경험치가 슬그머니
 * 되살아난다 — 인벤토리를 비우면서 손에 든 것만 지우고 바닥에 떨어진 아이템은 그대로 두는
 * 것과 같은 구멍이다. 그래서 아이템과 같은 자리에서 함께 치운다.
 *
 * <h2>왜 즉석에서 훑은 목록을 따로 만드는가</h2>
 * <p>{@code ServerLevel.getEntities(EntityTypeTest, Predicate)}는 내부적으로 새
 * {@code ArrayList}를 만들어 채운 뒤 그 사본을 돌려준다({@code Lists.newArrayList()} 뒤에
 * {@code LevelEntityGetter.get}으로 채우는 구조 — 26.2 deobf jar의 {@code ServerLevel} 바이트코드로
 * 확인했다). 청크를 실제로 담고 있는 살아 있는 컬렉션이 아니라 사본이므로, 그 목록을 돌면서
 * {@link ItemEntity#discard()}를 불러도 훑는 도중에 컬렉션이 바뀌는 문제가 없다.
 */
public final class GroundItemSweeper {
	private GroundItemSweeper() {
	}

	/**
	 * 서버가 들고 있는 모든 레벨을 훑어 바닥에 떨어진 아이템과 경험치 오브를 지운다.
	 *
	 * <p>레벨 하나를 훑다 예외가 나도 부르는 쪽까지 죽이지 않는다 — 대신 그 지점까지 지운
	 * 개수를 그대로 돌려주고 경고만 남긴다. 로그에 [RUN] 태그를 붙이는 것은 부르는 쪽의 몫이다.
	 *
	 * @return 지운 엔티티 총 개수(아이템 + 경험치 오브)
	 */
	public static int sweep(MinecraftServer server) {
		int removed = 0;
		try {
			for (ServerLevel level : server.getAllLevels()) {
				removed += sweepLevel(level);
			}
		} catch (RuntimeException error) {
			SharedFateMod.LOGGER.warn("월드에 떨어진 아이템을 치우다 실패했습니다.", error);
		}
		return removed;
	}

	private static int sweepLevel(ServerLevel level) {
		int removed = 0;
		List<? extends ItemEntity> items =
				level.getEntities(EntityTypeTest.forClass(ItemEntity.class), entity -> true);
		for (ItemEntity item : items) {
			item.discard();
			removed++;
		}
		List<? extends ExperienceOrb> orbs =
				level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class), entity -> true);
		for (ExperienceOrb orb : orbs) {
			orb.discard();
			removed++;
		}
		return removed;
	}
}
